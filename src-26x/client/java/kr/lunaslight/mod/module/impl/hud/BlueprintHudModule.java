package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.impl.waypoint.BlueprintModule;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.Blueprint;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-253차: 설계도 HUD(사용자 9번, 11번).
 *
 * <p>49-258차(사용자: "한 줄마다 다 때려 박으니까 보기 너무 어려워, 층이랑 Y좌표 둘 다 뜨는 것도 투머치, 손에 든 블록도 이상하게 나와"):
 * 줄 목록 대신 작은 판으로 다시 짰다.
 * <ul>
 *   <li>머리: 설계도 이름 + 오른쪽에 층(전체 층을 보고 있으면 안 씀). Y 좌표는 뺐다.</li>
 *   <li>진행 막대 + "N개 남음"(층을 좁혀 보고 있으면 그 층 기준) + 오른쪽 %.</li>
 *   <li>손에 든 블록: 아이콘 + "N개 더"(보이는 층 기준 한 숫자. 예전 "12 / 12"는 층/전체라 둘 다 줄어 헷갈렸다).</li>
 *   <li>틀린 칸(있을 때만), 바라보는 홀로그램 블록: 아이콘 + 이름 + 놓는 방향 안내.</li>
 * </ul>
 * 설계도를 불러왔을 때만 보인다.
 */
public class BlueprintHudModule extends Module {

	private final PositionSetting position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 160)));
	private final ColorSetting textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	private final BooleanSetting showName = register(new BooleanSetting("show_name", "설계도 이름", "불러온 설계도 이름과 보고 있는 층입니다.", true));
	private final BooleanSetting showLayer = register(new BooleanSetting("show_layer", "층 기준 남은 블록", "층을 좁혀 보고 있으면 남은 블록을 그 층 기준으로 셉니다. 끄면 늘 전체 기준.", true));
	private final BooleanSetting showTotal = register(new BooleanSetting("show_total", "남은 블록", "더 놓아야 하는 블록 수입니다.", true));
	private final BooleanSetting showProgress = register(new BooleanSetting("show_progress", "진행 막대", "전체에서 맞게 놓은 비율 막대와 %입니다.", true));
	private final BooleanSetting showHeld = register(new BooleanSetting("show_held", "손에 든 블록", "손에 든 블록을 몇 개 더 놓아야 하는지입니다.", true));
	private final BooleanSetting showWrong = register(new BooleanSetting("show_wrong", "틀린 블록", "다른 블록이나 방향이 틀린 칸 수입니다(있을 때만).", true));
	private final BooleanSetting showLooked = register(new BooleanSetting("show_looked", "바라보는 블록", "바라보는 홀로그램 블록 이름과 놓는 방향 안내입니다.", true));

	private static final int W_MIN = 130;
	private static final int ROW = 11;
	private static final int ICON_ROW = 18;
	private static final int BAR_H = 4;
	private static final int GAP = 4;

	public BlueprintHudModule() {
		super("blueprint_hud", "설계도 HUD", ModuleCategory.HUD, "남은 블록 | 손에 든 블록 | 놓는 방향");
		enableHudStyle();
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	/** 그릴 내용(미리보기와 실제가 같은 그리기를 쓰게). */
	private static final class View {
		String name;
		String layer;          // "층 3" / "층 3~5" / null
		int left = -1;         // 남은 블록(-1 = 안 씀)
		String leftLabel = "";
		boolean checking;
		float progress = -1f;  // 0~1, -1 = 안 씀
		ItemStack heldIcon;
		String heldText;
		String wrongText;
		ItemStack lookIcon;
		String lookName;
		String lookTag;        // " (다른 블록)" 등, 색 코드 포함
		final List<String> hints = new ArrayList<>();
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		View v = isPreview() ? preview() : live();
		if (v == null) {
			return;
		}
		var tr = client.font;
		int textCol = textColor.getArgb();
		int sub = 0xFFA8B0B8;
		// ---- 크기 ----
		int w = W_MIN;
		int h = 0;
		if (v.name != null) {
			int nw = LunaCompat.getTextWidth(tr, v.name) + (v.layer == null ? 0 : 10 + LunaCompat.getTextWidth(tr, v.layer));
			w = Math.max(w, nw);
			h += ROW;
		}
		boolean leftRow = v.left >= 0 || v.progress >= 0;
		if (leftRow) {
			if (h > 0) {
				h += 2;
			}
			if (v.progress >= 0) {
				h += BAR_H + 3;
			}
			h += ROW;
		}
		if (v.heldText != null) {
			w = Math.max(w, 20 + LunaCompat.getTextWidth(tr, v.heldText));
			h += (h > 0 ? GAP : 0) + ICON_ROW;
		}
		if (v.wrongText != null) {
			w = Math.max(w, LunaCompat.getTextWidth(tr, v.wrongText));
			h += (h > 0 ? 2 : 0) + ROW;
		}
		if (v.lookName != null) {
			w = Math.max(w, 20 + LunaCompat.getTextWidth(tr, v.lookName + (v.lookTag == null ? "" : v.lookTag)));
			h += (h > 0 ? GAP : 0) + ICON_ROW;
			for (String hint : v.hints) {
				w = Math.max(w, 20 + LunaCompat.getTextWidth(tr, hint));
				h += ROW;
			}
		}
		if (h == 0) {
			return;
		}
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		drawHudBoxShadow(context, x - HUD_BOX_PAD, y - HUD_BOX_PAD, w + HUD_BOX_PAD * 2, h + HUD_BOX_PAD * 2);
		drawHudBox(context, x - HUD_BOX_PAD, y - HUD_BOX_PAD, w + HUD_BOX_PAD * 2, h + HUD_BOX_PAD * 2);
		// ---- 그리기 ----
		int cy = y;
		if (v.name != null) {
			LunaCompat.drawHudText(context, tr, v.name, x, cy + 1, 0xFF7FD4FF);
			if (v.layer != null) {
				LunaCompat.drawHudText(context, tr, v.layer, x + w - LunaCompat.getTextWidth(tr, v.layer), cy + 1, sub);
			}
			cy += ROW;
		}
		if (leftRow) {
			if (cy > y) {
				cy += 2;
			}
			if (v.progress >= 0) {
				int track = LunaCompat.hudCream ? 0x40000000 : 0x40FFFFFF;
				int fill = LunaCompat.hudCream ? 0xFF5B9B3C : 0xFF6FD08A;
				kr.lunaslight.mod.gui.LunaDraw.roundRect(context, x, cy, w, BAR_H, 2, track);
				int fw = Math.round(w * Math.max(0f, Math.min(1f, v.progress)));
				if (fw > 0) {
					kr.lunaslight.mod.gui.LunaDraw.roundRect(context, x, cy, Math.max(BAR_H, fw), BAR_H, 2, fill);
				}
				cy += BAR_H + 3;
			}
			if (v.left >= 0) {
				String s = v.leftLabel + v.left + "개 남음" + (v.checking ? " §7(확인 중)" : "");
				LunaCompat.drawHudText(context, tr, s, x, cy + 1, textCol);
			}
			if (v.progress >= 0) {
				String p = (int) Math.floor(v.progress * 100) + "%";
				LunaCompat.drawHudText(context, tr, p, x + w - LunaCompat.getTextWidth(tr, p), cy + 1, sub);
			}
			cy += ROW;
		}
		if (v.heldText != null) {
			if (cy > y) {
				cy += GAP;
			}
			drawIcon(context, v.heldIcon, x, cy + 1);
			LunaCompat.drawHudText(context, tr, v.heldText, x + 20, cy + 5, textCol);
			cy += ICON_ROW;
		}
		if (v.wrongText != null) {
			if (cy > y) {
				cy += 2;
			}
			LunaCompat.drawHudText(context, tr, v.wrongText, x, cy + 1, textCol);
			cy += ROW;
		}
		if (v.lookName != null) {
			if (cy > y) {
				cy += GAP;
			}
			drawIcon(context, v.lookIcon, x, cy + 1);
			LunaCompat.drawHudText(context, tr, v.lookName + (v.lookTag == null ? "" : v.lookTag), x + 20, cy + 5, textCol);
			cy += ICON_ROW;
			for (String hint : v.hints) {
				LunaCompat.drawHudText(context, tr, hint, x + 20, cy, sub);
				cy += ROW;
			}
		}
	}

	private static void drawIcon(GuiGraphicsExtractor ctx, ItemStack st, int x, int y) {
		if (st == null || st.isEmpty()) {
			return;
		}
		try {
			ctx.item(st, x, y);
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprintHud:icon", t);
		}
	}

	private static ItemStack stackOf(String itemId) {
		if (itemId == null || itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			Item it = LunaCompat.itemById(itemId);
			return it == null ? ItemStack.EMPTY : new ItemStack(it);
		} catch (Throwable t) {
			return ItemStack.EMPTY;
		}
	}

	private View live() {
		BlueprintModule bm = BlueprintModule.instance;
		if (bm == null || bm.bp == null || !bm.isEnabled() || client.player == null || LunaCompat.screenOf(client) != null) {
			return null;
		}
		Blueprint bp = bm.bp;
		View v = new View();
		boolean partial = bm.layerMin > 1 || bm.layerMax < bp.h;
		if (showName.get()) {
			v.name = bp.name;
			v.layer = !partial ? null : bm.layerMin == bm.layerMax ? "층 " + bm.layerMin : "층 " + bm.layerMin + "~" + bm.layerMax;
		}
		if (showTotal.get()) {
			boolean byLayer = partial && showLayer.get();
			v.left = byLayer ? bm.layerLeft : bm.totalLeft;
			v.leftLabel = byLayer ? "이 층 " : "";
			v.checking = !bm.scanned();
		}
		if (showProgress.get() && bp.solid > 0) {
			v.progress = Math.max(0f, (bp.solid - bm.totalLeft) / (float) bp.solid);
		}
		if (showHeld.get()) {
			ItemStack held = client.player.getMainHandItem();
			if (held != null && !held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.BlockItem) {
				int need = bm.heldLeft()[partial ? 0 : 1];
				Object id = LunaCompat.getItemId(held.getItem());
				boolean inBp = false;
				for (Blueprint.Entry e : bp.palette) {
					if (id != null && id.toString().equals(e.item)) {
						inBp = true;
						break;
					}
				}
				if (inBp) {
					v.heldIcon = held;
					v.heldText = need > 0 ? need + "개 더 놓기" : "§a다 놓았어요";
				}
			}
		}
		if (showWrong.get() && (bm.wrongCount > 0 || bm.rotatedCount > 0)) {
			StringBuilder sb = new StringBuilder();
			if (bm.wrongCount > 0) {
				sb.append("§c다른 블록 ").append(bm.wrongCount);
			}
			if (bm.rotatedCount > 0) {
				sb.append(sb.length() > 0 ? "   " : "").append("§6방향 틀림 ").append(bm.rotatedCount);
			}
			v.wrongText = sb.toString();
		}
		if (showLooked.get()) {
			int ci = bm.lookedCell();
			if (ci >= 0) {
				Blueprint.Entry e = bp.entry(bp.cells[ci]);
				int st = bm.statusOf(ci);
				v.lookIcon = e == null ? ItemStack.EMPTY : stackOf(e.item);
				v.lookName = e == null ? "?" : e.name;
				v.lookTag = st == Blueprint.WRONG ? " §c(다른 블록)" : st == Blueprint.ROTATED ? " §6(방향 틀림)" : null;
				v.hints.addAll(Blueprint.placeHint(e, bm.playerFacing()));
			}
		}
		return v;
	}

	private View preview() {
		View v = new View();
		if (showName.get()) {
			v.name = "집 1";
			v.layer = "층 3~5";
		}
		if (showTotal.get()) {
			v.left = 42;
			v.leftLabel = showLayer.get() ? "이 층 " : "";
		}
		if (showProgress.get()) {
			v.progress = 0.64f;
		}
		if (showHeld.get()) {
			v.heldIcon = stackOf("minecraft:oak_planks");
			v.heldText = "12개 더 놓기";
		}
		if (showWrong.get()) {
			v.wrongText = "§c다른 블록 2   §6방향 틀림 1";
		}
		if (showLooked.get()) {
			v.lookIcon = stackOf("minecraft:purple_glazed_terracotta");
			v.lookName = "자주색 유약 테라코타";
			v.hints.add("방향 북  |  남쪽을 보고 놓기");
		}
		return v;
	}
}
