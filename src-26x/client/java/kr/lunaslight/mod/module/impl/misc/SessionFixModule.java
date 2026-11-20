package kr.lunaslight.mod.module.impl.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaSocial;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

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

	public SessionFixModule() {
		super("session_fix", "세션 복구", ModuleCategory.FEATURE,
			"'세션이 잘못되었습니다' 화면에서 재시작 없이 세션을 새로 받아 바로 재접속");
		// 49-122차(사용자: "세션 복구는 기능이 아니라 무조건 고정 - 기능에서 없애고 고정시켜"): 항상 켜짐 + 기능 목록에서 숨김.
		alwaysOn();
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		LunaCompat.registerScreenAfterInit(this::onScreenInit);   // 49-344차
		LunaCompat.registerScreenAfterInit(this::onLimitInit);   // 49-672차: 로그인 429
		LunaCompat.registerScreenAfterRender(this::onLimitFrame);
	}

	@Override
	public boolean hiddenInList() {
		return true;   // 49-122차: 켜고 끄는 기능이 아니라 항상 도는 고정 기능 - 격자에 안 띄운다.
	}

	/** 49-619차: 런처가 features.relogin = false를 넘기면(너굴라이언트) 끊김 화면 버튼(세션 새로고침 후 재접속, 재접속)을 아예 안 얹는다. */
	@Override
	public boolean isEnabled() {
		return LunaSocial.featureRelogin && super.isEnabled();
	}

	private static boolean isDisconnectedScreen(Screen screen) {
		if (screen == null) {
			return false;
		}
		try {
			return LunaCompat.classForName("net.minecraft.client.gui.screens.DisconnectedScreen").isInstance(screen);
		} catch (Throwable ignored) {
			return screen.getClass().getName().contains("Disconnected");
		}
	}

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
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
		// 49-344차(사용자, 끊김 화면 사진: "세션 재접속 버튼은 [서버 목록으로 돌아가기] 아래에, 마크 버튼이랑 똑같게"): 직접 그리던
		// 초록 입체 버튼(49-197차)을 없애고 onScreenInit이 얹은 바닐라 버튼을 쓴다. 여기서는 글자/활성만 맞추고 안내 글을 그 아래에 쓴다.
		String label = switch (phase) {
			case WORKING -> "세션 새로 받는 중…";
			case RECONNECT -> "다시 접속하는 중…";
			default -> "세션 새로고침 후 재접속";
		};
		if (button != null && buttonScreen == screen) {
			LunaCompat.setButton(button, label, phase == 0);
			if (note != null && !note.isEmpty()) {
				int nw = LunaCompat.getTextWidth(client.font, note);
				int by = LunaCompat.widgetInt(button, "getY", "y");
				LunaDraw.text(ctx, client.font, note, (s.width - nw) / 2, by + 24, LunaDraw.TEXT_DIM);
			}
		}
	}

	/** 얹은 바닐라 버튼과 그 화면(화면이 다시 init되면 새로 얹는다). */
	private Object button;
	private Object buttonScreen;

	/** 끊김 화면 init 뒤: 맨 아래 버튼([서버 목록으로 돌아가기]) 바로 밑에 같은 폭의 바닐라 버튼을 얹는다. */
	private void onScreenInit(Object screen) {
		button = null;
		buttonScreen = null;
		if (!isEnabled() || !(screen instanceof Screen s) || !isDisconnectedScreen(s)) {
			return;
		}
		java.util.List<Object> buttons = LunaCompat.screenButtons(screen);
		if (buttons == null || buttons.isEmpty()) {
			return;
		}
		// 49-577차(사용자 사진: [세션 새로고침 후 재접속]이 두 개): 화면 init 뒤 알림이 한 화면에 두 번 와서 버튼이 두 번 얹혔다 -
		// 이미 얹은 우리 버튼이 있으면 다시 얹지 않는다.
		for (Object b : buttons) {
			String msg = buttonText(b);
			if (msg != null && (msg.contains("세션 새로") || msg.contains("다시 접속하는 중") || msg.equals(RECONNECT_LABEL))) {
				return;
			}
		}
		Object last = null;
		int lastY = Integer.MIN_VALUE;
		for (Object b : buttons) {
			int y = LunaCompat.widgetInt(b, "getY", "y");
			if (y > lastY) {
				lastY = y;
				last = b;
			}
		}
		if (last == null) {
			return;
		}
		int x = LunaCompat.widgetInt(last, "getX", "x");
		int w = LunaCompat.widgetInt(last, "getWidth", "width");
		int h = LunaCompat.widgetInt(last, "getHeight", "height");
		if (w <= 0) {
			w = 200;
		}
		if (h <= 0) {
			h = 20;
		}
		button = LunaCompat.addVanillaButton(screen, x, lastY + h + 4, w, h, "세션 새로고침 후 재접속", () -> {
			if (phase == 0) {
				startRefresh();
			}
		});
		buttonScreen = button == null ? null : screen;
		// 49-577차(사용자: "하나 없애 주고 없앤 자리에 재접속 버튼"): 세션은 그대로 두고 마지막 서버로 바로 다시 들어가는 버튼
		String addr = AutoReconnectModule.lastServerAddress();
		if (button != null && addr != null && !addr.isBlank()) {
			LunaCompat.addVanillaButton(screen, x, lastY + (h + 4) * 2, w, h, RECONNECT_LABEL, () -> {
				String a = AutoReconnectModule.lastServerAddress();
				if (a != null && !a.isBlank()) {
					AutoReconnectModule.reconnectTo(a);
				}
			});
		}
	}

	private static final String RECONNECT_LABEL = "재접속";

	private static String buttonText(Object b) {
		try {
			Object m = LunaCompat.callNoArg(b, "getMessage");
			return m instanceof net.minecraft.network.chat.Component c ? c.getString() : null;
		} catch (Throwable t) {
			return null;
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
			fail("요청을 보내지 못했습니다");
		}
	}

	private void pollResult() {
		long now = System.currentTimeMillis();
		if (now - lastPoll < 250) {
			return;   // 매 프레임 파일을 열지 않게 살짝 텀
		}
		lastPoll = now;
		if (now > deadline) {
			fail("런처 응답이 없습니다 (런처가 켜져 있는지 확인)");
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
				fail("서명 확인 실패 - 무시했습니다");
				return;
			}
			if (uuid == null || name == null || token == null || token.isEmpty()) {
				fail("런처가 토큰을 주지 못했습니다");
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
				note = "세션을 새로 받았습니다 - 서버를 다시 눌러 주십시오";
				return;
			}
			phase = RECONNECT;
			note = "";
			AutoReconnectModule.reconnectTo(addr);
			// 재접속을 띄우면 곧 이 화면을 떠난다. 실패해 다시 끊김 화면이 뜨면 phase 0으로 버튼이 다시 보인다.
			phase = 0;
		} catch (Throwable t) {
			LunaCompat.warnOnce("sessionFix:result", t);
			fail("세션을 새로 받지 못했습니다");
		}
	}

	private void fail(String why) {
		phase = 0;
		note = why == null ? "실패했습니다" : why;
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

	// ==================== 49-672차: 로그인 429(요청이 너무 많음) ====================
	// 사용자(사진: "다른 서버로 이송되는 중 연결에 실패했습니다 / 로그인에 실패했습니다: Status: 429", "너굴라이언트 맨날 이럼"):
	// 너굴마을은 서버를 옮길 때(로비 → 섬 등) 마인크래프트 로그인을 처음부터 다시 한다. 짧은 시간에 여러 번 옮기면 모장 로그인 서버가
	// 잠깐 막는다(429). 노바/너굴라이언트 탓이 아니라 기다리면 풀린다. 바로 다시 붙으면 또 막히니 기다린 뒤에(연달아 막히면
	// 10 → 20 → 40 → 80초) 49-685차부터 한 번 자동으로 다시 접속한다. 버튼은 기다림이 끝나면 켜진다.
	private Object limitButton;
	private Object limitScreen;
	private long limitReadyAt;
	private int limitStreak;
	private long limitLastAt;
	private long limitAutoFor;   // 49-685차: 이 429(limitLastAt)로 자동 재접속을 이미 했으면 그 값
	private int limitTries;      // 5분 안에 연달아 막힌 횟수 - 6번째부터는 자동으로 붙지 않고 버튼만
	private Object limitSeenScreen;

	private static boolean isRateLimited(Screen s) {
		String t = AutoReconnectModule.screenText(s);
		return t.contains("429") || t.toLowerCase(java.util.Locale.ROOT).contains("too many requests");
	}

	private void onLimitInit(Object screen) {
		limitButton = null;
		limitScreen = null;
		if (!(screen instanceof Screen s) || !isDisconnectedScreen(s) || !isRateLimited(s)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (screen != limitSeenScreen && now - limitLastAt > 3000) {   // 같은 화면 init이 두 번 와도(창 크기 바꿈 등) 한 번만 센다
			limitSeenScreen = screen;
			boolean again = now - limitLastAt < 5 * 60_000;
			limitStreak = again ? Math.min(limitStreak + 1, 3) : 0;
			limitTries = again ? limitTries + 1 : 0;
			limitLastAt = now;
			limitReadyAt = now + (10_000L << limitStreak);
		}
		replaceLimitText(s);
		// 세션 복구의 [재접속] 버튼이 이미 있으면(노바 클라이언트) 버튼은 그걸 쓰고 안내만 쓴다.
		if (isEnabled()) {
			return;
		}
		java.util.List<Object> buttons = LunaCompat.screenButtons(screen);
		if (buttons == null || buttons.isEmpty()) {
			return;
		}
		Object last = null;
		int lastY = Integer.MIN_VALUE;
		for (Object b : buttons) {
			String msg = buttonText(b);
			if (msg != null && msg.startsWith("다시 접속")) {
				return;   // 이미 얹음
			}
			int y = LunaCompat.widgetInt(b, "getY", "y");
			if (y > lastY) {
				lastY = y;
				last = b;
			}
		}
		if (last == null) {
			return;
		}
		int x = LunaCompat.widgetInt(last, "getX", "x");
		int w = Math.max(200, LunaCompat.widgetInt(last, "getWidth", "width"));
		int h = Math.max(20, LunaCompat.widgetInt(last, "getHeight", "height"));
		limitButton = LunaCompat.addVanillaButton(screen, x, lastY + h + 4, w, h, "다시 접속", () -> {
			if (System.currentTimeMillis() >= limitReadyAt) {
				limitReconnect();
			}
		});
		limitScreen = limitButton == null ? null : screen;
	}

	/**
	 * 49-685차(너굴라이언트 세션 분석: 로그인 429는 모장 로그인 서버가 이 PC 요청을 잠깐 막은 것 - "429일 때는 10초쯤 기다렸다가 붙고,
	 * 오류 문구 대신 '잠시 후 자동으로 다시 접속합니다'를 보여 주면 됩니다"): 기다린 뒤 한 번 자동으로 다시 접속한다.
	 * 튕김(서버가 내보냄) 뒤 자동 재접속을 막은 49-183차 규칙은 그대로 - 이것은 로그인 자체가 안 된 429에만.
	 */
	private void limitReconnect() {
		limitAutoFor = limitLastAt;
		String a = AutoReconnectModule.lastServerAddress();
		if (a != null && !a.isBlank()) {
			AutoReconnectModule.reconnectTo(a);
		}
	}

	/** 화면의 "로그인에 실패했습니다: Status: 429 …" 글을 알아듣기 쉬운 안내로 바꾼다. */
	private static void replaceLimitText(Screen s) {
		try {
			for (Object o : s.children()) {
				if (o instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage() != null) {
					String t = w.getMessage().getString();
					if (t.contains("429") || t.toLowerCase(java.util.Locale.ROOT).contains("too many requests")) {
						w.setMessage(net.minecraft.network.chat.Component.literal(
								"마인크래프트 로그인 서버가 잠시 막았습니다(요청이 너무 많음).\n잠시 후 자동으로 다시 접속합니다."));
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("sessionFix:limitText", t);
		}
	}

	private void onLimitFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		if (!(screen instanceof Screen s) || !isDisconnectedScreen(s) || limitLastAt == 0 || !isRateLimited(s)) {
			return;
		}
		long left = Math.max(0, (limitReadyAt - System.currentTimeMillis() + 999) / 1000);
		boolean auto = limitAutoFor != limitLastAt && limitTries < 5;
		if (left == 0 && auto) {
			limitReconnect();   // 49-685차: 기다린 뒤 한 번 자동으로
			return;
		}
		if (limitButton != null && limitScreen == screen) {
			LunaCompat.setButton(limitButton, left > 0 ? "다시 접속 (" + left + "초 뒤)" : "다시 접속", left == 0);
		}
		String[] lines = {
				"마인크래프트 로그인 서버가 잠시 막았습니다 (요청이 너무 많음).",
				left > 0 ? left + (limitTries < 5 ? "초 뒤 자동으로 다시 접속합니다." : "초 뒤 다시 접속하실 수 있습니다.") : "다시 접속하실 수 있습니다."
		};
		int y = 12;
		for (String line : lines) {
			int lw = LunaCompat.getTextWidth(client.font, line);
			LunaDraw.text(ctx, client.font, line, (s.width - lw) / 2, y, LunaDraw.TEXT_DIM);
			y += 11;
		}
	}
}
