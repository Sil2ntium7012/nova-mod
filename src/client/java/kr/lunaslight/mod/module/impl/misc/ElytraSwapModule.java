package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

/**
 * 겉날개 교체 - 키 하나로 지금 입은 <b>흉갑</b>과 인벤토리의 <b>겉날개</b>를 맞바꾼다(그 반대도).
 *
 * <h3>49-85차: 통째로 다시 썼다(사용자: "겉날개 스왑 안돼")</h3>
 * 예전 판은 두 군데가 어긋나 <b>한 번도 동작하지 않았다</b>:
 * <ol>
 *   <li>지금 입은 흉갑을 {@code PlayerInventory.getStack(6)}으로 읽었는데 - 그 인덱스는 <b>가슴 갑옷이 아니라
 *       핫바 7번 칸</b>이다(PlayerInventory는 0~8 핫바, 9~35 창고, 갑옷은 별도 목록). 늘 엉뚱한 칸을 봤다.</li>
 *   <li>바꿀 때 {@code clickSlot(.., 대상, 6, SWAP, ..)}을 썼는데 - {@code SWAP}의 button은 <b>핫바 인덱스(0~8)</b>라
 *       "핫바 7번과 맞바꿔라"는 뜻이 된다. 갑옷 칸으로는 SWAP이 안 간다.</li>
 * </ol>
 * 이제는 <b>플레이어 인벤토리 핸들러의 슬롯 번호</b>로만 다룬다(5~8=갑옷[머리·가슴·다리·발], 9~35=창고, 36~44=핫바).
 * 가슴 갑옷 = 슬롯 <b>6</b>. 갑옷 칸은 SWAP이 안 되므로 <b>집기(PICKUP) 3번</b>으로 맞바꾼다(마우스로 하는 것과 같은 순서):
 * ① 인벤토리의 대상(겉날개/흉갑)을 집고 → ② 가슴 칸에 놓으면서 원래 있던 걸 집어 들고 → ③ 그 빈 칸에 되돌려 놓는다.
 *
 * <p>안티치트(clickSlot 자동 클릭)에 걸릴 수 있어 하이픽셀에서는 스스로 꺼진다. 1.15.2는 컨테이너 API가 통째로 달라
 * 이 모듈이 빌드에서 빠진다(no_container_modules). 다른 창(상자 등)이 열려 있으면 건드리지 않는다.
 */
public class ElytraSwapModule extends Module {

	/** 플레이어 인벤토리 핸들러에서 가슴 갑옷 슬롯 번호(5=머리,6=가슴,7=다리,8=발). 겉날개도 이 칸에 장착된다. */
	private static final int CHEST_SLOT = 6;

	private final KeybindSetting swapKey;
	private boolean wasPressed = false;

	public ElytraSwapModule() {
		super("elytra_swap", "겉날개 교체", ModuleCategory.INVENTORY, "키 하나로 흉갑과 겉날개 교체");
		swapKey = register(new KeybindSetting("swap_key", "교체 키", "겉날개와 갑옷을 서로 바꾸는 키입니다.", GLFW.GLFW_KEY_UNKNOWN));
	}

	@Override
	protected void onEnable() {
		if (isHypixel()) {
			setEnabledSilently(false);   // 안티치트 우회 논란 방지 - 하이픽셀에선 강제로 꺼짐
		}
	}

	@Override
	public void onTick() {
		if (isHypixel()) {
			setEnabledSilently(false);
			return;
		}
		if (client.player == null || client.interactionManager == null || !swapKey.isBound()) {
			wasPressed = false;
			return;
		}
		boolean pressedNow = swapKey.isDown(client);
		boolean justPressed = pressedNow && !wasPressed;
		wasPressed = pressedNow;
		if (justPressed && client.currentScreen == null) {
			performSwap();
		}
	}

	private void performSwap() {
		// 자기 인벤토리 핸들러가 열려 있을 때만(다른 상자 창이 열려 있으면 서버가 거부/꼬임).
		if (client.player.currentScreenHandler != client.player.playerScreenHandler) {
			return;
		}
		ItemStack chest = slotStack(CHEST_SLOT);
		boolean wearingElytra = chest != null && !chest.isEmpty() && chest.getItem() == Items.ELYTRA;

		// 겉날개를 입고 있으면 흉갑을, 아니면 겉날개를 찾는다. 가슴이 비어 있으면 겉날개 우선, 없으면 흉갑.
		int target = wearingElytra ? findInInventory(false) : findInInventory(true);
		if (target < 0 && (chest == null || chest.isEmpty())) {
			target = findInInventory(false);
		}
		if (target < 0) {
			return;   // 바꿀 게 없다 - 조용히
		}
		int syncId = client.player.playerScreenHandler.syncId;
		click(syncId, target);        // ① 대상 집기
		click(syncId, CHEST_SLOT);    // ② 가슴 칸에 놓으며 원래 것 집기
		click(syncId, target);        // ③ 원래 것을 빈 칸에 되돌리기
	}

	/** 창고(9~35) + 핫바(36~44)에서 겉날개(elytra=true) 또는 흉갑(false)이 든 첫 슬롯 번호. 없으면 -1. */
	private int findInInventory(boolean elytra) {
		for (int i = 9; i <= 44; i++) {
			ItemStack s = slotStack(i);
			if (s == null || s.isEmpty()) {
				continue;
			}
			boolean isElytra = s.getItem() == Items.ELYTRA;
			if (elytra ? isElytra : (!isElytra && isChestplate(s))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isChestplate(ItemStack s) {
		net.minecraft.util.Identifier id = LunaCompat.getItemId(s.getItem());
		return id != null && id.getPath().contains("chestplate");
	}

	/** 플레이어 인벤토리 핸들러의 슬롯 번호가 담은 아이템. 슬롯 범위를 벗어나거나 실패하면 빈 스택. */
	private ItemStack slotStack(int index) {
		try {
			return client.player.playerScreenHandler.getSlot(index).getStack();
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	private void click(int syncId, int slot) {
		client.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, client.player);
	}

	/** 현재 접속 중인 서버 주소가 하이픽셀인지 문자열로 판단. */
	private boolean isHypixel() {
		ServerInfo entry = client.getCurrentServerEntry();
		if (entry == null || entry.address == null) {
			return false;
		}
		return entry.address.toLowerCase().contains("hypixel.net");
	}
}
