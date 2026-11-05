package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

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
			LunaCompat.mouseKeyCode(2)));
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
		if (client == null || client.player == null || client.gameMode == null) {
			wasDown = false;
			pendingRegion = null;
			return;
		}
		if (!(kr.lunaslight.mod.util.LunaCompat.screenOf(client) instanceof AbstractContainerScreen) || LunaCompat.isCreativeInventory(kr.lunaslight.mod.util.LunaCompat.screenOf(client))) {
			wasDown = false;
			pendingRegion = null;
			return;
		}
		// 진행 중인 정렬을 몇 칸씩 이어 끝낸다(한 번 눌러 전부 정렬).
		if (pendingRegion != null) {
			sortStep();
		}
		boolean down = key.isBound() && key.isDown(client);
		if (down && !wasDown && cursorEmpty() && pendingRegion == null && !spreadClick()) {
			beginSort();
		}
		wasDown = down;
	}

	/** 49-569차(사용자: "쉬프트 휠 누르면 아이템 정리 안 되게"): Shift를 누른 채면 아이템 펼치기(Shift + 휠클릭) 몫이라 정렬하지 않는다. */
	private boolean spreadClick() {
		return ItemSpreadModule.busy(client);   // 49-572차: 펼치기 키를 바꿀 수 있게 되어 그 키가 눌려 있는지로
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
		Slot hovered = focusedSlot(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
		if (hovered == null) {
			return;
		}
		List<Slot> region = new ArrayList<>();
		var handler = client.player.containerMenu;
		if (isStorage(hovered) && customGui(kr.lunaslight.mod.util.LunaCompat.screenOf(client))) {
			// 49-513차: 서버가 그린 메뉴(너굴마을 창고 목록, 상점 …)의 칸은 버튼이다 - 누르면 안 된다
			return;
		}
		if (isStorage(hovered)) {
			for (Slot s : handler.slots) {
				if (isStorage(s)) {
					region.add(s);
				}
			}
		} else if (hovered.container instanceof Inventory) {
			// 내 인벤토리 = 주 3줄(칸 index 9~35). 핫바 포함이 켜져 있으면 핫바(0~8)까지.
			// 방어구/제작칸(그 밖의 index)은 건드리지 않는다.
			int lo = includeHotbar.get() ? 0 : 9;
			for (Slot s : handler.slots) {
				if (s.container instanceof Inventory) {
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
		pendingSyncId = handler.containerId;
		stepGuard = 0;
	}

	/**
	 * 49-513차(사용자: "여전히 인벤 정리하면 렉 걸리고 정리도 제대로 안 돼, 내 인벤에 막 아이템이 들어오거나 버벅거리고"):
	 * 예전엔 칸 둘을 바꿀 때마다 핫바 9번 칸을 거쳐 SWAP 세 번(상자 정리인데 내 핫바로 아이템이 오갔다 - 서버가 하나라도 씹으면 내 인벤에 남음)
	 * + 매번 반사(reflection)로 아이템 비교를 수천 번 했다. 이제:
	 * <ol>
	 *   <li>같은 아이템의 덜 찬 묶음을 먼저 합친다(집어서 다른 묶음에 놓기).</li>
	 *   <li>목표 배치를 한 번 계산하고, <b>커서로 집어 옮기는 순환</b>(PICKUP)으로 한 번에 옮긴다 - 칸 하나당 클릭 한 번, 그 상자(또는 내 인벤) 안에서만.</li>
	 *   <li>같은 아이템끼리는 개수가 달라도 제자리로 본다(놓으면 합쳐지기만 하고 안 바뀌어서). 꾸러미(번들)는 그 자리에 두고 정렬하지 않는다
	 *       (들고 누르면 꾸러미에 들어가 버림).</li>
	 *   <li>다음 틱에 실제 칸을 다시 읽어 틀린 게 남았으면 다시(최대 3번).</li>
	 * </ol>
	 */
	private void sortStep() {
		var handler = client.player.containerMenu;
		if (handler != pendingHandler || handler.containerId != pendingSyncId || !cursorEmpty()) {
			pendingRegion = null;   // 화면이 바뀌었거나 커서에 뭔가 들려 있으면 중단
			return;
		}
		if (++stepGuard > 3) {
			pendingRegion = null;
			return;
		}
		List<Slot> region = new ArrayList<>();
		for (Slot sl : pendingRegion) {
			if (!isBundle(sl.getItem())) {
				region.add(sl);
			}
		}
		int n = region.size();
		if (n < 2) {
			pendingRegion = null;
			return;
		}
		int sync = handler.containerId;
		int[] budget = {600};
		mergePartials(region, sync, budget);
		if (!cursorEmpty()) {
			pendingRegion = null;
			return;
		}
		// 목표 배치(안정 정렬, 이름은 한 번만 만든다)
		ItemStack[] snap = new ItemStack[n];
		String[] names = new String[n];
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			ItemStack st = region.get(i).getItem();
			snap[i] = st == null || st.isEmpty() ? ItemStack.EMPTY : st.copy();
			names[i] = snap[i].isEmpty() ? "" : nameOf(snap[i]);
			idx[i] = i;
		}
		boolean desc = order.get() == Order.DESC;
		java.util.Arrays.sort(idx, (x, y) -> compare(snap[x], snap[y], names[x], names[y], desc));
		ItemStack[] target = new ItemStack[n];
		for (int p = 0; p < n; p++) {
			target[p] = snap[idx[p]];
		}
		int clicks = 0;
		for (int p = 0; p < n && budget[0] > 0; p++) {
			if (eq(live(region, p), target[p])) {
				continue;
			}
			// target[p]와 같은 아이템인데 제자리가 아닌 칸을 집는다
			int q = -1;
			for (int k = 0; k < n; k++) {
				if (k != p && eq(live(region, k), target[p]) && !eq(live(region, k), target[k])) {
					q = k;
					break;
				}
			}
			if (q < 0) {
				continue;
			}
			click(sync, region.get(q));          // 집기(q는 빈다)
			click(sync, region.get(p));          // p에 놓고 p에 있던 것을 든다
			clicks += 2;
			// 든 것을 제자리로 - 그 자리에 있던 것을 또 들고 … 빈 칸에 놓이면 끝
			int guard = n + 2;
			while (!cursorEmpty() && guard-- > 0 && budget[0] > 0) {
				ItemStack c = handler.getCarried();
				int r = -1;
				for (int k = 0; k < n; k++) {
					if (eq(target[k], c) && !eq(live(region, k), target[k])) {
						r = k;
						if (live(region, k).isEmpty()) {
							break;   // 빈 칸이면 바로 끝낼 수 있다
						}
					}
				}
				if (r < 0) {
					r = q;   // 갈 데가 없으면 처음 비운 칸에
				}
				click(sync, region.get(r));
				clicks++;
			}
			if (!cursorEmpty()) {
				// 안전: 비어 있는 칸 아무 데나 내려놓는다
				for (int k = 0; k < n && !cursorEmpty(); k++) {
					if (live(region, k).isEmpty()) {
						click(sync, region.get(k));
					}
				}
				break;
			}
			budget[0] -= clicks;
			clicks = 0;
		}
		// 다 맞았으면 끝, 아니면 다음 틱에 다시 읽어 남은 것만
		boolean done = true;
		for (int p = 0; p < n; p++) {
			if (!eq(live(region, p), target[p])) {
				done = false;
				break;
			}
		}
		if (done) {
			pendingRegion = null;
		}
	}

	/** 같은 아이템의 덜 찬 묶음 합치기: 뒤쪽 묶음을 집어 앞쪽 묶음에 놓는다(넘치면 남은 걸 도로 놓음). */
	private void mergePartials(List<Slot> region, int sync, int[] budget) {
		int n = region.size();
		for (int i = 0; i < n && budget[0] > 0; i++) {
			ItemStack a = live(region, i);
			if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) {
				continue;
			}
			for (int j = n - 1; j > i && budget[0] > 0; j--) {
				ItemStack b = live(region, j);
				a = live(region, i);
				if (a.getCount() >= a.getMaxStackSize()) {
					break;
				}
				if (b.isEmpty() || b.getCount() >= b.getMaxStackSize() || !ItemStack.isSameItemSameComponents(a, b)) {
					continue;
				}
				click(sync, region.get(j));   // 뒤쪽 묶음 집기
				click(sync, region.get(i));   // 앞쪽에 합치기
				budget[0] -= 2;
				if (!cursorEmpty()) {
					click(sync, region.get(j));   // 남은 건 도로
					budget[0]--;
				}
			}
		}
	}

	private static ItemStack live(List<Slot> region, int k) {
		ItemStack st = region.get(k).getItem();
		return st == null ? ItemStack.EMPTY : st;
	}

	/** 같은 칸으로 볼 것: 둘 다 빔, 또는 같은 아이템 + 같은 성분(개수는 무관 - 놓으면 합쳐질 뿐 안 바뀐다). */
	private static boolean eq(ItemStack a, ItemStack b) {
		boolean ae = a == null || a.isEmpty();
		boolean be = b == null || b.isEmpty();
		if (ae || be) {
			return ae && be;
		}
		return ItemStack.isSameItemSameComponents(a, b);
	}

	private static boolean isBundle(ItemStack st) {
		try {
			return st != null && !st.isEmpty() && st.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
		} catch (Throwable t) {
			return false;
		}
	}

	private void click(int sync, Slot s) {
		client.gameMode.handleContainerInput(sync, s.index, 0, ContainerInput.PICKUP, client.player);
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
		if (c == 0 && !ItemStack.isSameItemSameComponents(a, b)) {
			c = idOf(a).compareTo(idOf(b));   // 이름이 같아도 다른 아이템이면 id로 갈라 같은 것끼리 붙게
		}
		return desc ? -c : c;
	}

	private static String nameOf(ItemStack s) {
		try {
			return s.getHoverName().getString();
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

	/** 내 핫바 칸이면 그 번호(0~8), 아니면 -1. */
	private static int hotbarOf(Slot s) {
		if (!(s.container instanceof Inventory)) {
			return -1;
		}
		int idx = slotIndex(s);
		return idx >= 0 && idx <= 8 ? idx : -1;
	}

	// ==================== 슬롯 판별(마우스 트윅스와 같은 방식) ====================

	private static Slot focusedSlot(Object screen) {
		if (!(screen instanceof AbstractContainerScreen)) {
			return null;
		}
		try {
			java.lang.reflect.Field f = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "focusedSlot");
			f.setAccessible(true);
			Object v = f.get(screen);
			return v instanceof Slot s ? s : null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("inventorySort:focusedSlot", t);
			return null;
		}
	}

	/** 제목에 리소스팩 글자(사용자 영역 U+E000~)가 있는 서버 그림 메뉴인가. */
	private static boolean customGui(Object screen) {
		try {
			String t = ((AbstractContainerScreen<?>) screen).getTitle().getString();
			for (int i = 0; i < t.length(); ) {
				int cp = t.codePointAt(i);
				if ((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000) {
					return true;
				}
				i += Character.charCount(cp);
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 저장 컨테이너(상자·통 등) 칸인가 - 플레이어 인벤토리도, 제작 격자·결과도 아닌 것. */
	private static boolean isStorage(Slot s) {
		if (s.container instanceof Inventory) {
			return false;
		}
		String n = s.container == null ? "" : s.container.getClass().getSimpleName();
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
