package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-617차(사용자: "mine으로 일반 광산이랑 고급 광산, 다이아몬드 광맥 이렇게 나뉘는데 어디 광산인지 몇 번 광산인지 화면 위에 띄워 줘"):
 * <b>광산 위치</b>. 지금 월드(차원) 이름으로: mine1_N = 일반 광산 N번, mine2_N = 고급 광산 N번, mine_diamond = 다이아몬드 광맥.
 * 화면 위 가운데에 작은 상자로. 광산이 아니면 안 보인다.
 */
public class NeogulMineWhereModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_CENTER, 0, 4)));

	private static final Pattern MINE = Pattern.compile("^mine(\\d+)(?:_(\\d+))?$");

	public NeogulMineWhereModule() {
		super("neogul_mine_where", "광산 위치", ModuleCategory.SERVER, "광산에 있으면 어느 광산 몇 번인지 화면 위에");
		serverGroup("너굴마을");
		newFeature("49-617");
	}

	/** 지금 차원 이름(이름공간 뺀 것). */
	private String dimPath() {
		try {
			String ds = String.valueOf(client.level.dimension());   // ResourceKey[minecraft:dimension / minecraft:mine1_5]
			int i = ds.lastIndexOf(" / ");
			String dim = i >= 0 ? ds.substring(i + 3).replace("]", "").trim() : ds;
			return dim.indexOf(':') >= 0 ? dim.substring(dim.indexOf(':') + 1) : dim;
		} catch (Throwable t) {
			return "";
		}
	}

	/** {광산 이름, 번호(없으면 null)} 또는 광산이 아니면 null. */
	static String[] where(String path) {
		if (path == null) {
			return null;
		}
		String p = path.toLowerCase(java.util.Locale.ROOT);
		if (p.startsWith("mine_diamond")) {
			Matcher n = Pattern.compile("_(\\d+)$").matcher(p);
			return new String[]{"다이아몬드 광맥", n.find() ? n.group(1) : null};
		}
		Matcher m = MINE.matcher(p);
		if (!m.find()) {
			return null;
		}
		String kind = switch (m.group(1)) {
			case "1" -> "일반 광산";
			case "2" -> "고급 광산";
			default -> "광산 " + m.group(1);
		};
		return new String[]{kind, m.group(2)};
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		String[] w;
		if (isPreview()) {
			w = new String[]{"고급 광산", "2"};
		} else {
			if (client.player == null || client.level == null || !NeogulData.onNeogul(client)) {
				return;
			}
			w = where(dimPath());
			if (w == null) {
				return;
			}
		}
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		String a = w[0], b = w[1] == null ? "" : "  " + w[1] + "번";
		int tw = LunaDraw.width(client.font, a) + LunaDraw.width(client.font, b);
		int bw = tw + 22, bh = 15;
		int x = position.get().resolveX(sw, bw);
		int y = position.get().resolveY(sh, bh);
		draw(ctx, x, y, bw, bh, a, b);
	}

	private void draw(GuiGraphicsExtractor ctx, int x, int y, int bw, int bh, String a, String b) {
		drawHudPanel(ctx, x, y, bw, bh);
		LunaDraw.roundRect(ctx, x + 5, y + 4, 3, bh - 8, 1, 0xFFFFD36A);
		int ty = LunaDraw.textY(y, bh);
		LunaCompat.drawHudText(ctx, client.font, a, x + 12, ty, 0xFFFFD36A);
		if (!b.isEmpty()) {
			LunaCompat.drawHudText(ctx, client.font, b, x + 12 + LunaDraw.width(client.font, a), ty, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public String newFeatureScene() {
		return "stone";
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		String a = "고급 광산", b = "  2번";
		int bw = LunaDraw.width(client.font, a) + LunaDraw.width(client.font, b) + 22;
		draw(ctx, x + (w - bw) / 2, y + 8, bw, 15, a, b);
	}
}
