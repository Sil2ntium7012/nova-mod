package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * 49-253차: 설계도 - 바라보는 홀로그램 블록을 인벤토리에서 손으로 가져오기(사용자 10번).
 * 핫바에 있으면 그 칸을 고르고, 가방(9~35칸)에 있으면 지금 든 칸과 맞바꾼다(handleContainerInput SWAP).
 */
public final class BlueprintPick {
	private BlueprintPick() {
	}

	/** 0 = 이미 들고 있음, 1 = 핫바에서 고름, 2 = 가방에서 가져옴, -1 = 없음. */
	public static int pick(Minecraft client, String itemId) {
		if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
			return -1;
		}
		Inventory inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return -1;
		}
		int selected = LunaCompat.selectedSlot(client.player);
		if (selected >= 0 && matches(LunaCompat.invGetStack(inv, selected), itemId)) {
			return 0;
		}
		for (int i = 0; i < 9; i++) {
			if (matches(LunaCompat.invGetStack(inv, i), itemId)) {
				setSelected(inv, i);
				return 1;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (matches(LunaCompat.invGetStack(inv, i), itemId)) {
				if (selected < 0 || client.gameMode == null) {
					return -1;
				}
				kr.lunaslight.mod.module.impl.inventory.AutoRefillModule.resync();   // 49-305차
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, i, selected,
						ContainerInput.SWAP, client.player);
				return 2;
			}
		}
		return -1;
	}

	/** 인벤토리에 그 아이템이 모두 몇 개 있나. */
	public static int count(Minecraft client, String itemId) {
		if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
			return 0;
		}
		Inventory inv = LunaCompat.getPlayerInventory(client.player);
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack st = LunaCompat.invGetStack(inv, i);
			if (matches(st, itemId)) {
				n += st.getCount();
			}
		}
		return n;
	}

	static boolean matches(ItemStack st, String itemId) {
		if (st == null || st.isEmpty()) {
			return false;
		}
		Object id = LunaCompat.getItemId(st.getItem());
		return id != null && itemId.equals(id.toString());
	}

	private static void setSelected(Inventory inv, int slot) {
		inv.setSelectedSlot(slot);
	}
}
