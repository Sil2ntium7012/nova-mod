package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.ScrollHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 49-27차: 부드러운 마우스(사용자: "마우스 트윅스 모드처럼 쉬프트 클릭으로 여러 아이템을 옮길 수 있게 -
 * 원래는 한 칸밖에 안 되는 걸 없애 주는 것").
 *
 *  ① Shift + 좌클릭을 누른 채 **끌면** 지나가는 칸이 전부 반대쪽으로 옮겨진다(칸마다 shift-click).
 *     같은 칸을 두 번 처리하지 않게 이번 드래그에서 이미 만진 칸을 기억한다.
 *  ② 좌클릭만으로 끌기(보조키 없이)도 옵션. 상자 정리할 때 편하지만 실수로 옮길 수 있어 기본은 꺼짐.
 *  ③ 휠: 마우스를 올린 칸에서 위로 굴리면 그 칸을 반대쪽으로 보내고, 아래로 굴리면 반대쪽에 있는
 *     같은 아이템을 이쪽으로 가져온다(스택 단위 - 개수를 쪼개지 않아 서버와 어긋날 일이 없음).
 *
 * 입력은 새 믹스인 없이 처리한다. 드래그는 화면 렌더 콜백(ScreenEvents.afterRender - 마우스 좌표가 같이
 * 옴)에서 GLFW로 버튼/보조키 상태를 직접 읽고, 휠은 이미 있는 ScrollHook(MouseScrollMixin)에 얹는다.
 *
 * 슬롯 조작은 clickSlot(QUICK_MOVE) 기반이라 안티치트가 있는 서버에서는 주의(싱글·친구 서버용).
 * 1.15.2는 Container/SlotActionType 이름이 달라 컴파일에서 제외(ModuleManager가 리플렉션 등록).
 */
public class MouseTweaksModule extends Module {

	// 49-41차(사용자: "마우스 트윅스는 설정할 게 없어야 하는데 - 그냥 Shift 누르고 끌면 다 옮겨지고"): 설정 전부 제거.
	// Shift+끌기 = 지나간 칸 전부(종류 무관), 휠 = 한 개씩.
	//
	// 49-69차(4-44) 한 프레임 상한. 예전 값은 3이었는데 빨리 끌면 한 프레임에 한 줄(9칸)을 지나간다.
	// 상한에 걸려 남은 칸을 놓치면 "끌었는데 몇 칸이 안 옮겨졌다"가 된다. 그렇다고 무제한으로 두면
	// 패킷이 한꺼번에 몰리므로 한 줄보다 조금 넉넉한 선에서 자른다.
	private static final int PER_FRAME = 12;
	/** 칸 한 변(픽셀). 마우스가 지나간 자리를 훑을 때 쓴다. */
	private static final int SLOT_SIZE = 16;

	/** 이번 드래그에서 이미 처리한 슬롯 id. */
	private final java.util.Set<Integer> handled = new java.util.HashSet<>();
	private boolean dragging;
	private Object dragScreen;
	/** 직전 프레임에 마우스가 있던 칸(건너뛴 칸을 이어 붙이는 기준). */
	private Slot lastSlot;

	public MouseTweaksModule() {
		super("mouse_tweaks", "마우스 트윅스", ModuleCategory.INVENTORY, "Shift 끌기로 여러 칸 | 휠로 한 개씩");
		defaultEnabled(true);
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		ScrollHook.register(this::onScroll);
	}

	// ==================== 공통 ====================

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
			LunaCompat.warnOnce("mouseTweaks:focusedSlot", t);
			return null;
		}
	}

	private boolean keyDown(int key) {
		return client.getWindow() != null && kr.lunaslight.mod.util.LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), key);
	}

	private boolean leftDown() {
		return client.getWindow() != null
				&& kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0);
	}

	private boolean usable() {
		// 49-245차(사용자: "마우스 트윅스 크리에이티브 인벤토리에서도"): 크리에이티브 창도 받는다. 대신 거기선 칸 클릭을 서버로 바로 보내지 않고
		// 화면 자신의 칸 클릭(크리에이티브 화면이 크리에이티브 패킷으로 바꿔 보냄)으로 넘긴다 - 49-40차의 "가짜 칸 번호" 문제가 없다.
		return isEnabled() && client.player != null && client.gameMode != null
				&& kr.lunaslight.mod.util.LunaCompat.screenOf(client) instanceof AbstractContainerScreen;
	}

	/** 커서에 아이템을 들고 있으면(바닐라 드래그 분배 중) 우리가 끼어들지 않는다. */
	private boolean holdingStack() {
		try {
			ItemStack cursor = LunaCompat.cursorStack(client.player); // 49-36차: ≤1.16은 PlayerInventory#getCursorStack
			return cursor != null && !cursor.isEmpty();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private void quickMove(int slotId) {
		click(slotId, 0, ContainerInput.QUICK_MOVE);
	}

	/** 49-245차: 지금 화면이 크리에이티브 인벤토리인지. */
	private boolean creative() {
		return LunaCompat.isCreativeInventory(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
	}

	/** 열린 화면의 칸들(크리에이티브는 플레이어의 열린 메뉴가 아니라 화면 자신의 메뉴를 봐야 한다). */
	private java.util.List<Slot> menuSlots() {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (screen instanceof AbstractContainerScreen<?> hs) {
			try {
				return hs.getMenu().slots;
			} catch (Throwable ignored) {
			}
		}
		return client.player.containerMenu.slots;
	}

	private static java.lang.reflect.Method screenClick;
	private static boolean screenClickResolved;

	/** AbstractContainerScreen의 칸 클릭(Slot, int, int, ContainerInput) - 이름은 버전마다 달라 모양으로 찾는다. */
	private static java.lang.reflect.Method screenClick() {
		if (!screenClickResolved) {
			screenClickResolved = true;
			for (java.lang.reflect.Method m : AbstractContainerScreen.class.getDeclaredMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 4 && p[0] == Slot.class && p[1] == int.class && p[2] == int.class && p[3] == ContainerInput.class
						&& m.getReturnType() == void.class) {
					m.setAccessible(true);
					screenClick = m;
					break;
				}
			}
		}
		return screenClick;
	}

	/** 칸 클릭 하나. 크리에이티브면 화면에 맡기고(크리에이티브 패킷), 아니면 평소처럼 서버로. */
	private void click(int slotId, int button, ContainerInput type) {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (creative()) {
			Slot target = null;
			for (Slot s : menuSlots()) {
				if (s.index == slotId) {
					target = s;
					break;
				}
			}
			java.lang.reflect.Method m = screenClick();
			if (target == null || m == null) {
				return;
			}
			try {
				m.invoke(screen, target, slotId, button, type);
			} catch (Throwable t) {
				LunaCompat.warnOnce("mouseTweaks:creativeClick", t);
			}
			return;
		}
		client.gameMode.handleContainerInput(client.player.containerMenu.containerId, slotId, button, type, client.player);
	}

	// ==================== ① 끌어서 옮기기 ====================

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		if (!usable() || screen != kr.lunaslight.mod.util.LunaCompat.screenOf(client)) {
			return;
		}
		boolean shift = keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT);
		boolean want = shift;
		boolean down = leftDown();

		if (!down || !want || holdingStack()) {
			if (dragging) {
				dragging = false;
				handled.clear();
				dragScreen = null;
				lastSlot = null;
			}
			return;
		}
		if (!dragging || dragScreen != screen) {
			dragging = true;
			dragScreen = screen;
			handled.clear();
			lastSlot = null;
		}

		Slot slot = focusedSlot(screen);
		if (slot == null) {
			return;                    // 칸 밖 - 이어 붙일 기준(lastSlot)은 그대로 둔다
		}
		// 49-69차(4-44): 직전 칸에서 지금 칸까지 **곧게 이어** 지나간 칸을 전부 처리한다.
		// 지금 칸 자신도 이 안에서 처리되므로 따로 부르지 않는다.
		sweep(lastSlot, slot);
		lastSlot = slot;
	}

	/**
	 * 49-69차(4-44) <b>버그 수정</b>: 빠르게 끌 때 건너뛴 칸을 이어 붙이는 방식이 틀렸었다.
	 *
	 * <p>예전 코드(`catchUp`)는 "방금 처리한 칸의 <b>바로 옆·바로 위아래</b> 칸"을 더 처리했다.
	 * 마우스가 지나갔는지와 상관없이 <b>인접하기만 하면</b> 옮겼다는 뜻이다. 그래서 한 줄을 따라
	 * 가로로 끌면 <b>윗줄·아랫줄 칸까지 같이 빨려 나갔다</b> - "건드리지도 않은 칸이 옮겨진다"가 바로 이것이다.
	 *
	 * <p>고친 방식: 직전 프레임의 칸 중심에서 지금 칸 중심까지 <b>직선을 긋고, 그 선이 지나가는 칸만</b>
	 * 처리한다. 화면 좌표가 아니라 <b>칸 좌표(slot.x/slot.y)</b>로 계산하므로 창 위치를 알 필요가 없다
	 * (버전마다 이름이 갈리는 화면 오프셋 필드를 안 건드린다).
	 *
	 * <p>드래그를 막 시작했을 때({@code from == null})는 지금 칸 하나만 처리한다.
	 */
	private void sweep(Slot from, Slot to) {
		if (to == null) {
			return;
		}
		try {
			int moved = take(to);
			if (from == null || from == to) {
				return;
			}
			double x0 = from.x + SLOT_SIZE / 2.0;
			double y0 = from.y + SLOT_SIZE / 2.0;
			double x1 = to.x + SLOT_SIZE / 2.0;
			double y1 = to.y + SLOT_SIZE / 2.0;
			double len = Math.hypot(x1 - x0, y1 - y0);
			if (len <= SLOT_SIZE) {
				return;               // 바로 옆 칸 - 사이에 낀 칸이 없다
			}
			// 칸 폭보다 촘촘하게(4px) 훑어야 사선으로 끌 때도 안 빠진다. 너무 긴 이동은 잘라 낸다.
			int steps = Math.min(128, (int) Math.ceil(len / 4.0));
			for (int i = 1; i < steps && moved < PER_FRAME; i++) {
				double t = i / (double) steps;
				Slot hit = slotAt(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t);
				if (hit != null) {
					moved += take(hit);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("mouseTweaks:sweep", t);
		}
	}

	/** 그 칸을 반대쪽으로 보낸다. 이미 처리했거나 빈 칸이면 0(빈 칸도 기억해 매 프레임 다시 보지 않게). */
	private int take(Slot slot) {
		if (slot == null || !handled.add(slot.index)) {
			return 0;
		}
		if (creative() && !(slot.container instanceof Inventory)) {
			return 0;   // 크리에이티브 아이템 목록 칸은 옮길 대상이 아니다(누르면 한 묶음이 커서에 집힌다)
		}
		ItemStack stack = slot.getItem();
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		quickMove(slot.index);
		return 1;
	}

	/** 칸 좌표 (x, y)를 품고 있는 칸. 없으면 null. */
	private Slot slotAt(double x, double y) {
		for (Slot s : menuSlots()) {
			if (x >= s.x && x < s.x + SLOT_SIZE && y >= s.y && y < s.y + SLOT_SIZE) {
				return s;
			}
		}
		return null;
	}

	// ==================== ③ 휠로 옮기기 ====================

	private boolean onScroll(double horizontal, double vertical) {
		if (!usable() || Math.abs(vertical) < 0.01 || holdingStack()) {
			return false;
		}
		Slot slot = focusedSlot(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
		if (slot == null) {
			return false;
		}
		// 49-32차(사용자: "휠로는 1개씩 움직이고") - 마우스 트윅스와 같은 동작.
		ItemStack stack = slot.getItem();
		if (vertical > 0) {
			// 위로: 이 칸에서 반대쪽으로 한 개
			if (stack == null || stack.isEmpty()) {
				return false;
			}
			int target = findSpotOnOtherSide(slot, stack.getItem());
			if (target < 0) {
				return false;
			}
			moveOne(slot.index, target);
			return true;
		}
		// 아래로: 반대쪽에서 이 칸으로 한 개
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		int source = findOnOtherSide(slot, stack.getItem());
		if (source < 0) {
			return false;
		}
		moveOne(source, slot.index);
		return true;
	}

	// ==================== 49-109차: 어느 화면이든 되는 "반대쪽" 판정 ====================
	// 사용자: "마우스 트윅스가 상자에선 되는데 작업대·플레이어 인벤토리에선 안 돼."
	// 원인: 반대쪽을 "플레이어 vs 비플레이어"로만 봐서, 상자가 없는 화면(작업대·인벤토리)에선 비플레이어 = 제작 격자뿐이라
	//   휠이 아무 데도 못 보냈다. → 저장 상자가 있으면 상자↔플레이어, 없으면 핫바↔주 인벤토리로 옮긴다(제작 격자·결과 칸 제외).

	/** 저장 컨테이너(상자·통 등) 칸인가 - 플레이어 인벤토리도, 제작 격자·결과도 아닌 것. */
	private static boolean isStorage(Slot s) {
		if (s.container instanceof Inventory) {
			return false;
		}
		String n = s.container == null ? "" : s.container.getClass().getSimpleName();
		return !(n.contains("Crafting") || n.contains("Result") || n.contains("Recipe"));
	}

	private boolean hasStorage() {
		if (creative()) {
			return false;   // 49-245차: 크리에이티브는 핫바 ↔ 주 인벤토리만(아이템 목록, 휴지통 칸 제외)
		}
		for (Slot s : menuSlots()) {
			if (isStorage(s)) {
				return true;
			}
		}
		return false;
	}

	/** 슬롯의 인벤토리 내 index(핫바 0-8 판별용). 버전마다 이름이 갈려 리플렉션으로. 못 구하면 -1. */
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

	/** 플레이어 저장 칸 중 가장 아래 줄(핫바)의 화면 y. index 리플렉션이 실패할 때의 폴백 기준. */
	private int hotbarRowY() {
		int maxY = Integer.MIN_VALUE;
		for (Slot s : menuSlots()) {
			if (s.container instanceof Inventory && s.y > maxY) {
				maxY = s.y;
			}
		}
		return maxY;
	}

	/** 이 칸이 핫바(맨 아래 줄)인가. index를 먼저 보고, 못 구하면 화면 y로 판단(버전 무관). */
	private boolean isHotbarSlot(Slot s) {
		if (!(s.container instanceof Inventory)) {
			return false;
		}
		int idx = slotIndex(s);
		if (idx >= 0) {
			return idx <= 8;
		}
		int hy = hotbarRowY();
		return hy != Integer.MIN_VALUE && s.y >= hy - 2;   // 폴백: 맨 아래 줄이면 핫바
	}

	/**
	 * 이 칸이 속한 무리(0/1). storageMode면 저장(0)/플레이어(1), 아니면 핫바(0)/주 인벤토리(1).
	 * 옮길 대상이 못 되는 칸(제작 격자·결과)이면 -1.
	 */
	private int group(Slot s, boolean storageMode) {
		if (storageMode) {
			if (isStorage(s)) {
				return 0;
			}
			return s.container instanceof Inventory ? 1 : -1;
		}
		if (!(s.container instanceof Inventory)) {
			return -1;   // 제작 격자·결과·방어구 외 - 핫바/주 인벤토리 모드에선 제외
		}
		return isHotbarSlot(s) ? 0 : 1;   // 핫바 vs 주 인벤토리
	}

	/** slot의 반대쪽 무리에서 같은 아이템이 있는 첫 칸. */
	private int findOnOtherSide(Slot slot, net.minecraft.world.item.Item item) {
		boolean storageMode = hasStorage();
		int here = group(slot, storageMode);
		if (here < 0) {
			return -1;
		}
		int want = 1 - here;
		for (Slot s : menuSlots()) {
			if (group(s, storageMode) != want) {
				continue;
			}
			ItemStack st = s.getItem();
			if (st != null && !st.isEmpty() && st.getItem() == item) {
				return s.index;
			}
		}
		return -1;
	}

	/**
	 * 한 개만 옮긴다: 원래 칸을 통째로 집어 → 목표 칸에 우클릭으로 한 개 놓고 → 남은 걸 제자리에.
	 * (마인크래프트에 "한 개만 옮기기" 패킷이 따로 없어서 마우스 트윅스도 같은 방식을 쓴다.)
	 */
	private void moveOne(int fromSlot, int toSlot) {
		if (client.gameMode == null || client.player == null || fromSlot < 0 || toSlot < 0) {
			return;
		}
		try {
			click(fromSlot, 0, ContainerInput.PICKUP);
			click(toSlot, 1, ContainerInput.PICKUP);
			click(fromSlot, 0, ContainerInput.PICKUP);
		} catch (Throwable t) {
			LunaCompat.warnOnce("mouseTweaks:moveOne", t);
		}
	}

	/** 반대쪽에서 이 아이템을 더 담을 수 있는 칸(같은 아이템이 있는 칸 우선, 없으면 빈 칸). */
	private int findSpotOnOtherSide(Slot slot, net.minecraft.world.item.Item item) {
		int same = findOnOtherSide(slot, item);
		if (same >= 0) {
			return same;
		}
		try {
			boolean storageMode = hasStorage();
			int here = group(slot, storageMode);
			if (here < 0) {
				return -1;
			}
			int want = 1 - here;
			for (Slot other : menuSlots()) {
				if (group(other, storageMode) != want) {
					continue;
				}
				ItemStack st = other.getItem();
				if (st == null || st.isEmpty()) {
					return other.index;
				}
			}
		} catch (Throwable ignored) {
		}
		return -1;
	}
}
