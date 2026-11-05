package kr.lunaslight.mod.module.impl.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-565차(사용자: "아이템 펼치기 기능 만들어서 아이템을 쉬프트 휠클릭하면 아이템이 1개씩 남은 인벤에 퍼지게"): <b>아이템 펼치기</b>.
 *
 * <p>상자나 인벤토리 화면에서 아이템 칸을 Shift + 휠클릭하면, 그 묶음을 들어 원래 칸에 1개를 두고 같은 칸 묶음(내 인벤토리면 내 인벤토리, 상자면 그 상자)의
 * 빈 칸마다 1개씩 놓는다(우클릭 놓기와 같은 칸 클릭). 빈 칸이 모자라 남으면 원래 칸에 도로 둔다. 내 인벤토리는 갑옷, 왼손 칸은 빼고
 * 가방 → 핫바 순서. 손에 아이템을 들고 있거나 크리에이티브 창이면 안 한다. 바닐라 휠클릭(크리에이티브 복사)은 이때만 먹는다.
 */
public class ItemSpreadModule extends Module {

	public ItemSpreadModule() {
		super("item_spread", "아이템 펼치기", ModuleCategory.INVENTORY, "Shift + 휠클릭으로 빈 칸마다 1개씩");
		newFeature("49-565");
		try {
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof AbstractContainerScreen<?>) {
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
						.register((s, e) -> !(e.button() == 2 && onMiddle(s)));
				}
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("itemSpread:init", t);
		}
	}

	/** 휠클릭: 펼쳤으면 true(바닐라 클릭은 막음). */
	private boolean onMiddle(Object screen) {
		try {
			if (!isEnabled() || client.player == null || client.gameMode == null || !shiftDown()
					|| !(screen instanceof AbstractContainerScreen<?> hs) || LunaCompat.isCreativeInventory(screen)) {
				return false;
			}
			ItemStack cursor = LunaCompat.cursorStack(client.player);
			if (cursor != null && !cursor.isEmpty()) {
				return false;
			}
			Slot src = hoveredSlot(hs);
			if (src == null || !src.hasItem() || src.getItem().getCount() < 2 || !src.mayPickup(client.player)) {
				return false;
			}
			return spread(hs.getMenu(), src);
		} catch (Throwable t) {
			LunaCompat.warnOnce("itemSpread:click", t);
			return false;
		}
	}

	private boolean spread(AbstractContainerMenu menu, Slot src) {
		ItemStack stack = src.getItem().copy();
		List<Slot> empty = new ArrayList<>();
		for (Slot s : menu.slots) {
			if (s == src || s.container != src.container || s.hasItem() || !s.mayPlace(stack)) {
				continue;
			}
			if (s.container instanceof Inventory && s.getContainerSlot() >= 36) {
				continue;   // 갑옷, 왼손
			}
			empty.add(s);
		}
		if (empty.isEmpty()) {
			return false;
		}
		int left = stack.getCount();
		click(menu, src.index, 0);   // 묶음 들기
		click(menu, src.index, 1);   // 원래 칸에 1개
		left--;
		for (Slot s : empty) {
			if (left <= 0) {
				break;
			}
			click(menu, s.index, 1);
			left--;
		}
		if (left > 0) {
			click(menu, src.index, 0);   // 남은 건 원래 칸에
		}
		LunaCompat.playUiSound(client, "UI_BUTTON_CLICK", 1.4f);
		return true;
	}

	private void click(AbstractContainerMenu menu, int slotId, int button) {
		client.gameMode.handleContainerInput(menu.containerId, slotId, button, ContainerInput.PICKUP, client.player);
	}

	private boolean shiftDown() {
		return LunaCompat.isKeyPressed(client, InputConstants.KEY_LSHIFT) || LunaCompat.isKeyPressed(client, InputConstants.KEY_RSHIFT);
	}

	private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
		try {
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "focusedSlot");
			f.setAccessible(true);
			Object v = f.get(screen);
			return v instanceof Slot s ? s : null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("itemSpread:slot", t);
			return null;
		}
	}

	// 49-570차(사용자: "신규 기능은 항상 겜 들어갔을 때 추가할 거냐고 물어보라고, 미리보기랑 함께"): 새 기능 창 미리보기 -
	// 왼쪽 칸 하나에 든 5개가 Shift + 휠클릭으로 오른쪽 다섯 칸에 1개씩 차례로 퍼지는 그림(2.4초마다 다시).
	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(net.minecraft.client.gui.GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		var font = client.font;
		ItemStack wheat = new ItemStack(net.minecraft.world.item.Items.WHEAT);
		long t = System.currentTimeMillis() % 2400L;
		int shown = (int) Math.min(5, Math.max(0, (t - 500) / 180));
		String label = "Shift + 휠클릭";
		int rowW = 5 * 18 + 4 * 2;
		int total = rowW + 28 + rowW;
		int sx = x + (w - total) / 2, sy = y + h / 2 - 2;
		kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, label, x + (w - kr.lunaslight.mod.gui.LunaDraw.width(font, label)) / 2, y + 12, 0xFFFFE08A);
		for (int k = 0; k < 2; k++) {
			int rx = sx + k * (rowW + 28);
			for (int i = 0; i < 5; i++) {
				int cx = rx + i * 20;
				kr.lunaslight.mod.gui.LunaDraw.roundRect(ctx, cx, sy, 18, 18, 2, 0x40FFFFFF);
				boolean has = k == 0 ? i == 0 : i < shown;
				if (has) {
					ctx.item(wheat, cx + 1, sy + 1);
					String c = k == 0 ? "5" : "1";
					kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, c, cx + 18 - kr.lunaslight.mod.gui.LunaDraw.width(font, c), sy + 10, 0xFFFFFFFF);
				}
			}
		}
		String arrow = "→";
		kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, arrow, sx + rowW + 14 - kr.lunaslight.mod.gui.LunaDraw.width(font, arrow) / 2, sy + 5, 0xFFFFFFFF);
		String sub = "빈 칸마다 1개씩, 남으면 원래 칸에";
		kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, sub, x + (w - kr.lunaslight.mod.gui.LunaDraw.width(font, sub)) / 2, sy + 26, 0xFFB8C2CF);
	}
}
