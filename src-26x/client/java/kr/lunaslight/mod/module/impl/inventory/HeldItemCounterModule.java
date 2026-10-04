package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;

import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * <b>손 아이템 개수</b> - 손에 든 아이템이 인벤토리에 모두 몇 개 있는지 아이콘 옆에 띄운다.
 *
 * <p>49-157차에 [소모품 개수](key_item_count)의 한 줄로 합쳤다가, 49-179차(사용자: "전에 손에 들고 있는 아이템 수
 * 알려주는 기능 어디갔어 - 왜 아이템 개수로 되돌려놔")에 따로 된 기능으로 되살렸다. 설정 id(held_item_counter)는
 * 예전 그대로라 옛 설정 파일의 위치와 켜짐이 그대로 이어진다.
 */
public class HeldItemCounterModule extends Module {

	private static final int ICON = 16;

	private final PositionSetting position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, -140, 3)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "개수 글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	// 49-195차(사용자: "이름 아이템 개수로, 전체 합산은 기능 삭제하고 기본 설정으로"): [전체 합산] 설정을 빼고 늘 인벤토리 전체를 센다.

	public HeldItemCounterModule() {
		super("held_item_counter", "아이템 개수", ModuleCategory.HUD, "손에 든 아이템이 모두 몇 개 있는지");
		enableHudStyle();
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		ItemStack hand = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
		int total;
		if (isPreview() && (hand == null || hand.isEmpty())) {
			// 미리보기에서 손이 비었으면 예시(조약돌)
			net.minecraft.world.item.Item cobble = LunaCompat.itemById("minecraft:cobblestone");
			hand = cobble == null ? ItemStack.EMPTY : new ItemStack(cobble, 64);
			total = 192;
		} else {
			if (hand == null || hand.isEmpty()) {
				return;
			}
			total = countAll(hand);
		}
		if (hand.isEmpty()) {
			return;
		}
		String n = String.valueOf(total);
		int w = ICON + 3 + LunaCompat.getTextWidth(client.font, n);
		int x;
		int y;
		if (isPreviewBoxed()) {
			// 49-195차(사용자: "미리보기 안 보이고"): 설정 미리보기 상자 안 가운데에(예전엔 화면 실제 자리 - 상자 밖이라 안 보였다)
			x = previewCenterX() - w / 2;
			y = previewCenterY() - ICON / 2;
		} else {
			x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
			y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), ICON);
		}
		drawHudPanel(context, x, y, w, ICON);
		context.item(hand, x, y);
		LunaCompat.drawHudText(context, client.font, n, x + ICON + 3, y + 4, textColor.getArgb());
	}

	private int countAll(ItemStack hand) {
		int total = 0;
		try {
			Object inv = LunaCompat.getPlayerInventory(client.player);
			int size = LunaCompat.invSize(inv);
			for (int i = 0; i < size; i++) {
				ItemStack s = LunaCompat.invGetStack(inv, i);
				if (s != null && !s.isEmpty() && s.getItem() == hand.getItem()) {
					total += s.getCount();
				}
			}
		} catch (Throwable ignored) {
			return hand.getCount();
		}
		return Math.max(total, hand.getCount());
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
