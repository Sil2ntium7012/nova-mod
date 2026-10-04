package kr.lunaslight.mod.module.impl.waypoint;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 나침반 - 화면 상단 가로 바. 화면 중앙 = 내가 보는 방향(yaw).
 *
 * 49-21차 재작업(사용자: "EWSN이 막대기랑 겹쳐서 안 보이고, 검정 배경이 이상하고, 바라보는 곳 막대기
 * 대신 작은 화살표, 각도 숫자는 옆으로" + 웨이포인트 연동):
 *  - 배경 상자 제거(투명 위에 글자·눈금만, 가독성은 그림자 텍스트로).
 *  - 배치: [화살표 ▼] → [방위 글자 줄] → [눈금 줄(아래로 늘어짐)] → [웨이포인트 마커 줄]. 글자와
 *    눈금이 세로로 분리돼 절대 안 겹침.
 *  - 각도 숫자는 49-32차에 뺐다(사용자: 나침반은 방향만).
 *  - 웨이포인트: WaypointModule.getVisible()의 각 지점을 방위각 위치에 색 점으로, 중앙에 가장 가까운
 *    것 하나는 이름·거리까지.
 * 마인크래프트 yaw: 남=0, 서=90, 북=180, 동=-90/270.
 */
public class CompassModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_CENTER, 0, 4)));

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "방위 글자의 색입니다.", 0xFFFFFFFF));

	private final ColorSetting arrowColor = register(new ColorSetting(
			"arrow_color", "화살표 색", "가운데 화살표의 색입니다.", 0xFFA9D973));

	private final IntSetting width = register(new IntSetting(
			"width", "폭", "나침반 폭(픽셀)입니다.", 200, 100, 400, 10).unit("px"));

	private final IntSetting range = register(new IntSetting(
			"range", "표시 범위", "나침반에 담기는 각도입니다.", 180, 60, 360, 10).unit("°"));

	private final BooleanSetting showTicks = register(new BooleanSetting(
			"show_ticks", "눈금", "5°, 15°, 45° 눈금을 표시합니다.", true));

	private final BooleanSetting showIntercardinals = register(new BooleanSetting(
			"show_intercardinals", "8방위", "북동, 남동, 남서, 북서도 표시합니다.", true));


	private final BooleanSetting showWaypoints = register(new BooleanSetting(
			"show_waypoints", "웨이포인트", "축 아래에 웨이포인트 마커를 표시합니다.", true));

	private static final Map<String, Float> CARDINALS = new LinkedHashMap<>();
	private static final Map<String, Float> INTERCARDINALS = new LinkedHashMap<>();

	static {
		CARDINALS.put("N", 180f);
		CARDINALS.put("E", -90f);
		CARDINALS.put("S", 0f);
		CARDINALS.put("W", 90f);
		INTERCARDINALS.put("NE", -135f);
		INTERCARDINALS.put("SE", -45f);
		INTERCARDINALS.put("SW", 45f);
		INTERCARDINALS.put("NW", 135f);
	}

	public CompassModule() {
		super("compass_hud", "나침반", ModuleCategory.HUD, "상단 가로 나침반과 웨이포인트 마커");
	}

	private static final int ARROW_H = 4;   // ▼
	private static final int LABEL_H = 9;   // 방위 글자 줄
	private static final int TICK_H = 5;    // 눈금 줄 - 49-22차: 큰 방향 막대(45°) 7 → 5px로 축소
	private static final int MARK_H = 14;   // 마커 줄(점 + 이름)

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (client.player == null && !isPreview()) {
			return;
		}
		// 미리보기: 북쪽(180°)을 바라보는 고정 방향 + 예시 마커
		float yaw = isPreview() ? 172f : LunaCompat.getYaw(client.player) % 360f;
		if (yaw < 0) {
			yaw += 360f;
		}
		int barW = isPreviewBoxed() ? Math.min(width.get(), previewW() - 40) : width.get();
		int totalH = ARROW_H + 1 + LABEL_H + 1 + TICK_H + (showWaypoints.get() ? MARK_H : 0);
		int left = isPreviewBoxed() ? previewCenterX() - barW / 2 : position.get().resolveX(client.getWindow().getGuiScaledWidth(), barW);
		int top = isPreviewBoxed() ? previewCenterY() - totalH / 2 : position.get().resolveY(client.getWindow().getGuiScaledHeight(), totalH);
		int right = left + barW;
		int centerX = left + barW / 2;

		float halfRange = range.get() / 2f;
		float ppd = barW / (float) range.get(); // pixels per degree
		int textCol = textColor.getArgb();

		// ▼ 가운데 화살표(글자 위)
		int ay = top;
		int ac = arrowColor.getArgb();
		context.fill(centerX - 3, ay, centerX + 4, ay + 1, ac);
		context.fill(centerX - 2, ay + 1, centerX + 3, ay + 2, ac);
		context.fill(centerX - 1, ay + 2, centerX + 2, ay + 3, ac);
		context.fill(centerX, ay + 3, centerX + 1, ay + 4, ac);

		// 방위 글자 줄
		int labelY = top + ARROW_H + 1;
		drawLabels(context, CARDINALS, yaw, centerX, labelY, ppd, halfRange, left, right, textCol);
		if (showIntercardinals.get()) {
			drawLabels(context, INTERCARDINALS, yaw, centerX, labelY, ppd, halfRange, left, right,
					(0xB4 << 24) | (textCol & 0x00FFFFFF));
		}

		// 눈금 줄(글자 아래로 늘어짐)
		int tickTop = labelY + LABEL_H + 1;
		if (showTicks.get()) {
			for (int deg = 0; deg < 360; deg += 5) {
				float diff = shortestAngleDiff(yaw, deg);
				if (Math.abs(diff) > halfRange) {
					continue;
				}
				int tx = centerX + Math.round(diff * ppd);
				if (tx < left || tx >= right) {
					continue;
				}
				boolean major = deg % 45 == 0;
				boolean mid = deg % 15 == 0;
				int len = major ? TICK_H : (mid ? 3 : 2);
				int alpha = major ? 0xE6 : (mid ? 0x99 : 0x66);
				context.fill(tx, tickTop, tx + 1, tickTop + len, (alpha << 24) | (textCol & 0x00FFFFFF));
			}
		}
		// 눈금 기준선(얇게)
		context.fill(left, tickTop, right, tickTop + 1, (0x66 << 24) | (textCol & 0x00FFFFFF));

		// 49-32차: 지금 보는 각도 숫자는 뺐다(사용자 요청 - 나침반은 방향만).

		// 웨이포인트 마커 줄
		if (showWaypoints.get()) {
			drawWaypointMarkers(context, yaw, centerX, tickTop + TICK_H + 1, ppd, halfRange, left, right);
		}
	}

	private void drawLabels(GuiGraphicsExtractor context, Map<String, Float> labels, float yaw, int centerX, int y,
			float ppd, float halfRange, int left, int right, int color) {
		for (Map.Entry<String, Float> entry : labels.entrySet()) {
			float diff = shortestAngleDiff(yaw, entry.getValue());
			if (Math.abs(diff) > halfRange) {
				continue;
			}
			String label = entry.getKey();
			int lw = LunaCompat.getTextWidth(client.font, label);
			int cx = centerX + Math.round(diff * ppd);
			if (cx < left || cx > right) {
				continue;
			}
			// 가장자리에서 서서히 사라지게
			float edge = 1f - Math.min(1f, Math.abs(diff) / halfRange);
			int a = Math.round(((color >>> 24) & 0xFF) * Math.min(1f, 0.25f + edge * 1.5f));
			LunaCompat.drawHudText(context, client.font, label, cx - lw / 2, y, (a << 24) | (color & 0x00FFFFFF));
		}
	}

	/** 마커 한 점(웨이포인트/핑 공용): 위치·색·이름. */
	private record Marker(double x, double y, double z, int color, String name) {
	}

	private final List<Marker> markerScratch = new java.util.ArrayList<>();

	private List<Marker> collectMarkers() {
		List<Marker> list = markerScratch;
		list.clear();
		if (isPreview()) {
			list.add(new Marker(0, 0, 0, 0xFFA9D973, "집"));
			return list;
		}
		var opt = ModuleManager.get().find("waypoint");
		if (opt.isPresent() && opt.get() instanceof WaypointModule wm && wm.isEnabled()) {
			for (WaypointModule.Waypoint w : wm.getVisible()) {
				list.add(new Marker(w.x, w.y, w.z, w.argb(), w.name));
			}
		}
		// 49-22차: 핑도 나침반에(사용자 요청)
		var pingOpt = ModuleManager.get().find("ping_mark");
		if (pingOpt.isPresent() && pingOpt.get().isEnabled()) {
			for (PingMarkModule.Ping ping : PingMarkModule.getPings()) {
				list.add(new Marker(ping.pos.x, ping.pos.y, ping.pos.z, ping.color,
						ping.name == null || ping.name.isEmpty() ? "핑" : ping.name));
			}
		}
		return list;
	}

	private void drawWaypointMarkers(GuiGraphicsExtractor context, float yaw, int centerX, int y, float ppd,
			float halfRange, int left, int right) {
		List<Marker> list = collectMarkers();
		if (list.isEmpty()) {
			return;
		}
		Vec3 p;
		if (isPreview()) {
			// 미리보기: 마커가 중앙에서 살짝 오른쪽(북동쪽 20m)에 오도록
			p = new Vec3(-6, 0, 18);
		} else {
			p = LunaCompat.getPos(client.player);
		}
		if (p == null) {
			return;
		}
		Marker nearestCenter = null;
		float nearestDiff = Float.MAX_VALUE;
		for (Marker w : list) {
			double dx = w.x() - p.x, dz = w.z() - p.z;
			float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz)); // yaw 규약
			float diff = shortestAngleDiff(yaw, bearing);
			if (Math.abs(diff) > halfRange) {
				continue;
			}
			int mx = centerX + Math.round(diff * ppd);
			if (mx < left || mx > right) {
				continue;
			}
			int c = w.color();
			// 작은 마름모(5px)
			context.fill(mx, y, mx + 1, y + 1, c);
			context.fill(mx - 1, y + 1, mx + 2, y + 2, c);
			context.fill(mx - 2, y + 2, mx + 3, y + 3, c);
			context.fill(mx - 1, y + 3, mx + 2, y + 4, c);
			context.fill(mx, y + 4, mx + 1, y + 5, c);
			if (Math.abs(diff) < nearestDiff) {
				nearestDiff = Math.abs(diff);
				nearestCenter = w;
			}
		}
		if (nearestCenter != null && nearestDiff <= 12f) {
			double dx = nearestCenter.x() - p.x, dy = nearestCenter.y() - p.y, dz = nearestCenter.z() - p.z;
			int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
			String text = nearestCenter.name() + " §7" + dist + "m";
			int tw = LunaCompat.getTextWidth(client.font, text);
			float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
			int mx = centerX + Math.round(shortestAngleDiff(yaw, bearing) * ppd);
			int tx = Math.max(left, Math.min(right - tw, mx - tw / 2));
			LunaCompat.drawHudText(context, client.font, text, tx, y + 6, 0xFFFFFFFF);
		}
	}

	/** target - current를 -180~180 범위로 정규화(최단 회전 방향/거리). */
	private static float shortestAngleDiff(float current, float target) {
		float diff = (target - current) % 360f;
		if (diff < -180f) {
			diff += 360f;
		}
		if (diff > 180f) {
			diff -= 360f;
		}
		return diff;
	}
}
