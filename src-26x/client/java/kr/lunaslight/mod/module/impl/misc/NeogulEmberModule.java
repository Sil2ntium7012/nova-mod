package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulDungeonHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-606차(사용자 사진: 왼쪽 위 돈/도토리/지역 상자, 초록 잉걸불 설명 - "왼쪽 위 표시가 섬일 때 내 창고에 4가지 잉걸불 중 하나라도 있다면
 * 특정 위치에 있다고 표시, 껐다 켰다 X 항상 켬"): <b>창고 잉걸불</b>. 서버 HUD 지역이 섬이고 기억한 창고(/창고)에 초록, 푸른, 잿빛, 붉은 잉걸불이
 * 있으면 왼쪽 위 서버 상자 바로 아래에 같은 모양 상자로 "그림 + 개수 + 든 창고"를 한 줄씩. 창고 내용은 창고 미리보기가 기억한 것(열어 본 창고).
 */
public class NeogulEmberModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다. 기본은 왼쪽 위 서버 상자 바로 아래입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 5, 62)));

	private static final int BG = 0xE6493524, EDGE = 0xFF7D6142, TEXT = 0xFFF5E9D2, DIM = 0xFFC9B48E;

	public NeogulEmberModule() {
		super("neogul_ember", "창고 잉걸불", ModuleCategory.SERVER, "섬에 있을 때 창고에 든 잉걸불과 그 창고를 왼쪽 위에");
		serverGroup("너굴마을");
		alwaysOn();
		newFeature("49-606");
	}

	/** 49-619차(사용자: 너굴라이언트 - "잉걸불 개수 확인 삭제"): 너굴라이언트로 켰으면 없는 기능. */
	@Override
	public boolean isEnabled() {
		return !kr.lunaslight.mod.util.LunaSocial.nugulient && super.isEnabled();
	}

	@Override
	public boolean hiddenInList() {
		return kr.lunaslight.mod.util.LunaSocial.nugulient || super.hiddenInList();
	}

	@Override
	public boolean newFeatureAsked() {
		return kr.lunaslight.mod.util.LunaSocial.nugulient || super.newFeatureAsked();
	}

	/** 늘 켜져 있어서 새 기능 창은 알려 주기만 한다. */
	@Override
	public boolean newFeatureTogglesModule() {
		return false;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		List<Object[]> rows = new ArrayList<>();
		if (isPreview()) {
			rows.add(new Object[]{new ItemStack(Items.SLIME_BALL), 128, "3번 창고"});
			rows.add(new Object[]{new ItemStack(Items.BLAZE_POWDER), 12, "던전 창고"});
		} else {
			if (client.player == null || !NeogulData.onNeogul(client) || !NeogulDungeonHud.onIsland()) {
				return;
			}
			for (NeogulStorageModule.EmberSpot e : NeogulStorageModule.embers()) {
				rows.add(new Object[]{e.icon(), e.count(), e.where()});
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		int w = 0;
		for (Object[] r : rows) {
			w = Math.max(w, 16 + LunaDraw.width(client.font, String.format("%,d", (Integer) r[1])) + 8 + LunaDraw.width(client.font, (String) r[2]) + 6);
		}
		w = Math.max(w, 72);
		int rowH = 14, gap = 2;
		int h = rows.size() * rowH + (rows.size() - 1) * gap;
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		draw(ctx, x, y, w, rows, rowH, gap);
	}

	private void draw(GuiGraphicsExtractor ctx, int x, int y, int w, List<Object[]> rows, int rowH, int gap) {
		for (int i = 0; i < rows.size(); i++) {
			Object[] r = rows.get(i);
			int ry = y + i * (rowH + gap);
			LunaDraw.roundRectBordered(ctx, x, ry, w, rowH, 2, BG, EDGE);
			ItemStack icon = (ItemStack) r[0];
			if (icon != null && !icon.isEmpty()) {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiTranslate(ctx, x + 2, ry + 1);
				LunaCompat.guiScale(ctx, 0.75f, 0.75f);
				ctx.item(icon, 0, 0);
				LunaCompat.guiPop(ctx);
			}
			int ty = LunaDraw.textY(ry, rowH);
			String n = String.format("%,d", (Integer) r[1]);
			LunaDraw.text(ctx, client.font, n, x + 16, ty, TEXT);
			String where = (String) r[2];
			LunaDraw.text(ctx, client.font, where, x + w - 5 - LunaDraw.width(client.font, where), ty, DIM);
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

	private static ItemStack ember(String key, net.minecraft.world.item.Item fallback) {
		try {
			ItemStack st = kr.lunaslight.mod.util.NeogulData.icon(kr.lunaslight.mod.util.NeogulData.get(key), 1);   // 서버 팩 모양
			if (st != null && !st.isEmpty()) {
				return st;
			}
		} catch (Throwable ignored) {
		}
		return new ItemStack(fallback);
	}

	/** 49-612차(사용자: "인게임처럼 보이게, 그림 이상해"): 왼쪽 위 서버 상자(돈, 도토리, 지역) 바로 아래에 붙는 모습 그대로. */
	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		List<Object[]> server = new ArrayList<>();
		server.add(new Object[]{new ItemStack(Items.GOLD_NUGGET), 14172222, ""});
		server.add(new Object[]{new ItemStack(Items.COCOA_BEANS), 128, ""});
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{ember("ember_g", Items.SLIME_BALL), 128, "3번 창고"});
		rows.add(new Object[]{ember("ember_r", Items.BLAZE_POWDER), 12, "던전 창고"});
		int bw = 110;
		draw(ctx, x + 5, y + 5, bw, server, 14, 2);
		// 지역 상자: "섬"
		LunaDraw.roundRectBordered(ctx, x + 5, y + 37, bw, 14, 2, BG, EDGE);
		LunaDraw.text(ctx, client.font, "섬", x + 5 + bw - 5 - LunaDraw.width(client.font, "섬"), LunaDraw.textY(y + 37, 14), TEXT);
		draw(ctx, x + 5, y + 55, bw, rows, 14, 2);
	}
}
