package kr.lunaslight.mod.module.impl.waypoint;

// 20차: 다른 유저에게 보이려면 서버가 이 핑 패킷을 릴레이해줘야 하는데, 클라이언트끼리는
// 절대 직접 통신할 수 없고 반드시 서버를 거쳐야 함(마인크래프트 자체가 클라-서버 구조라
// P2P가 없음). 남이 운영하는 서버는 이 패킷을 모르니 무시함 - 그래서 이 릴레이는 사실상
// "내가 싱글플레이에서 LAN에 공개"로 켠 경우에만 실제로 동작함. kr.lunaslight.mod.network.
// PingPayload가 이 리플렉션 기반 등록/송수신/릴레이를 전담. 그 외의 모든 경우(일반 서버 접속)는
// 조용히 실패하고 "내 화면에 로컬로 찍기"만 계속 동작함.
//
// 49-22차 전면 재작성(사용자 5건):
//  ① "표시 이름 설정 없애고 내 닉네임으로" - display_name 설정 삭제, 핑 라벨은 내 닉네임(플레이어 이름).
//  ② "손 안 닿으면 안 찍힘" - crosshairTarget은 도달 거리(~4.5블록)까지만 계산된 값이라 먼 곳은 MISS였음.
//     이제 Entity#raycast(최대 거리, 1f, false)로 직접 광선을 쏘고, 그래도 안 맞으면 최대 거리 지점에 찍는다.
//  ③ "움직일 때 버벅임" - 플레이어 yaw/pitch(틱 값) + FOV 70 가정의 근사 투영을 버리고 LunaProjection
//     (보간된 카메라 위치/각도 + 실제 FOV, 프레임당 1회 계산)으로 투영 → 부드럽게 따라감.
//  ④ "위로 높은 레이저" - LunaProjection.drawBeam(HUD 투영 빛기둥, 벽 너머로도 보임)으로 핑마다 기둥.
//  ⑤ "나침반 표시" - CompassModule이 getPings()를 읽어 웨이포인트 마커 줄에 같이 찍는다.

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.glfw.GLFW;

public class PingMarkModule extends Module {

	/** 화면에 잠깐 표시되는 핑 마커 하나(로컬 전용). */
	public static class Ping {
		public final Vec3d pos;
		public final int color;
		public final String name;
		final long createdAtMillis;
		long expireAtMillis;

		Ping(Vec3d pos, int color, String name, long expireAtMillis) {
			this.pos = pos;
			this.color = color;
			this.name = name;
			this.createdAtMillis = System.currentTimeMillis();
			this.expireAtMillis = expireAtMillis;
		}
	}

	private final KeybindSetting pingKey = register(new KeybindSetting(
			"ping_key", "핑 키", "바라보는 지점에 핑 마커를 찍습니다.", GLFW.GLFW_KEY_X));

	private final ColorSetting pingColor = register(new ColorSetting(
			"ping_color", "핑 색", "핑 마커의 색입니다.", 0xFFFFAA00));

	private final IntSetting durationSeconds = register(new IntSetting(
			"duration_seconds", "지속 시간", "핑 마커가 사라지기까지의 시간(초)입니다.", 8, 1, 60, 1).unit("초"));

	private final IntSetting maxRange = register(new IntSetting(
			"max_range", "최대 거리", "핑을 찍을 수 있는 최대 거리(블록)입니다.", 128, 16, 256, 1).unit("블록"));

	private final BooleanSetting beam = register(new BooleanSetting(
			"beam", "레이저", "핑 위치에 위로 뻗는 빛기둥을 그립니다.", true));

	private final IntSetting beamHeight = register(new IntSetting(
			"beam_height", "레이저 높이", "빛기둥 높이(블록)입니다.", 48, 8, 256, 8).unit("블록"));

	private final List<Ping> activePings = new ArrayList<>();
	private boolean prevPingKeyPressed = false;

	private static volatile List<Ping> pingsStatic = List.of();

	public PingMarkModule() {
		super("ping_mark", "핑 마커", ModuleCategory.FEATURE, "바라보는 지점에 마커 표시 (싱글 | LAN)");
	}

	/** 나침반용: 지금 살아 있는 핑 목록(읽기 전용 스냅샷). */
	public static List<Ping> getPings() {
		return pingsStatic;
	}

	@Override
	protected void onEnable() {
		registerNetworkingIfPossible();
	}

	@Override
	protected void onDisable() {
		activePings.clear();
		pingsStatic = List.of();
	}

	private void registerNetworkingIfPossible() {
		try {
			kr.lunaslight.mod.network.PingPayload.registerType(this::receiveRemotePing);
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn(
					"[Nova] 핑 페이로드 네트워킹 등록 실패 - 로컬 표시만 동작합니다.", t);
		}
	}

	/** 서버(랜 호스트)로부터 다른 플레이어가 찍은 핑이 도착했을 때 PingPayload가 호출. */
	private void receiveRemotePing(double x, double y, double z, int color, String name) {
		long expireAt = System.currentTimeMillis() + durationSeconds.get() * 1000L;
		activePings.add(new Ping(new Vec3d(x, y, z), color, name, expireAt));
		pingsStatic = new ArrayList<>(activePings);
	}

	@Override
	public void onTick() {
		if (client.player == null || WindowAccess.of(client) == null || !pingKey.isBound()) {
			prevPingKeyPressed = false;
		} else {
			boolean pressed = client.currentScreen == null && pingKey.isDown(client);
			if (pressed && !prevPingKeyPressed) {
				placePing();
			}
			prevPingKeyPressed = pressed;
		}

		long now = System.currentTimeMillis();
		boolean changed = false;
		Iterator<Ping> it = activePings.iterator();
		while (it.hasNext()) {
			if (now >= it.next().expireAtMillis) {
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			pingsStatic = new ArrayList<>(activePings);
		}
	}

	private String myName() {
		try {
			return client.player.getName().getString();
		} catch (Throwable t) {
			return "";
		}
	}

	private static Method raycastMethod;
	private static boolean raycastResolved;

	/** 먼 곳까지 광선을 쏴서 맞은 지점(없으면 최대 거리 지점). */
	private Vec3d aimPoint() {
		double max = maxRange.get();
		Vec3d eye = client.player.getCameraPosVec(1f);
		Vec3d dir = client.player.getRotationVec(1f);
		try {
			if (!raycastResolved) {
				raycastResolved = true;
				raycastMethod = LunaCompat.findMethod(Entity.class, "raycast", double.class, float.class, boolean.class);
				if (raycastMethod == null) {
					raycastMethod = LunaCompat.findMethod(Entity.class, "rayTrace", double.class, float.class, boolean.class);
				}
			}
			if (raycastMethod != null) {
				Object r = raycastMethod.invoke(client.player, max, 1f, false);
				if (r instanceof HitResult hit && hit.getType() != HitResult.Type.MISS) {
					return hit.getPos();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("ping:raycast", t);
		}
		// 아무것도 안 맞음(하늘 등): 최대 거리 지점
		return eye.add(dir.multiply(max));
	}

	private void placePing() {
		Vec3d hitPos = aimPoint();
		if (hitPos == null) {
			return;
		}
		long expireAt = System.currentTimeMillis() + durationSeconds.get() * 1000L;
		String name = myName();
		activePings.add(new Ping(hitPos, pingColor.getArgb(), name, expireAt));
		pingsStatic = new ArrayList<>(activePings);

		// 같은 모드를 설치한 다른 클라이언트에게도 브로드캐스트를 "시도"하지만, 서버가 이
		// 페이로드를 릴레이해주지 않는 한 실제로 다른 사람에게는 아무 효과가 없습니다.
		try {
			kr.lunaslight.mod.network.PingPayload.sendIfPossible(hitPos, pingColor.getArgb(), name);
		} catch (Throwable ignored) {
			// 네트워킹이 실패해도 로컬 핑 표시는 계속 동작해야 하므로 조용히 무시.
		}
	}

	// 49-47차(사용자: "미리보기 필요 없는 것들은 없애도 돼"): 핑 마크는 월드 안에 그리는 것이라
	// 작은 미리보기 칸에 담으면 실제와 전혀 달라 보인다 - 미리보기 없음(기본값 그대로).

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			int cx = previewCenterX(), cy = previewCenterY() + 20;
			int c = pingColor.getArgb();
			if (beam.get()) {
				context.fillGradient(cx - 3, previewY() + 8, cx + 3, cy, c & 0x00FFFFFF, c);
			}
			drawMarker(context, cx, cy, 1f, c, client.player != null ? myName() : "플레이어", 24, 1f);
			return;
		}
		if (client.player == null || activePings.isEmpty() || client.currentScreen != null) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		long now = System.currentTimeMillis();
		double[] out = new double[3];
		for (Ping ping : activePings) {
			// 마지막 1초 동안 서서히 사라짐, 처음 0.25초 동안 커지며 등장
			float fade = Math.min(1f, (ping.expireAtMillis - now) / 1000f);
			float pop = Math.min(1f, (now - ping.createdAtMillis) / 250f);
			float alphaMul = Math.max(0f, fade) * (0.5f + 0.5f * pop);
			int c = ping.color;
			int a = Math.round(((c >>> 24) & 0xFF) * alphaMul);
			int col = (a << 24) | (c & 0x00FFFFFF);
			if (beam.get()) {
				proj.drawBeam(context, ping.pos.x, ping.pos.y, ping.pos.z, beamHeight.get(), 0.35, col);
			}
			if (!proj.project(ping.pos.x, ping.pos.y + 0.4, ping.pos.z, out)) {
				continue;
			}
			if (out[0] < -80 || out[0] > proj.sw + 80 || out[1] < -80 || out[1] > proj.sh + 80) {
				continue;
			}
			double dx = ping.pos.x - proj.camX, dy = ping.pos.y - proj.camY, dz = ping.pos.z - proj.camZ;
			int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
			// 마커는 이름표처럼 거리에 따라 살짝 축소(최소 0.6배) - 멀어도 읽히게 하한을 둠
			float scale = (float) (0.025 * proj.sh / (2.0 * proj.tanHalf * Math.max(0.5, out[2])));
			scale = Math.max(0.6f, Math.min(1.4f, scale));
			drawMarker(context, (float) out[0], (float) out[1], scale, col, ping.name, dist, pop);
		}
	}

	/** (cx, cy)에 마름모 마커 + 위에 "이름 · 거리m" 라벨. */
	private void drawMarker(DrawContext context, float cx, float cy, float scale, int color, String name, int dist, float pop) {
		boolean xf = scale != 1f && LunaCompat.guiTransformSupported(context);
		if (xf) {
			LunaCompat.guiPush(context);
			LunaCompat.guiTranslate(context, cx, cy);
			LunaCompat.guiScale(context, scale, scale);
			cx = 0;
			cy = 0;
		}
		int x = Math.round(cx), y = Math.round(cy);
		int r = Math.round(3 + 2 * pop);
		// 마름모
		for (int i = -r; i <= r; i++) {
			int half = r - Math.abs(i);
			context.fill(x - half, y + i, x + half + 1, y + i + 1, color);
		}
		String label = (name == null || name.isEmpty() ? "" : name + " ") + "§7" + dist + "m";
		int tw = LunaCompat.getTextWidth(client.textRenderer, label);
		int lx = x - tw / 2, ly = y - r - 14;
		int bgA = Math.round(((color >>> 24) & 0xFF) * 0.55f);
		context.fill(lx - 3, ly - 2, lx + tw + 3, ly + 10, (bgA << 24));
		LunaCompat.drawHudText(context, client.textRenderer, label, lx, ly, (color & 0xFF000000) | 0x00FFFFFF);
		if (xf) {
			LunaCompat.guiPop(context);
		}
	}
}
