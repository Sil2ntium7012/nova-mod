package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-111차(사용자: "인벤토리 정리 - 단축키식, 이름/갯수/id 순, 오름/내림차순, 기본 키 마우스 휠클릭, 상자·내 인벤토리 다"):
 * 키 하나로 마우스가 올라간 쪽(상자 또는 내 인벤토리)을 <b>정렬</b>한다.
 *
 * <p>정렬은 <b>슬롯을 서로 바꾸는(스왑, 숫자 키 바꾸기) 선택정렬</b>이라 개수를 건드리지 않는다 - 아이템이 사라지거나 개수가
 * 어긋날 위험이 없다(안티치트 서버에서도 pickup 클릭이라 안전한 편). 같은 아이템이 붙어 정렬되고 빈 칸은 뒤로 간다.
 * (반쪽 스택끼리 합치는 건 아직 안 한다 - 개수 조작은 서버와 어긋나기 쉬워 다음에 안전하게 붙일 것.)
 *
 * <p>1.15.2는 SlotActionType/Slot 이름이 달라 빌드에서 제외(ModuleManager가 리플렉션 등록).
 */
public class InventorySortModule extends Module {

	public enum SortKey {
		NAME("이름"),
		COUNT("갯수"),
		ID("id");

		private final String label;

		SortKey(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Order {
		ASC("오름차순"),
		DESC("내림차순");

		private final String label;

		Order(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final KeybindSetting key = register(new KeybindSetting(
			"key", "정렬 키", "누르면 마우스가 올라간 쪽(상자 또는 내 인벤토리)을 정렬합니다.",
			LunaCompat.mouseKeyCode(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)));
	private final EnumSetting<SortKey> sortKey = register(new EnumSetting<>(
			"sort_key", "정렬 기준", "이름 갯수 id 중 무엇으로 정렬할지입니다.", SortKey.NAME, SortKey.class));
	private final EnumSetting<Order> order = register(new EnumSetting<>(
			"order", "차순", "오름차순 내림차순.", Order.ASC, Order.class));
	// 49-124차(사용자: "핫바도 정리에 포함시킬건지 선택하게 해주고 기본 비활성화"): 기본은 주 인벤토리 3줄만.
	private final BooleanSetting includeHotbar = register(new BooleanSetting(
			"include_hotbar", "핫바 포함", "내 인벤토리를 정렬할 때 핫바 칸(맨 아랫줄)도 함께 정렬합니다.", false));

	private boolean wasDown;

	// 49-124차(사용자: "한 번 눌렀을 때 딱 바로 다 돼야지 여러번 눌러야 됨"): 예전엔 한 틱에 스왑을 수십 번(클릭 수백)
	// 몰아 보내 서버가 뒤쪽 클릭을 씹으면 절반만 정렬돼 또 눌러야 했다. 이제 한 번 누르면 정렬 대상을 잡아 두고,
	// 매 틱 <b>실제 칸 상태를 다시 읽어</b> 몇 칸씩 이어서 끝낸다 - 서버가 씹어도 다음 틱에 다시 맞춰 결국 다 정렬된다.
	private List<Slot> pendingRegion;
	private Object pendingHandler;
	private int pendingSyncId;
	private int stepGuard;
	// 49-466차(사용자: "정리할 때 속도가 보이면 안 돼, 순간이동해야 해, 한 아이템씩 말고 전체를 계산해 놓고 한 번에, 그리고 렉 걸려"):
	// 예전엔 틱마다 선택정렬을 처음부터 다시 돌려(비교 수천 번, 매번 이름 글자 만들기) 4번씩만 바꿔서 옮겨지는 게 보이고 렉이 걸렸다.
	// 이제 누르는 순간 목표 배치를 한 번에 계산하고 틀린 칸을 그 틱에 전부 바꾼다(클라이언트가 바로 반영 = 순간이동). 다음 틱에 실제 칸을
	// 다시 읽어 서버가 씹은 게 있으면 남은 것만 다시(최대 5번).

	public InventorySortModule() {
		super("inventory_sort", "인벤토리 정리", ModuleCategory.INVENTORY, "키 하나로 상자/인벤토리 정렬(이름/갯수/id | 오름/내림)");
		defaultEnabled(true);
	}

	@Override
	protected void onDisable() {
		pendingRegion = null;
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.interactionManager == null) {
			wasDown = false;
			pendingRegion = null;
			return;
		}
		if (!(client.currentScreen instanceof HandledScreen) || LunaCompat.isCreativeInventory(client.currentScreen)) {
			wasDown = false;
			pendingRegion = null;
			return;
		}
		// 진행 중인 정렬을 몇 칸씩 이어 끝낸다(한 번 눌러 전부 정렬).
		if (pendingRegion != null) {
			sortStep();
		}
		boolean down = key.isBound() && key.isDown(client);
		if (down && !wasDown && cursorEmpty() && pendingRegion == null) {
			beginSort();
		}
		wasDown = down;
	}

	private boolean cursorEmpty() {
		try {
			ItemStack cursor = LunaCompat.cursorStack(client.player);
			return cursor == null || cursor.isEmpty();
		} catch (Throwable ignored) {
			return true;
		}
	}

	/** 마우스가 올라간 칸이 속한 쪽을 정렬 대상으로 잡는다(실제 정렬은 매 틱 sortStep이 이어서 한다). */
	private void beginSort() {
		Slot hovered = focusedSlot(client.currentScreen);
		if (hovered == null) {
			return;
		}
		List<Slot> region = new ArrayList<>();
		var handler = client.player.currentScreenHandler;
		if (isStorage(hovered)) {
			for (Slot s : handler.slots) {
				if (isStorage(s)) {
					region.add(s);
				}
			}
		} else if (hovered.inventory instanceof PlayerInventory) {
			// 내 인벤토리 = 주 3줄(칸 index 9~35). 핫바 포함이 켜져 있으면 핫바(0~8)까지.
			// 방어구/제작칸(그 밖의 index)은 건드리지 않는다.
			int lo = includeHotbar.get() ? 0 : 9;
			for (Slot s : handler.slots) {
				if (s.inventory instanceof PlayerInventory) {
					int idx = slotIndex(s);
					if (idx >= lo && idx <= 35) {
						region.add(s);
					}
				}
			}
		} else {
			return;   // 제작 격자·결과 등은 정렬 안 함
		}
		if (region.size() < 2) {
			return;
		}
		pendingRegion = region;
		pendingHandler = handler;
		pendingSyncId = handler.syncId;
		stepGuard = 0;
	}

	/**
	 * 매 틱 한 걸음: <b>실제 칸 상태를 다시 읽어</b> 선택정렬로 자리가 틀린 칸을 최대 {@link #SWAPS_PER_TICK}개만 바로잡는다.
	 * 스왑은 pickup 클릭이라 개수를 안 건드린다. 더 바꿀 게 없으면(정렬 끝) 대상을 놓는다. 서버가 클릭을 씹어도 다음
	 * 틱에 다시 읽어 남은 것만 이어서 하므로 <b>한 번 누르면 결국 전부 정렬</b>된다.
	 */
	private void sortStep() {
		var handler = client.player.currentScreenHandler;
		if (handler != pendingHandler || handler.syncId != pendingSyncId || !cursorEmpty()) {
			pendingRegion = null;   // 화면이 바뀌었거나 커서에 뭔가 들려 있으면 중단
			return;
		}
		if (++stepGuard > 5) {
			pendingRegion = null;   // 안전장치(무한 방지)
			return;
		}
		List<Slot> region = pendingRegion;
		int n = region.size();
		ItemStack[] model = new ItemStack[n];
		for (int i = 0; i < n; i++) {
			ItemStack st = region.get(i).getStack();
			model[i] = st == null ? ItemStack.EMPTY : st;
		}
		boolean desc = order.get() == Order.DESC;
		// 목표 배치: 지금 칸들을 한 번에 안정 정렬(같은 것끼리는 원래 순서)
		Integer[] idx = new Integer[n];
		String[] names = new String[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
			names[i] = model[i].isEmpty() ? "" : nameOf(model[i]);
		}
		final ItemStack[] snap = model.clone();
		java.util.Arrays.sort(idx, (x, y) -> compare(snap[x], snap[y], names[x], names[y], desc));
		ItemStack[] target = new ItemStack[n];
		for (int p = 0; p < n; p++) {
			target[p] = snap[idx[p]];
		}
		// 틀린 칸만 바꾼다 - 이 틱에 전부
		int swapsDone = 0;
		for (int p = 0; p < n; p++) {
			if (same(model[p], target[p])) {
				continue;
			}
			int q = -1;
			for (int k = p + 1; k < n; k++) {
				if (same(model[k], target[p])) {
					q = k;
					break;
				}
			}
			if (q < 0) {
				continue;
			}
			swap(region, model, p, q);
			swapsDone++;
		}
		if (swapsDone == 0) {
			pendingRegion = null;   // 바꿀 게 없다 = 정렬 완료
		}
	}

	/** 49-466차: 같은 칸으로 볼 것(둘 다 빔, 또는 같은 아이템 + 같은 개수). */
	private static boolean same(ItemStack a, ItemStack b) {
		boolean ae = a == null || a.isEmpty();
		boolean be = b == null || b.isEmpty();
		if (ae || be) {
			return ae && be;
		}
		return a.getCount() == b.getCount() && LunaCompat.sameStack(a, b);
	}

	/** a가 b보다 앞에 와야 하면 음수. 빈 칸은 항상 뒤. desc면 비지 않은 것끼리 순서를 뒤집는다. 이름은 미리 만든 것(49-466차). */
	private int compare(ItemStack a, ItemStack b, String nameA, String nameB, boolean desc) {
		boolean ae = a == null || a.isEmpty();
		boolean be = b == null || b.isEmpty();
		if (ae && be) {
			return 0;
		}
		if (ae) {
			return 1;    // 빈 칸은 뒤로
		}
		if (be) {
			return -1;
		}
		int c;
		switch (sortKey.get()) {
			case COUNT -> c = Integer.compare(a.getCount(), b.getCount());
			case ID -> c = idOf(a).compareTo(idOf(b));
			default -> c = nameA.compareToIgnoreCase(nameB);
		}
		if (c == 0 && sortKey.get() != SortKey.NAME) {
			c = nameA.compareToIgnoreCase(nameB);   // 2차 기준: 이름
		}
		return desc ? -c : c;
	}

	private static String nameOf(ItemStack s) {
		try {
			return s.getName().getString();
		} catch (Throwable t) {
			return "";
		}
	}

	private static String idOf(ItemStack s) {
		try {
			Identifier id = LunaCompat.getItemId(s.getItem());
			return id == null ? "" : id.toString();
		} catch (Throwable t) {
			return "";
		}
	}

	/**
	 * region[i] ↔ region[j] 스왑(모델 + 서버 클릭). 49-416차(사용자: "아이템 정리하면 꾸러미에 아이템이 들어가 버려"):
	 * 예전엔 커서로 집어 옮겼는데(PICKUP), 들고 있는 아이템을 꾸러미 칸에 누르면 꾸러미가 그걸 넣어 버리고, 같은 아이템끼리는
	 * 합쳐져 개수도 바뀌었다. 이제 숫자 키 바꾸기(SWAP - 칸과 핫바 칸을 그대로 맞바꿈)만 쓴다. 커서를 안 써서 꾸러미에 들어가거나
	 * 합쳐지는 일이 없다. 둘 다 핫바 칸이 아니면 핫바 9번 칸을 거쳐 세 번 바꾼다(9번 칸은 원래대로 돌아온다).
	 */
	private void swap(List<Slot> region, ItemStack[] model, int i, int j) {
		int syncId = client.player.currentScreenHandler.syncId;
		int idI = region.get(i).id;
		int idJ = region.get(j).id;
		int hi = hotbarOf(region.get(i)), hj = hotbarOf(region.get(j));
		try {
			if (hi >= 0) {
				swapWithHotbar(syncId, idJ, hi);       // j ↔ (핫바 칸인) i
			} else if (hj >= 0) {
				swapWithHotbar(syncId, idI, hj);       // i ↔ (핫바 칸인) j
			} else {
				swapWithHotbar(syncId, idI, TEMP_HOTBAR);   // i ↔ 9번
				swapWithHotbar(syncId, idJ, TEMP_HOTBAR);   // j ↔ 9번(= 원래 i)
				swapWithHotbar(syncId, idI, TEMP_HOTBAR);   // i ↔ 9번(= 원래 j), 9번은 원래대로
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("inventorySort:swap", t);
			return;
		}
		ItemStack tmp = model[i];
		model[i] = model[j];
		model[j] = tmp;
	}

	/** 바꿀 때 거치는 핫바 칸(9번 = index 8). */
	private static final int TEMP_HOTBAR = 8;

	private void swapWithHotbar(int syncId, int slotId, int hotbar) {
		client.interactionManager.clickSlot(syncId, slotId, hotbar, SlotActionType.SWAP, client.player);
	}

	/** 내 핫바 칸이면 그 번호(0~8), 아니면 -1. */
	private static int hotbarOf(Slot s) {
		if (!(s.inventory instanceof PlayerInventory)) {
			return -1;
		}
		int idx = slotIndex(s);
		return idx >= 0 && idx <= 8 ? idx : -1;
	}

	// ==================== 슬롯 판별(마우스 트윅스와 같은 방식) ====================

	private static Slot focusedSlot(Object screen) {
		if (!(screen instanceof HandledScreen)) {
			return null;
		}
		try {
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(HandledScreen.class, "focusedSlot");
			f.setAccessible(true);
			Object v = f.get(screen);
			return v instanceof Slot s ? s : null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("inventorySort:focusedSlot", t);
			return null;
		}
	}

	/** 저장 컨테이너(상자·통 등) 칸인가 - 플레이어 인벤토리도, 제작 격자·결과도 아닌 것. */
	private static boolean isStorage(Slot s) {
		if (s.inventory instanceof PlayerInventory) {
			return false;
		}
		String n = s.inventory == null ? "" : s.inventory.getClass().getSimpleName();
		return !(n.contains("Crafting") || n.contains("Result") || n.contains("Recipe"));
	}

	private static int slotIndex(Slot s) {
		try {
			java.lang.reflect.Method m = LunaCompat.findAnyMethod(s.getClass(), "getIndex");
			if (m != null) {
				Object r = m.invoke(s);
				if (r instanceof Integer i) {
					return i;
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(Slot.class, "index");
			f.setAccessible(true);
			return f.getInt(s);
		} catch (Throwable ignored) {
		}
		return -1;
	}
}
