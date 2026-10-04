package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 49-23차: "핫바를 제외한 인벤토리에 있는 아이템을 보여주는 HUD" - 인벤토리 위 3줄(슬롯 9~35)을 9×3 격자로.
 * 칸 배경/테두리 색 조절. 화면(인벤토리·상자)이 열려 있을 땐 숨김. 49-41차: 빈 칸도 칸으로, 세 줄 항상.
 * 미리보기는 예시 아이템으로.
 */
public class InventoryHudModule extends Module {

	private static final int CELL = 18;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 88)));
	// 49-53차(4-38): 핫바 바로 위로. 아래 48px은 핫바(22) + 체력·갑옷·배고픔 줄(~45)을 피한 값이다.

	public enum Style {
		CLEAN("깔끔"),
		BEVEL("입체"),
		MINIMAL("투명");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	// 49-24차: "너무 안 예뻐 - 입체감이나 깔끔함" → 세 가지 스타일. 깔끔(기본) = 둥근 어두운 판 + 옅은 격자선,
	// 입체 = 마크 인벤토리 베벨 슬롯, 투명 = 아이템만.
	private final kr.lunaslight.mod.module.setting.EnumSetting<Style> style = register(new kr.lunaslight.mod.module.setting.EnumSetting<>(
			"style", "모양", "칸 모양입니다.", Style.CLEAN, Style.class).style());

	// 49-195차(사용자: "인벤토리도 꾸밀 수 있게 하고 이름 인벤토리 미리보기로"): 자체 [배경]/[배경 색] 대신 다른 HUD와 같은
	// [배경] [배경 색] [윤곽선] [배경 모양](직접 그림 포함) 설정을 쓴다(enableHudStyle). 기본은 예전과 같은 옅은 둥근 판.

	private final BooleanSetting outline = register(new BooleanSetting(
			"outline", "격자선", "칸 사이에 옅은 선을 그립니다.", true).style());

	private final ColorSetting outlineColor = register(new ColorSetting(
			"outline_color", "격자선 색", "칸 사이 선의 색입니다.", 0x14FFFFFF));

	// 49-41차(사용자): [아이템 칸만]·[빈 줄 숨김]·[화면 열면 숨김] 설정 제거 - 빈 칸도 항상 칸으로 보이고(투명 X),
	// 세 줄 전부, 화면이 열리면 당연히 숨긴다.

	public InventoryHudModule() {
		super("inventory_hud", "인벤토리 미리보기", ModuleCategory.HUD, "인벤토리 3줄을 화면에 상시 표시");
		outline.withColor(outlineColor);
		// 49-27차의 기본 알파(0x3C, 화면을 가리지 않게)를 그대로
		enableHudStyle(0x3C0B0D11, false, 0x24FFFFFF, kr.lunaslight.mod.module.Module.HudShape.ROUND);
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		ItemStack[] stacks = new ItemStack[27];
		if (isPreview()) {
			for (int i = 0; i < 27; i++) {
				stacks[i] = ItemStack.EMPTY;
			}
			stacks[0] = new ItemStack(Items.COBBLESTONE, 64);
			stacks[1] = new ItemStack(Items.OAK_LOG, 32);
			stacks[2] = new ItemStack(Items.TORCH, 16);
			stacks[4] = new ItemStack(Items.IRON_INGOT, 9);
			stacks[9] = new ItemStack(Items.COOKED_BEEF, 12);
			stacks[10] = new ItemStack(Items.BOW);
			stacks[13] = new ItemStack(Items.ARROW, 48);
			stacks[18] = new ItemStack(Items.WATER_BUCKET);
			stacks[26] = new ItemStack(Items.ENDER_PEARL, 7);
		} else {
			if (client.player == null || LunaCompat.isHudHidden(client)) {
				return;
			}
			if (client.currentScreen != null) {
				return;
			}
			PlayerInventory inv = LunaCompat.getPlayerInventory(client.player);
			for (int i = 0; i < 27; i++) {
				ItemStack s = LunaCompat.invGetStack(inv, 9 + i); // 49-36차: 1.15.2 getInvStack
				stacks[i] = s == null ? ItemStack.EMPTY : s;
			}
		}

		int rows = 3;
		Style st = style.get();
		int pad = st == Style.BEVEL ? 4 : (st == Style.CLEAN ? 3 : 0);
		int w = 9 * CELL + pad * 2;
		int h = rows * CELL + pad * 2;
		int x;
		int y;
		if (isPreviewBoxed()) {
			x = previewCenterX() - w / 2;
			y = previewCenterY() - h / 2;
		} else {
			x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
			y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), h);
		}

		if (st == Style.BEVEL) {
			kr.lunaslight.mod.gui.LunaDraw.mcPanel(context, x, y, w, h);
		} else {
			// 49-195차: 깔끔/투명 모두 이 기능의 [배경] 설정대로(모양, 색, 윤곽선, 직접 그린 배경)
			drawHudBox(context, x, y, w, h);
		}

		int ry = y + pad;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				int cx = x + pad + c * CELL;
				if (st == Style.BEVEL) {
					kr.lunaslight.mod.gui.LunaDraw.mcSlot(context, cx, ry);
				} else if (st == Style.CLEAN && outline.get()) {
					int oc = outlineColor.getArgb();
					// 칸 사이 선만(바깥 테두리는 판이 대신) - 격자 느낌
					if (c > 0) {
						context.fill(cx, ry + 2, cx + 1, ry + CELL - 2, oc);
					}
					if (ry > y + pad) {
						context.fill(cx + 2, ry, cx + CELL - 2, ry + 1, oc);
					}
				}
				ItemStack s = stacks[r * 9 + c];
				if (!s.isEmpty()) {
					context.drawItem(s, cx + 1, ry + 1);
					LunaCompat.drawItemOverlay(context, client.textRenderer, s, cx + 1, ry + 1);
				}
			}
			ry += CELL;
		}
	}
}
