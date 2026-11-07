package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulScareRate;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 49-572차(사용자: "허수아비 확률 계산기 넣어서 농사한 개수 비례 현재 본인 허수아비 등장 확률 계속 측정해 줘, 나온 것만으로도"):
 * <b>허수아비 확률</b> HUD - 캔 작물 수와 나온 허수아비 수로 지금 내 확률을 계속 잰다({@link NeogulScareRate}).
 * <pre>
 *  허수아비 확률 0.0197%
 *  5,078번 캘 때 1번 꼴
 *  작물 15,234개  허수아비 3번
 *  마지막 허수아비 뒤 1,203개
 * </pre>
 * 아직 0번이면 "아직 안 나옴"과 캔 수만.
 */
public class NeogulScareRateModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 60)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));
	private final ActionSetting reset = register(new ActionSetting(
			"reset", "기록 지우기", "지금까지 센 작물과 허수아비 수를 0으로 되돌립니다.", "지우기", NeogulScareRate::reset));

	public NeogulScareRateModule() {
		super("neogul_scare_rate", "허수아비 확률", ModuleCategory.SERVER, "캔 작물 수로 내 허수아비 확률을 계속 재요");
		serverGroup("너굴마을");
		newFeature("49-572");
		enableHudStyle();
		NeogulScareRate.init();
	}

	private List<String> lines(boolean sample) {
		long h = sample ? 15234 : NeogulScareRate.harvests;
		long s = sample ? 3 : NeogulScareRate.scares;
		long since = sample ? 1203 : NeogulScareRate.sinceLast;
		// 49-579차(사용자: "확률 정리가 안 된 글만 보이는 느낌"): 흔들림 폭 줄을 빼고, 맨 위에 확률만 크게 보이게(금색), 아래는 흐린 글씨로 짧게.
		List<String> out = new ArrayList<>();
		if (s > 0 && h > 0) {
			double r = s / (double) h;
			out.add(String.format(Locale.ROOT, "허수아비 확률 §e%.4f%%", r * 100));
			out.add(String.format(Locale.ROOT, "§7%,d번 캘 때 1번 꼴", Math.round(1 / r)));
		} else {
			out.add("허수아비 확률 §7아직 안 나옴");
		}
		out.add(String.format(Locale.ROOT, "§7작물 %,d개  허수아비 %,d번", h, s));
		out.add(String.format(Locale.ROOT, "§7마지막 허수아비 뒤 %,d개", since));
		return out;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		boolean sample = isPreview();
		if (!sample) {
			if (client.player == null || !NeogulData.onNeogul(client)) {
				return;
			}
			NeogulScareRate.load();
		}
		List<String> ls = lines(sample);
		int w = hudLinesWidth(ls), h = hudLinesHeight(ls);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), h);
		drawHudLines(ctx, ls, x, y, textColor.getArgb());
	}

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		List<String> ls = lines(true);
		int lw = hudLinesWidth(ls), lh = hudLinesHeight(ls);
		drawHudLines(ctx, ls, x + (w - lw) / 2, y + (h - lh) / 2, 0xFFFFFFFF);
	}
}
