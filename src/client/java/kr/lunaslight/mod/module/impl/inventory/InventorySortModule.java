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
 * <p>정렬은 <b>슬롯을 서로 바꾸는(스왑) 선택정렬</b>이라 개수를 건드리지 않는다 - 아이템이 사라지거나 개수가
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
	private static final int SWAPS_PER_TICK = 4;   // 틱당 최대 스왑(스왑 1 = 클릭 2~3) - 서버가 씹지 않을 만큼만

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
		if (++stepGuard > 80) {
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
		int swapsDone = 0;
		for (int i = 0; i < n - 1 && swapsDone < SWAPS_PER_TICK; i++) {
			int best = i;
			for (int j = i + 1; j < n; j++) {
				if (compare(model[j], model[best], desc) < 0) {
					best = j;
				}
			}
			if (best != i) {
				swap(region, model, i, best);
				swapsDone++;
			}
		}
		if (swapsDone == 0) {
			pendingRegion = null;   // 바꿀 게 없다 = 정렬 완료
		}
	}

	/** a가 b보다 앞에 와야 하면 음수. 빈 칸은 항상 뒤. desc면 비지 않은 것끼리 순서를 뒤집는다. */
	private int compare(ItemStack a, ItemStack b, boolean desc) {
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
			default -> c = nameOf(a).compareToIgnoreCase(nameOf(b));
		}
		if (c == 0 && sortKey.get() != SortKey.NAME) {
			c = nameOf(a).compareToIgnoreCase(nameOf(b));   // 2차 기준: 이름
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

	/** region[i] ↔ region[j] 스왑(모델 + 서버 클릭). */
	private void swap(List<Slot> region, ItemStack[] model, int i, int j) {
		int syncId = client.player.currentScreenHandler.syncId;
		int idI = region.get(i).id;
		int idJ = region.get(j).id;
		boolean jEmpty = model[j] == null || model[j].isEmpty();
		try {
			click(syncId, idI);              // 집기: 커서 = i, i 비움
			click(syncId, idJ);              // j가 있으면 커서↔j 교환(커서=j, j=i), 비었으면 j=i·커서 비움
			if (!jEmpty) {
				click(syncId, idI);          // 커서(=j)를 i에
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("inventorySort:swap", t);
			return;
		}
		ItemStack tmp = model[i];
		model[i] = model[j];
		model[j] = tmp;
	}

	private void click(int syncId, int slotId) {
		client.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, client.player);
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
