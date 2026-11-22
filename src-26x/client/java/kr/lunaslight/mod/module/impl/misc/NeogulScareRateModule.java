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
	// 49-707차(사용자: "그래도 작물 캔 거 통합은 남아 있으면 좋겠는데"): 맨 아래에 모든 작물 합계 한 줄
	private final kr.lunaslight.mod.module.setting.BooleanSetting showTotal = register(new kr.lunaslight.mod.module.setting.BooleanSetting(
			"show_total", "전체 합계", "모든 작물을 합친 수확 수, 허수아비 수, 확률을 맨 아래에 같이 보여 줍니다.", true));
	private final ActionSetting reset = register(new ActionSetting(
			"reset", "기록 지우기", "작물마다 센 수확과 허수아비 수를 모두 0으로 되돌립니다.", "지우기", NeogulScareRate::reset));

	public NeogulScareRateModule() {
		super("neogul_scare_rate", "허수아비 확률", ModuleCategory.SERVER, "지금 캐는 작물의 허수아비 확률을 작물마다 따로 잽니다");
		serverGroup("너굴마을");
		newFeature("49-572");
		enableHudStyle();
		NeogulScareRate.init();
	}

	private List<String> lines(boolean sample) {
		// 49-706차(사용자: "캐고 있는 작물마다 허수아비 확률 다르게 하고 내가 캐고 있는 작물만 보이게"): 지금 캐는 작물(마지막으로 캔 것)의 것만.
		String key = sample ? "wheat" : NeogulScareRate.currentKey;
		long[] c = sample ? new long[] {15234, 3, 1203} : NeogulScareRate.crop(key);
		if (key == null || c == null) {
			return null;
		}
		long h = c[0], s = c[1];
		String name = NeogulScareRate.label(key);
		// 49-709차(사용자: "마지막 뒤 말고 최대한 괄호로 적게"): 두 줄 - "밀 허수아비 0.0197% (3/15,234)", "전체 0.0215% (9/41,870)".
		List<String> out = new ArrayList<>();
		out.add(name + " 허수아비 " + pct(s, h) + String.format(Locale.ROOT, " §7(%,d/%,d)", s, h));
		if (showTotal.get()) {
			long th = sample ? 41870 : NeogulScareRate.harvests;
			long ts = sample ? 9 : NeogulScareRate.scares;
			out.add("§7전체 " + pct(ts, th) + String.format(Locale.ROOT, " §7(%,d/%,d)", ts, th));
		}
		return out;
	}

	private static String pct(long s, long h) {
		return s > 0 && h > 0 ? String.format(Locale.ROOT, "§e%.4f%%", s * 100.0 / h) : "§7아직 없음";
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
		if (ls == null) {
			return;   // 49-706차: 아직 캔 작물이 없음
		}
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
