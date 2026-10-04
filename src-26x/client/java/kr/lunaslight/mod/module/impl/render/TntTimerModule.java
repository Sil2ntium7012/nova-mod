package kr.lunaslight.mod.module.impl.render;

// 36차: TntEntity의 남은 도화선 값은 애초에 필드가 아니라 DataTracker로 관리되고, 공개 getter
// getFuse()가 net.minecraft.entity.TntEntity(class_1541)의 method_6969로 1.16.5~1.21.8까지
// (실제 Yarn 원본 매핑 파일로 확인) 이름/시그니처 변화 없이 존재합니다. 예전엔 이 값을 리플렉션도
// 아니고 존재하지도 않는 "fuse"라는 이름의 private 필드로 착각해 @Accessor("fuse") mixin으로
// 접근하려 했었는데, 실제로는 그런 필드가 없어서(fuseTimer(1.16.5 전용)/FUSE(DataTracker 키)만
// 존재) 전 서브프로젝트 빌드 로그에 "Cannot remap fuse..." 경고가 떴었습니다. 그냥 공개 getFuse()를
// 직접 호출하는 걸로 단순화했습니다.
//
// 49-22차(사용자: "TNT 타이머 TNT 위쪽에도"): 가까운 TNT 하나의 HUD 한 줄에 더해, 감지 반경 안의
// **모든 TNT 위에** 남은 시간을 띄웁니다(LunaProjection으로 화면 투영, 이름표처럼 거리에 따라 축소).
// TNT 목록은 틱마다 갱신, 프레임에서는 투영만.

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class TntTimerModule extends Module {

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "타이머 글자의 색입니다.", 0xFFFF5555));

	private final IntSetting radius = register(new IntSetting(
			"radius", "감지 거리", "TNT를 감지하는 거리(블록)입니다.", 32, 8, 64, 1).unit("블록"));

	private final BooleanSetting hudLine = register(new BooleanSetting(
			"hud_line", "HUD 줄", "가장 가까운 TNT까지의 거리와 시간을 한 줄로 표시합니다.", true));

	private final BooleanSetting aboveTnt = register(new BooleanSetting(
			"above_tnt", "TNT 위", "TNT마다 머리 위에 남은 시간을 표시합니다.", true));

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "HUD 줄의 위치입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 174)));

	private PrimedTnt nearest;
	private List<PrimedTnt> tnts = List.of();

	public TntTimerModule() {
		super("tnt_timer", "TNT", ModuleCategory.HUD, "TNT가 터지기까지의 시간");
		enableHudStyle();
	}

	@Override
	public void onTick() {
		if (client.level == null || client.player == null) {
			nearest = null;
			tnts = List.of();
			return;
		}
		int r = radius.get();
		AABB searchBox = client.player.getBoundingBox().inflate(r);
		List<PrimedTnt> tntList = LunaCompat.getEntitiesByClass(client.level, PrimedTnt.class, searchBox);
		// 49-198차(사용자: "벽 뚫고 보이면 안 되지 - ESP잖아"): 블록에 가려 안 보이는 TNT는 위 시간도, HUD 줄도 안 띄운다.
		if (tntList != null && !tntList.isEmpty()) {
			List<PrimedTnt> seen = new java.util.ArrayList<>(tntList.size());
			for (PrimedTnt t : tntList) {
				if (kr.lunaslight.mod.module.impl.render.NametagVisibilityModule.canSeeCached(t)) {
					seen.add(t);
				}
			}
			tntList = seen;
		}

		PrimedTnt closest = null;
		double closestDistSq = Double.MAX_VALUE;
		for (PrimedTnt tnt : tntList) {
			double distSq = tnt.distanceToSqr(client.player);
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = tnt;
			}
		}
		nearest = closest;
		tnts = tntList == null ? List.of() : tntList;
	}

	private static String seconds(int fuse) {
		return String.format("%.1fs", Math.max(0, fuse) / 20f);
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview()) {
			// 미리보기: HUD 한 줄 + TNT 위 라벨 예시
			String text = "TNT: 3.2m, 1.5s";
			int tw = LunaCompat.getTextWidth(client.font, text);
			int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), tw);
			int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
			if (hudLine.get()) {
				drawHudLine(context, text, x, y, textColor.getArgb());
			}
			if (aboveTnt.get() && isPreviewBoxed()) {
				drawAbove(context, previewCenterX(), previewCenterY() - 14, 1f, "1.5s");
			}
			return;
		}
		if (client.player == null) {
			return;
		}
		if (aboveTnt.get() && !tnts.isEmpty() && kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null) {
			LunaProjection proj = LunaProjection.capture(client);
			if (proj != null) {
				double[] out = new double[3];
				for (PrimedTnt tnt : tnts) {
					if (!tnt.isAlive()) {
						continue;
					}
					Vec3 pos = LunaCompat.getPos(tnt);
					if (pos == null || !proj.project(pos.x, pos.y + 1.15, pos.z, out)) {
						continue;
					}
					if (out[0] < -60 || out[0] > proj.sw + 60 || out[1] < -60 || out[1] > proj.sh + 60) {
						continue;
					}
					float scale = (float) (0.025 * proj.sh / (2.0 * proj.tanHalf * Math.max(0.5, out[2])));
					scale = Math.max(0.35f, Math.min(1.6f, scale));
					drawAbove(context, (float) out[0], (float) out[1], scale, seconds(tnt.getFuse()));
				}
			}
		}
		if (hudLine.get() && nearest != null) {
			int fuse = nearest.getFuse();
			double distance = Math.sqrt(nearest.distanceToSqr(client.player));
			String text = String.format("TNT: %.1fm, %s", distance, seconds(fuse));
			int textWidth = LunaCompat.getTextWidth(client.font, text);
			int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
			int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
			drawHudLine(context, text, x, y, textColor.getArgb());
		}
	}

	/** (cx, baseY)를 아래 중앙으로 하는 작은 라벨(배경 + 시간). */
	private void drawAbove(GuiGraphicsExtractor context, float cx, float baseY, float scale, String text) {
		int tw = LunaCompat.getTextWidth(client.font, text);
		boolean xf = scale != 1f && LunaCompat.guiTransformSupported(context);
		if (xf) {
			LunaCompat.guiPush(context);
			LunaCompat.guiTranslate(context, cx, baseY);
			LunaCompat.guiScale(context, scale, scale);
			cx = 0;
			baseY = 0;
		}
		int x = Math.round(cx) - tw / 2;
		int y = Math.round(baseY) - 12;
		context.fill(x - 3, y - 2, x + tw + 3, y + 10, 0x80000000);
		LunaCompat.drawHudText(context, client.font, text, x, y, textColor.getArgb());
		if (xf) {
			LunaCompat.guiPop(context);
		}
	}
}
