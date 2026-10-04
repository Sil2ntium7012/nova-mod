package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * 49-253차: 설계도 - 바라보는 홀로그램 블록을 인벤토리에서 손으로 가져오기(사용자 10번).
 * 핫바에 있으면 그 칸을 고르고, 가방(9~35칸)에 있으면 지금 든 칸과 맞바꾼다(clickSlot SWAP - 거리 재기와 같은 클릭 계열이라
 * 1.14.4/1.15.2는 컴파일에서 빼고 BlueprintModule이 리플렉션으로 부른다).
 */
public final class BlueprintPick {
	private BlueprintPick() {
	}

	/** 0 = 이미 들고 있음, 1 = 핫바에서 고름, 2 = 가방에서 가져옴, -1 = 없음. */
	public static int pick(MinecraftClient client, String itemId) {
		if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
			return -1;
		}
		PlayerInventory inv = LunaCompat.getPlayerInventory(client.player);
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
				if (selected < 0 || client.interactionManager == null) {
					return -1;
				}
				client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, i, selected,
						SlotActionType.SWAP, client.player);
				return 2;
			}
		}
		return -1;
	}

	/** 인벤토리에 그 아이템이 모두 몇 개 있나. */
	public static int count(MinecraftClient client, String itemId) {
		if (client == null || client.player == null || itemId == null || itemId.isEmpty()) {
			return 0;
		}
		PlayerInventory inv = LunaCompat.getPlayerInventory(client.player);
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

	private static void setSelected(PlayerInventory inv, int slot) {
		try {
			java.lang.reflect.Method m = LunaCompat.findMethod(inv.getClass(), "setSelectedSlot", int.class);
			if (m != null) {
				m.setAccessible(true);
				m.invoke(inv, slot);
				return;
			}
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(PlayerInventory.class, "selectedSlot");
			f.setAccessible(true);
			f.setInt(inv, slot);
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:select", t);
		}
	}
}
