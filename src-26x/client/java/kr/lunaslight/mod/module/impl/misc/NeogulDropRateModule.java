package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulDropRate;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 49-669차(사용자: "작물 하나 캘 때 몇 개 나오는지 확률 계산해 주는 거 만들어 줘, 허수아비같이"):
 * <b>작물 드랍 확률</b> HUD - 캔 수와 얻은 수로 지금 내 드랍을 계속 잰다({@link NeogulDropRate}).
 * <pre>
 *  작물 드랍 확률
 *  수박  1개에 7.42개  (수확 312)
 *  밀  1개에 2.84개  (수확 1,203)
 *  사과  나뭇잎 41개에 1개  (2.44%)
 * </pre>
 * 마지막으로 캔 작물은 금색. 아직 하나도 안 나온 작물은 "아직 안 나옴".
 */
public class NeogulDropRateModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 110)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));
	private final BooleanSetting onlyLast = register(new BooleanSetting(
			"only_last", "마지막 작물만", "마지막으로 캔 작물 한 줄만 보여 줍니다.", false));
	private final ActionSetting reset = register(new ActionSetting(
			"reset", "기록 지우기", "지금까지 센 작물과 얻은 수를 0으로 되돌립니다.", "지우기", NeogulDropRate::reset));

	public NeogulDropRateModule() {
		super("neogul_drop_rate", "작물 드랍 확률", ModuleCategory.SERVER, "작물 하나를 캘 때 몇 개 나오는지, 사과는 나뭇잎 몇 개에 하나 나오는지 잽니다");
		serverGroup("너굴마을");
		newFeature("49-669");
		enableHudStyle();
		NeogulDropRate.init();
	}

	private static String line(NeogulDropRate.Crop c, long breaks, long got, boolean hot) {
		String name = (hot ? "§e" : "§f") + c.label() + "§r  ";
		if (c.perLeaves()) {
			if (got <= 0) {
				return String.format(Locale.ROOT, "%s§7아직 안 나옴  (나뭇잎 %,d개)", name, breaks);
			}
			double per = breaks / (double) got;
			return String.format(Locale.ROOT, "%s나뭇잎 §e%,.1f개§r에 1개  §7(%.2f%%)", name, per, got * 100.0 / breaks);
		}
		if (got <= 0) {
			return String.format(Locale.ROOT, "%s§7아직 안 나옴  (수확 %,d)", name, breaks);
		}
		return String.format(Locale.ROOT, "%s1개에 §e%.2f개  §7(수확 %,d)", name, got / (double) breaks, breaks);
	}

	private List<String> lines(boolean sample) {
		List<String> out = new ArrayList<>();
		out.add("작물 드랍 확률");
		if (sample) {
			out.add(line(NeogulDropRate.CROPS.get("melon"), 312, 2315, true));
			out.add(line(NeogulDropRate.CROPS.get("wheat"), 1203, 3417, false));
			out.add(line(NeogulDropRate.CROPS.get("leaves"), 902, 22, false));
			return out;
		}
		String last = NeogulDropRate.lastKey;
		for (Map.Entry<String, NeogulDropRate.Crop> e : NeogulDropRate.CROPS.entrySet()) {
			long[] s = NeogulDropRate.STATS.get(e.getKey());
			if (s == null || s[0] <= 0) {
				continue;
			}
			boolean hot = e.getKey().equals(last);
			if (onlyLast.get() && !hot) {
				continue;
			}
			out.add(line(e.getValue(), s[0], s[1], hot));
		}
		if (out.size() == 1) {
			out.add("§7작물을 캐면 여기에 나옵니다");
		}
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
			NeogulDropRate.load();
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
