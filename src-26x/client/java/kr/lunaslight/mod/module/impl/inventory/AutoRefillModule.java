package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ⚠️ 컴파일 확인 필요 / 주의:
 * 1) 아이템 소진을 특정 이벤트(PlayerBlockBreakEvents.AFTER 등)로 정확히 잡기보다,
 *    매 틱마다 핫바(0~8번 슬롯)를 스캔해 count==0(빈 슬롯이 된 경우)을 감지하는 폴링 방식으로
 *    구현했습니다. 정확히 "직전까지 아이템이 있었던 슬롯"만 갱신 대상으로 삼기 위해 이전 틱의
 *    핫바 아이템 스냅샷을 저장해 비교합니다.
 * 2) 실제 슬롯 교체는 client.interactionManager.clickSlot(handler.syncId, fromSlot, 0,
 *    SlotActionType.SWAP, player) 패턴을 사용합니다. 이 방식은 Meteor/Wurst 등 유틸 모드가
 *    흔히 쓰는 방식이지만, **서버가 클라이언트의 임의 클릭 패킷을 검증(안티치트)하는 경우
 *    거부되거나 밴 사유가 될 수 있습니다. 싱글플레이 또는 치트를 허용하는 친구 서버에서만
 *    사용하는 것을 전제로 구현했습니다.**
 * 3) handler.syncId / PlayerScreenHandler 관련 필드명이 실제 배포판과 다를 가능성이 있어
 *    client.player.playerScreenHandler를 그대로 사용했습니다(표준 필드명으로 알려져 있음).
 */
public class AutoRefillModule extends Module {

	private final StringSetting excludedItems;
	private final BooleanSetting onlyWhenEmpty;
	/** 49-304차(사용자: "자동 채우기 핫바도 포함할 건지 고르게, 기본 비활성화"): 인벤토리에 없으면 다른 핫바 칸에서도 가져올지. */
	private final BooleanSetting fromHotbar;

	private final ItemStack[] prevHotbar = new ItemStack[9];

	/**
	 * 49-305차(사용자: "핫바 교체할 때 원래 위치로 돌아오는 오류"): 핫바 줄 바꾸기 같은 기능이 핫바를 통째로 바꾸면, 빈 칸이 된 핫바를
	 * "다 썼다"로 보고 방금 옮긴 아이템을 도로 끌어왔다. 그런 기능이 칸을 옮기기 전에 이걸 부르면 다음 틱엔 채우지 않고 새 배치만 기억한다.
	 */
	private static volatile int resyncTicks;

	public static void resync() {
		resyncTicks = 3;
	}

	public AutoRefillModule() {
		// 49-22차: "싱글 전용 아니라 멀티도 돼야" - 원래부터 싱글 제한 코드는 없었고(바닐라 서버도 SWAP 클릭을
		// 정상 처리) 설명 문구만 그렇게 적혀 있었음. 문구 수정 + 아래 멀티 안정화(핸들러 syncId를 현재 것으로).
		super("auto_refill", "자동 채우기", ModuleCategory.INVENTORY, "핫바가 비면 같은 아이템으로 채움");
		excludedItems = register(new StringSetting("excluded_items", "제외 아이템",
			"채우지 않을 아이템 id입니다. 예: minecraft:torch", "").list());
		onlyWhenEmpty = register(new BooleanSetting("only_when_empty", "빈 칸 한정",
			"칸이 완전히 비었을 때만 채웁니다. 끄면 수량이 적어도 채웁니다.", true));
		fromHotbar = register(new BooleanSetting("from_hotbar", "핫바에서도 가져오기",
			"인벤토리에 같은 아이템이 없으면 다른 핫바 칸에 있는 것도 끌어옵니다. 끄면 인벤토리(핫바 제외)에서만 가져옵니다.", false));

		for (int i = 0; i < prevHotbar.length; i++) {
			prevHotbar[i] = ItemStack.EMPTY;
		}
	}

	private Set<Identifier> parseExcluded() {
		Set<Identifier> result = new HashSet<>();
		String raw = excludedItems.get();
		if (raw == null || raw.isBlank()) {
			return result;
		}
		Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.forEach(s -> {
				Identifier id = Identifier.tryParse(s);
				if (id != null) {
					result.add(id);
				}
			});
		return result;
	}

	@Override
	public void onTick() {
		if (client.player == null || client.gameMode == null) {
			return;
		}

		// 49-8차: 인벤토리/상자 화면이 열려 있는 동안엔 절대 개입하지 않음. 예전엔 쉬프트클릭으로
		// 핫바에서 아이템을 빼는 순간 "소진됐다"고 판단해 즉시 되돌려놔서, 사용자 입장에선
		// "쉬프트클릭이 막히는" 것처럼 보였음. 화면이 열려 있으면 스냅샷만 갱신하고 종료 -
		// 사용자가 정리한 배치를 그대로 새 기준으로 받아들임.
		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			for (int i = 0; i < 9; i++) {
				prevHotbar[i] = LunaCompat.getPlayerInventory(client.player).getItem(i).copy();
			}
			return;
		}

		if (resyncTicks > 0) {
			resyncTicks--;
			for (int i = 0; i < 9; i++) {
				prevHotbar[i] = LunaCompat.getPlayerInventory(client.player).getItem(i).copy();
			}
			return;
		}

		Set<Identifier> excluded = parseExcluded();
		// 49-304차: 다른 핫바 칸에서 끌어왔으면 그 칸이 빈 것을 "다 썼다"로 보지 않게(서로 주고받기 반복 방지)
		boolean[] drained = new boolean[9];

		for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
			ItemStack current = LunaCompat.getPlayerInventory(client.player).getItem(hotbarSlot);
			ItemStack previous = prevHotbar[hotbarSlot];

			if (drained[hotbarSlot]) {
				continue;   // 이번 틱에 다른 칸을 채우려고 비운 칸(끝에서 빈 칸으로 기억)
			}
			// 49-305차: 다 쓴 것 = 조금씩 줄다가 빈 것(직전 틱에 2개 이하). 여러 개가 한 번에 사라졌으면 옮기거나 버린 것이라 채우지 않는다
			boolean usedUp = current.isEmpty() ? previous.getCount() <= 2
				: current.getItem() == previous.getItem() && previous.getCount() - current.getCount() <= 2;
			boolean depleted = onlyWhenEmpty.get()
				? current.isEmpty() && !previous.isEmpty() && usedUp
				: (current.isEmpty() || current.getCount() <= 1) && !previous.isEmpty() && usedUp;

			if (depleted) {
				Item neededItem = previous.getItem();
				Identifier neededId = LunaCompat.getItemId(neededItem);
				if (!excluded.contains(neededId)) {
					int foundSlot = findReplacement(neededItem, hotbarSlot);
					if (foundSlot >= 0) {
						// SlotActionType.SWAP: button 파라미터가 대상 핫바 인덱스(0~8)로 해석되어
						// foundSlot의 아이템과 hotbarSlot 핫바 칸을 맞바꾸는 표준 패턴(멀티에서도 서버가
						// 플레이어 인벤토리 핸들러(syncId 0)로 처리). 49-22차: 현재 핸들러가 플레이어
						// 인벤토리일 때만(다른 컨테이너가 열려 있으면 서버가 거부/꼬임).
						if (client.player.containerMenu == client.player.inventoryMenu) {
							client.gameMode.handleContainerInput(
								client.player.inventoryMenu.containerId,
								foundSlot, hotbarSlot, ContainerInput.SWAP, client.player);
							if (foundSlot >= 36) {
								drained[foundSlot - 36] = true;
							}
						}
					}
				}
			}

			prevHotbar[hotbarSlot] = current.copy();
		}
		for (int i = 0; i < 9; i++) {
			if (drained[i]) {
				prevHotbar[i] = ItemStack.EMPTY;
			}
		}
	}

	/**
	 * 인벤토리(핫바 제외, 대략 9~35번 슬롯)에서 같은 아이템을 찾음. 돌려주는 값 = 플레이어 인벤토리 화면의 칸 번호
	 * (9~35는 그대로, 핫바 i는 36 + i). 49-304차: [핫바에서도 가져오기]가 켜져 있으면 인벤토리에 없을 때 다른 핫바 칸도 본다.
	 */
	private int findReplacement(Item item, int excludeHotbarSlot) {
		// 49-8차: 36 이상은 갑옷/오프핸드 인벤토리 인덱스라 스캔 제외(예전엔 갑옷까지 끌어올 수 있었음)
		int size = Math.min(36, LunaCompat.getPlayerInventory(client.player).getContainerSize());
		for (int i = 9; i < size; i++) {
			ItemStack stack = LunaCompat.getPlayerInventory(client.player).getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				return i;
			}
		}
		if (fromHotbar.get()) {
			for (int i = 0; i < 9; i++) {
				if (i == excludeHotbarSlot) {
					continue;
				}
				ItemStack stack = LunaCompat.getPlayerInventory(client.player).getItem(i);
				if (!stack.isEmpty() && stack.getItem() == item) {
					return 36 + i;
				}
			}
		}
		return -1;
	}
}
