package kr.lunaslight.mod.module.impl.waypoint;

import kr.lunaslight.mod.util.WindowAccess;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 웨이포인트.
 *
 * 49-21차 전면 재작업(사용자: "찍으면 그 위치에 레이저가 생기고, 나침반이랑 연동돼서 나침반 축 아래에
 * 뜨고, 여러 개면 동시에 보이고, 관리하는 창도 따로, 지우는 기능도"):
 *  - 찍기: 추가 키 → 지금 위치에 "웨이포인트 N". 관리 키(또는 설정 화면의 [관리]) → LunaWaypointScreen.
 *  - 레이저: 월드 렌더 패스에서 각 웨이포인트 위치에 색 있는 빛기둥(아이템 빛기둥과 같은 가산 혼합 쿼드).
 *  - 라벨: 화면 투영(LunaProjection)으로 "이름 · 123m"을 항상(벽 너머도) 표시.
 *  - 나침반: CompassModule이 getVisible()을 읽어 축 아래에 방위각 위치로 마커를 찍는다.
 *  - 저장: config/lunaslight/waypoints.json (이름/좌표/색/표시 여부/차원).
 */
public class WaypointModule extends Module {

	public static class Waypoint {
		public String name;
		public double x, y, z;
		public int color;          // ARGB, 0이면 기본 연두
		public boolean visible = true;
		public String dimension;   // 예: minecraft:overworld (null이면 전부에서 표시)

		public Waypoint() {
		}

		public Waypoint(String name, double x, double y, double z, int color, String dimension) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.dimension = dimension;
		}

		public int argb() {
			return color == 0 ? 0xFFA9D973 : color;
		}
	}

	public static final int[] PRESET_COLORS = {
		0xFFA9D973, 0xFFFF5252, 0xFFFF9436, 0xFFFFE24A, 0xFF4CD964,
		0xFF3B9CFF, 0xFF4A5AE8, 0xFFB05CFF, 0xFFFFFFFF, 0xFF00E5FF
	};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final KeybindSetting addKey = register(new KeybindSetting(
			"add_waypoint_key", "추가 키", "지금 위치에 웨이포인트를 추가합니다.", -1));

	private final KeybindSetting manageKey = register(new KeybindSetting(
			"manage_key", "관리창 키", "웨이포인트 목록, 이름, 색, 삭제를 관리하는 창을 엽니다.", -1));

	private final BooleanSetting beams = register(new BooleanSetting(
			"beams", "레이저", "웨이포인트 위치에 빛기둥을 그립니다.", true));

	private final IntSetting beamHeight = register(new IntSetting(
			"beam_height", "레이저 높이", "빛기둥 높이(블록)입니다.", 96, 8, 256, 8).unit("블록"));

	private final BooleanSetting labels = register(new BooleanSetting(
			"labels", "이름표", "웨이포인트 위치에 이름과 거리를 표시합니다.", true));

	// 49-22차엔 "이름표는 완전 가까이 가야 보이게"라 12블록이 기본이었는데, 49-91차(8-21, 사용자: "이름이 멀어지면
	// 사라짐")로 기본을 무제한(0)으로. 저장된 옛 값 12가 남아 있지 않게 id를 바꿨다(label_distance → label_range).
	private final IntSetting labelDistance = register(new IntSetting(
			"label_range", "이름표 거리", "이 거리(블록) 안에서만 이름표를 표시합니다. 0이면 항상 표시합니다.", 0, 0, 256, 2).unit("블록"));

	private final IntSetting maxDistance = register(new IntSetting(
			"max_distance", "표시 거리", "이 거리(블록)까지만 표시합니다. 0이면 무제한입니다.", 0, 0, 2000, 50).unit("블록"));

	private final List<Waypoint> waypoints = new ArrayList<>();
	private boolean prevAdd, prevManage;
	private boolean loaded;
	private String cachedDimension;
	private List<Waypoint> visibleCache;   // 49-22차: 틱마다 갱신(프레임마다 목록을 새로 만들지 않게)

	public WaypointModule() {
		super("waypoint", "웨이포인트", ModuleCategory.FEATURE, "위치 저장 | 레이저 | 나침반 연동");
	}

	// ==================== 데이터 ====================

	public List<Waypoint> getWaypoints() {
		ensureLoaded();
		return waypoints;
	}

	/** 지금 차원에서 보이는 웨이포인트(나침반/라벨/레이저용). 차원 이름은 틱마다 한 번만 갱신(리플렉션 절약). */
	public List<Waypoint> getVisible() {
		ensureLoaded();
		List<Waypoint> cached = visibleCache;
		if (cached != null) {
			return cached;
		}
		String dim = cachedDimension;
		List<Waypoint> list = new ArrayList<>();
		for (Waypoint w : waypoints) {
			if (w.visible && (w.dimension == null || dim == null || w.dimension.equals(dim))) {
				list.add(w);
			}
		}
		visibleCache = list;
		return list;
	}

	public Waypoint addHere(String name) {
		Vec3d pos = client.player == null ? null : LunaCompat.getPos(client.player);
		if (pos == null) {
			return null;
		}
		String n = name == null || name.isBlank() ? "웨이포인트 " + (waypoints.size() + 1) : name;
		Waypoint w = new Waypoint(n, Math.floor(pos.x) + 0.5, Math.floor(pos.y), Math.floor(pos.z) + 0.5,
				PRESET_COLORS[waypoints.size() % PRESET_COLORS.length], currentDimension());
		waypoints.add(w);
		save();
		LunaCompat.sendActionBar(client, "§a웨이포인트 추가: §f" + n);
		return w;
	}

	public void remove(Waypoint w) {
		waypoints.remove(w);
		save();
	}

	public String currentDimension() {
		try {
			if (client.world == null) {
				return null;
			}
			Object key = LunaCompat.getMethodCompat(client.world.getClass(), "getRegistryKey").invoke(client.world);
			Object value = LunaCompat.getMethodCompat(key.getClass(), "getValue").invoke(key);
			return value == null ? null : value.toString();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private void ensureLoaded() {
		if (!loaded) {
			loaded = true;
			load();
		}
	}

	@Override
	protected void onEnable() {
		ensureLoaded();
	}

	@Override
	protected void onDisable() {
		save();
	}

	// ==================== 입력 ====================

	@Override
	public void onTick() {
		cachedDimension = currentDimension();
		visibleCache = null;
		if (client.player == null || WindowAccess.of(client) == null) {
			prevAdd = prevManage = false;
			return;
		}
		boolean inGame = client.currentScreen == null;
		boolean add = inGame && addKey.isDown(client);
		if (add && !prevAdd) {
			addHere(null);
		}
		prevAdd = add;
		boolean manage = inGame && manageKey.isDown(client);
		if (manage && !prevManage) {
			LunaCompat.openScreenReflectively("kr.lunaslight.mod.gui.LunaWaypointScreen", null);
		}
		prevManage = manage;
	}

	// ==================== 레이저(월드) ====================

	@Override
	public void onWorldRender(Object context) {
		if (!beams.get() || client.world == null || client.player == null) {
			return;
		}
		List<Waypoint> list = getVisible();
		if (list.isEmpty()) {
			return;
		}
		Vec3d camPos = LunaCompat.getWorldRenderCameraPos(context);
		MatrixStack matrices = LunaCompat.getMatrices(context);
		if (camPos == null || matrices == null) {
			return;
		}
		VertexConsumer beam = LunaCompat.getBeamBuffer(LunaCompat.call(context, "consumers"));
		if (beam == null) {
			return;
		}
		double limit = maxDistance.get() <= 0 ? Double.MAX_VALUE : (double) maxDistance.get() * maxDistance.get();
		float h = beamHeight.get();
		for (Waypoint w : list) {
			double dx = w.x - camPos.x, dz = w.z - camPos.z;
			if (dx * dx + dz * dz > limit) {
				continue;
			}
			int argb = w.argb();
			float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
			float bx = (float) dx, by = (float) (w.y - camPos.y), bz = (float) dz;
			// 멀수록 기둥을 굵게(화면에서 너무 가늘어지지 않게)
			double dist = Math.sqrt(dx * dx + dz * dz);
			float inner = (float) Math.max(0.08, Math.min(0.6, dist / 120.0));
			float d = inner * 0.7071f;
			LunaCompat.emitBeamQuad(matrices, beam, bx - inner, bz, bx + inner, bz, by, by + h, r, g, b, 0.75f, 0.05f);
			LunaCompat.emitBeamQuad(matrices, beam, bx, bz - inner, bx, bz + inner, by, by + h, r, g, b, 0.75f, 0.05f);
			LunaCompat.emitBeamQuad(matrices, beam, bx - d, bz - d, bx + d, bz + d, by, by + h, r, g, b, 0.75f, 0.05f);
			LunaCompat.emitBeamQuad(matrices, beam, bx - d, bz + d, bx + d, bz - d, by, by + h, r, g, b, 0.75f, 0.05f);
			float glowW = inner * 3f;
			LunaCompat.emitBeamQuad(matrices, beam, bx - glowW, bz, bx + glowW, bz, by, by + h * 0.6f, r, g, b, 0.22f, 0f);
			LunaCompat.emitBeamQuad(matrices, beam, bx, bz - glowW, bx, bz + glowW, by, by + h * 0.6f, r, g, b, 0.22f, 0f);
		}
	}

	// ==================== 라벨(화면 투영) ====================

	// 49-47차(사용자: "미리보기 필요 없는 것들은 없애도 돼"): 웨이포인트는 월드 안에 그리는 것이라
	// 작은 미리보기 칸에 담으면 실제와 전혀 달라 보인다 - 미리보기 없음(기본값 그대로).

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			// 미리보기: 레이저 + 이름표 예시
			int cx = previewCenterX(), cy = previewCenterY() + 24;
			int c = PRESET_COLORS[0];
			if (beams.get()) {
				context.fillGradient(cx - 4, previewY() + 6, cx + 4, cy, c & 0x00FFFFFF, c);
			}
			if (labels.get()) {
				drawLabel(context, cx, cy, "집 §712m", c);
			}
			return;
		}
		if (client.player == null || client.currentScreen != null) {
			return;
		}
		List<Waypoint> list = getVisible();
		if (list.isEmpty()) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		double limit = maxDistance.get() <= 0 ? Double.MAX_VALUE : (double) maxDistance.get() * maxDistance.get();
		// 월드 렌더 이벤트가 없는 버전: 레이저를 HUD 투영으로 대신 그림(벽 너머로도 보임)
		if (beams.get() && !LunaCompat.worldRenderAvailable()) {
			float h = beamHeight.get();
			for (Waypoint w : list) {
				double dx = w.x - proj.camX, dz = w.z - proj.camZ;
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				proj.drawBeam(context, w.x, w.y, w.z, h, 0.3, (0xB4 << 24) | (w.argb() & 0x00FFFFFF));
			}
		}
		if (!labels.get()) {
			return;
		}
		double[] out = new double[3];
		double labelLimit = labelDistance.get() <= 0 ? Double.MAX_VALUE : (double) labelDistance.get() * labelDistance.get();
		for (Waypoint w : list) {
			double dx = w.x - proj.camX, dy = w.y - proj.camY, dz = w.z - proj.camZ;
			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > limit || distSq > labelLimit) {
				continue;
			}
			// 라벨은 기둥 아래쪽(지면 + 1.5)에서 조금 위
			if (!proj.project(w.x, w.y + 1.5, w.z, out)) {
				continue;
			}
			if (out[0] < -40 || out[0] > proj.sw + 40 || out[1] < -20 || out[1] > proj.sh + 20) {
				continue;
			}
			int dist = (int) Math.round(Math.sqrt(distSq));
			drawLabel(context, (int) Math.round(out[0]), (int) Math.round(out[1]), w.name + " §7" + dist + "m", w.argb());
		}
	}

	/** (cx, cy)를 중심으로 색 점 + 이름 라벨. */
	private void drawLabel(DrawContext context, int cx, int cy, String text, int color) {
		int tw = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = cx - tw / 2;
		int y = cy - 5;
		context.fill(x - 9, y - 3, x + tw + 4, y + 11, 0x99000000);
		kr.lunaslight.mod.gui.LunaDraw.circle(context, x - 6, y + 1, 6, color);
		LunaCompat.drawHudText(context, client.textRenderer, text, x + 2, y + 1, 0xFFFFFFFF);
	}

	// ==================== 저장/불러오기 ====================

	private static Path waypointsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("waypoints.json");
	}

	public void save() {
		visibleCache = null;
		Path path = waypointsFile();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(waypoints, writer);
			}
		} catch (IOException e) {
			LunaClientMod.LOGGER.error("[Nova] 웨이포인트 저장 실패", e);
		}
	}

	public void load() {
		Path path = waypointsFile();
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Type listType = new TypeToken<List<Waypoint>>() {}.getType();
			List<Waypoint> loaded = GSON.fromJson(reader, listType);
			waypoints.clear();
			if (loaded != null) {
				waypoints.addAll(loaded);
			}
		} catch (Exception e) {
			LunaClientMod.LOGGER.error("[Nova] 웨이포인트 불러오기 실패", e);
		}
	}
}
