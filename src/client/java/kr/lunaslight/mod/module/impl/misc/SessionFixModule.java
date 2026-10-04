package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaSocial;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-106차(사용자: "'세션이 잘못되었습니다' 뜰 때 게임·런처 재시작 말고 여기서 바로 고치게"):
 * <b>세션 복구</b> - 끊김 화면(로그인 실패/세션 만료)에 버튼 하나를 얹어, 누르면 런처에게 액세스 토큰을 새로
 * 받아 게임 세션에 밀어 넣고 마지막 서버로 바로 다시 접속한다. 게임을 껐다 켤 필요가 없다.
 *
 * <p>흐름(파일 브리지 - 녹화·소셜과 같은 방식):
 * <ol>
 *   <li>버튼 클릭 → 게임 폴더에 {@code .luna-relogin.json}{action:"refresh"} 를 쓴다.</li>
 *   <li>런처가 그걸 보고 <b>활성 계정 토큰을 강제 갱신</b>해서 {@code .luna-relogin-result.json}
 *       {ts, sig, uuid, name, accessToken} 을 써 준다(우리 비밀값으로 서명).</li>
 *   <li>서명을 확인({@link LunaClientMod#verifySig2})하고 {@link LunaSocial#switchAccount}로 세션 교체,
 *       파일은 읽자마자 삭제(토큰이 디스크에 안 남게), 그리고 {@link AutoReconnectModule#reconnectTo} 로 재접속.</li>
 * </ol>
 * 런처가 없거나(다른 런처로 실행) 응답이 없으면 몇 초 뒤 "런처 응답이 없어요" 로 돌아간다.
 */
public class SessionFixModule extends Module {

	private static final int WORKING = 1;   // 런처에 요청하고 결과를 기다리는 중
	private static final int RECONNECT = 2;  // 세션 교체 성공, 재접속 띄우는 중

	private int phase;                  // 0 = 대기(버튼), 1 = 갱신 중, 2 = 재접속 중
	private long deadline;              // 결과 대기 마감(ms)
	private long lastPoll;             // 결과 파일 폴링 간격 조절
	private String note = "";          // 실패/안내 문구(버튼 아래)
	private boolean clickHeld;

	public SessionFixModule() {
		super("session_fix", "세션 복구", ModuleCategory.FEATURE,
			"'세션이 잘못되었습니다' 화면에서 재시작 없이 세션을 새로 받아 바로 재접속");
		// 49-122차(사용자: "세션 복구는 기능이 아니라 무조건 고정 - 기능에서 없애고 고정시켜"): 항상 켜짐 + 기능 목록에서 숨김.
		alwaysOn();
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
	}

	@Override
	public boolean hiddenInList() {
		return true;   // 49-122차: 켜고 끄는 기능이 아니라 항상 도는 고정 기능 - 격자에 안 띄운다.
	}

	private static boolean isDisconnectedScreen(Screen screen) {
		if (screen == null) {
			return false;
		}
		try {
			return LunaCompat.classForName("net.minecraft.client.gui.screen.DisconnectedScreen").isInstance(screen);
		} catch (Throwable ignored) {
			return screen.getClass().getName().contains("Disconnected");
		}
	}

	private void onScreenFrame(Object screen, DrawContext ctx, int mouseX, int mouseY) {
		if (!isEnabled() || !(screen instanceof Screen s) || !isDisconnectedScreen(s)) {
			// 다른 화면이면 진행 중이던 상태는 접는다(재접속 화면으로 넘어간 경우 등).
			if (phase != 0 && !(screen instanceof Screen s2 && isDisconnectedScreen(s2))) {
				phase = 0;
			}
			return;
		}
		if (phase == WORKING) {
			pollResult();
		}

		// 49-197차(사용자: "세션 로그인 후 재접속 버튼이 너무 초라해 - 색도 좀 있고 입체감도 있어야"): 납작한 한 가지 색 판 →
		// 입체 버튼. 테마색 위→아래 그라데이션 + 아래쪽 두꺼운 어두운 턱(누르면 내려앉음) + 위 가장자리 밝은 줄 + 그림자,
		// 마우스를 올리면 밝아지고 1px 떠오르며, 가만히 있을 때 2.6초마다 빛이 한 번 쓸고 지나간다. 앞에 새로고침 아이콘.
		int bw = 236;
		int bh = 26;
		int lip = 3;
		int bx = (s.width - bw) / 2;
		int by = s.height - 60;   // 자동 재접속 안내줄(height-24)보다 위
		boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh + lip;
		boolean clickable = phase == 0;
		boolean pressed = clickable && hover && WindowAccess.of(client) != null
			&& GLFW.glfwGetMouseButton(WindowAccess.of(client).getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		String label = switch (phase) {
			case WORKING -> "세션 새로 받는 중…";
			case RECONNECT -> "다시 접속하는 중…";
			default -> "세션 새로고침 후 재접속";
		};

		int accent = LunaDraw.ACCENT | 0xFF000000;
		int face = clickable ? accent : LunaDraw.lerpColor(accent, 0xFF2A2F36, 0.55f);
		if (clickable && hover && !pressed) {
			face = LunaDraw.lerpColor(face, 0xFFFFFFFF, 0.14f);
		}
		int top = LunaDraw.lerpColor(face, 0xFFFFFFFF, 0.22f);
		int bottom = LunaDraw.lerpColor(face, 0xFF000000, 0.10f);
		int lipColor = LunaDraw.lerpColor(face, 0xFF000000, 0.45f);
		int lift = pressed ? lip : (clickable && hover ? -1 : 0);   // 누르면 턱만큼 내려앉고, 올리면 1px 떠오른다
		int fy = by + lift;
		// 그림자와 턱(버튼 아래 두께)
		LunaDraw.shadow(ctx, bx, by + lip, bw, bh, 6);
		LunaDraw.roundRect(ctx, bx, by + lip, bw, bh, 6, lipColor);
		// 윗면
		LunaDraw.roundRectGradient(ctx, bx, fy, bw, bh, 6, top, bottom);
		ctx.fill(bx + 5, fy + 1, bx + bw - 5, fy + 2, LunaDraw.withAlpha(0xFFFFFFFF, 0x55));   // 위 가장자리 반짝임
		// 빛이 쓸고 지나감(가만히 있을 때만)
		long now = System.currentTimeMillis();
		if (clickable && !pressed) {
			float t = (now % 2600L) / 900f;   // 0..1 동안만 지나가고 나머지는 쉼
			if (t <= 1f) {
				int bandW = 26;
				int cx = bx - bandW + Math.round((bw + bandW * 2) * t);
				for (int k = -bandW / 2; k <= bandW / 2; k++) {
					int px = cx + k;
					if (px < bx + 4 || px >= bx + bw - 4) {
						continue;
					}
					int a = Math.round(70 * (1f - Math.abs(k) / (bandW / 2f)));
					ctx.fill(px, fy + 2, px + 1, fy + bh - 2, LunaDraw.withAlpha(0xFFFFFFFF, a));
				}
			}
		}
		// 진행 중이면 아래쪽에 흐르는 줄
		if (!clickable) {
			int seg = 40;
			int off = (int) ((now / 12L) % (bw + seg)) - seg;
			int x0 = Math.max(bx + 4, bx + off);
			int x1 = Math.min(bx + bw - 4, bx + off + seg);
			if (x1 > x0) {
				ctx.fill(x0, fy + bh - 3, x1, fy + bh - 1, LunaDraw.withAlpha(accent, 0xE0));
			}
		}
		// 글자색: 밝은 테마색 위엔 검정, 어두운 색 위엔 흰색
		int lum = (((face >> 16) & 0xFF) * 299 + ((face >> 8) & 0xFF) * 587 + (face & 0xFF) * 114) / 1000;
		int tc = !clickable ? 0xFFE6E9ED : (lum > 150 ? 0xFF0A0F0C : 0xFFFFFFFF);
		int tw = LunaCompat.getTextWidth(client.textRenderer, label);
		int iconW = clickable ? kr.lunaslight.mod.gui.LunaIcons.SIZE + 4 : 0;
		int tx = bx + (bw - tw - iconW) / 2 + iconW;
		int ty = LunaDraw.textY(fy, bh);
		if (clickable) {
			kr.lunaslight.mod.gui.LunaIcons.draw(ctx, client.textRenderer, kr.lunaslight.mod.gui.LunaIcons.RESET, tx - iconW,
				LunaDraw.iconY(fy, bh), tc);
		}
		// 글자 아래 한 칸 그림자(밝은 판 위에선 흰 그림자, 어두운 판 위에선 검은 그림자)
		LunaDraw.text(ctx, client.textRenderer, label, tx, ty + 1, LunaDraw.withAlpha(tc == 0xFF0A0F0C ? 0xFFFFFFFF : 0xFF000000, 0x40));
		LunaDraw.text(ctx, client.textRenderer, label, tx, ty, tc);
		int bh2 = bh + lip;   // 아래 안내 글 자리 계산용(턱 포함)

		if (note != null && !note.isEmpty()) {
			int nw = LunaCompat.getTextWidth(client.textRenderer, note);
			LunaDraw.text(ctx, client.textRenderer, note, (s.width - nw) / 2, by + bh2 + 5, LunaDraw.TEXT_DIM);
		}

		// 클릭(GLFW 폴링, 눌린 순간 한 번)
		boolean down = WindowAccess.of(client) != null
			&& GLFW.glfwGetMouseButton(WindowAccess.of(client).getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (down && !clickHeld) {
			clickHeld = true;
			if (clickable && hover) {
				startRefresh();
			}
		} else if (!down) {
			clickHeld = false;
		}
	}

	private void startRefresh() {
		note = "";
		phase = WORKING;
		deadline = System.currentTimeMillis() + 12_000;
		lastPoll = 0;
		try {
			Files.deleteIfExists(resultPath());   // 예전 결과가 남아 있으면 치운다
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("action", "refresh");
			Files.writeString(requestPath(), o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("sessionFix:request", t);
			fail("요청을 보내지 못했어요");
		}
	}

	private void pollResult() {
		long now = System.currentTimeMillis();
		if (now - lastPoll < 250) {
			return;   // 매 프레임 파일을 열지 않게 살짝 텀
		}
		lastPoll = now;
		if (now > deadline) {
			fail("런처 응답이 없어요 (런처가 켜져 있는지 확인)");
			return;
		}
		Path p = resultPath();
		if (!Files.exists(p)) {
			return;
		}
		String raw;
		try {
			raw = Files.readString(p, StandardCharsets.UTF_8);
		} catch (Throwable t) {
			return;   // 아직 쓰는 중일 수 있음 - 다음 폴링에서 다시
		}
		// 토큰이 디스크에 남지 않게 읽자마자 지운다.
		try {
			Files.deleteIfExists(p);
		} catch (Throwable ignored) {
		}
		try {
			JsonObject o = new JsonParser().parse(raw).getAsJsonObject();
			long ts = o.get("ts").getAsLong();
			String sig = o.has("sig2") ? o.get("sig2").getAsString() : "";
			String uuid = o.get("uuid").getAsString();
			String name = o.get("name").getAsString();
			String token = o.get("accessToken").getAsString();
			// 49-259차: 런처의 Ed25519 서명(sig2) - 서명 글자에 계정(uuid, 이름, 토큰 지문)이 들어 있어 다른 계정으로 바꿔치기 못 한다
			if (!LunaClientMod.verifySig2("relogin", ts, uuid + "|" + name + "|" + LunaClientMod.sha256Hex(token == null ? "" : token), sig)) {
				fail("서명 확인 실패 - 무시했어요");
				return;
			}
			if (uuid == null || name == null || token == null || token.isEmpty()) {
				fail("런처가 토큰을 주지 못했어요");
				return;
			}
			String why = LunaSocial.switchAccount(client, new LunaSocial.Account(uuid, name, token));
			if (why != null) {
				fail(why);
				return;
			}
			// 세션 교체 성공 → 마지막 서버로 재접속
			String addr = AutoReconnectModule.lastServerAddress();
			if (addr == null || addr.isEmpty()) {
				phase = 0;
				note = "세션을 새로 받았어요 - 서버를 다시 눌러 주세요";
				return;
			}
			phase = RECONNECT;
			note = "";
			AutoReconnectModule.reconnectTo(addr);
			// 재접속을 띄우면 곧 이 화면을 떠난다. 실패해 다시 끊김 화면이 뜨면 phase 0으로 버튼이 다시 보인다.
			phase = 0;
		} catch (Throwable t) {
			LunaCompat.warnOnce("sessionFix:result", t);
			fail("세션을 새로 받지 못했어요");
		}
	}

	private void fail(String why) {
		phase = 0;
		note = why == null ? "실패했어요" : why;
	}

	private static Path gameDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
	}

	private static Path requestPath() {
		return gameDir().resolve(".luna-relogin.json");
	}

	private static Path resultPath() {
		return gameDir().resolve(".luna-relogin-result.json");
	}
}
