package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 아이템 일괄 정리(넣기 · 꺼내기).
 *
 * 49-21차 재작성(사용자: "마우스 포인터에 있는 아이템을 그 키를 눌렀을 때 상자로 바로 이동시켜주는 기능 /
 * 마우스 올렸을 때 옮겨질 같은 아이템 하이라이트"):
 *  - [옮기기 키] 상자류 화면에서 마우스가 올라간 슬롯의 아이템과 **같은 종류 전부**를 반대쪽으로(shift-click).
 *    마우스가 **상자 쪽** 슬롯에 있으면 상자 → 인벤토리로 꺼내고, 인벤토리 쪽이면 상자로 넣는다. 처리 대상은
 *    항상 마우스가 올라간 쪽의 슬롯들.
 *  - [하이라이트] 화면에서 마우스를 올리면 같이 처리될 슬롯들이 연두색으로 표시(Screen API 렌더 훅).
 * 슬롯 조작은 clickSlot 기반이라 안티치트가 있는 서버에서는 주의(싱글/친구 서버용).
 *
 * 49-94차(사용자: "한번에 버리기 키는 아예 삭제"): [버리기 키]와 그 처리 경로를 통째로 뺐다. 옮기기(넣기·꺼내기)만 남는다.
 */
public class DropDumpModule extends Module {

	private final KeybindSetting moveKey;
	/** 49-245차(사용자: "일괄 정리에 인벤토리 모든 아이템 한 번에 옮기기 추가, 이것도 키 지정으로 기본 없음"). */
	private final KeybindSetting moveAllKey;
	private final BooleanSetting highlight;
	private final ColorSetting highlightColor;
	private final IntSetting slotsPerSecond;

	private boolean prevMove, prevMoveAll;
	/** true = 종류 상관없이 그쪽 칸 전부. */
	private boolean allItems;
	/** 49-245차: 이번 처리에서 이미 누른 칸(상자가 꽉 차 안 옮겨진 칸을 계속 누르지 않게). */
	private final java.util.Set<Integer> tried = new java.util.HashSet<>();
	private int tickCounter, nextActionTick;

	private Item targetItem;
	/** 처리할 슬롯이 내 인벤토리 쪽인지(false = 상자 쪽). */
	private boolean fromPlayer;
	private boolean working;

	public DropDumpModule() {
		super("drop_dump", "일괄 정리", ModuleCategory.INVENTORY, "같은 종류를 한 번에 넣기 | 꺼내기");
		moveKey = register(new KeybindSetting("move_key", "옮기기 키",
				"마우스를 올린 아이템과 같은 종류를 전부 반대쪽으로 옮깁니다. 인벤토리 쪽이면 상자로 넣고, 상자 쪽이면 인벤토리로 꺼냅니다.", InputConstants.KEY_R));
		moveAllKey = register(new KeybindSetting("move_all_key", "전부 옮기기 키",
				"상자를 연 채 누르면 내 인벤토리 아이템을 전부 상자로 넣습니다. 상자 쪽 칸에 마우스를 올리고 누르면 상자 아이템을 전부 꺼냅니다.", -1));
		highlight = register(new BooleanSetting("highlight", "대상 표시", "마우스를 올리면 함께 처리될 칸을 표시합니다.", true));
		highlightColor = register(new ColorSetting("highlight_color", "표시 색", "함께 처리될 칸의 색입니다.", 0x59A9D973));
		// 49-24차: "속도가 너무 느려 - 쫘라락이 돼야" → 초당 5~200칸(틱당 여러 칸), 기본 60.
		slotsPerSecond = register(new IntSetting("speed", "속도", "초당 처리하는 칸 수입니다.", 60, 5, 200, 5).unit("초"));

		LunaCompat.registerScreenAfterRender(this::renderHighlight);
	}

	// ==================== 입력 ====================

	@Override
	public void onTick() {
		if (client.player == null || client.gameMode == null) {
			return;
		}
		tickCounter++;
		boolean moveNow = moveKey.isDown(client);
		boolean moveEdge = moveNow && !prevMove;
		prevMove = moveNow;

		// 49-40차: 크리에이티브 창에서는 아무것도 하지 않는다(가짜 칸 → 서버 인벤토리 손상, LunaCompat.isCreativeInventory)
		if (LunaCompat.isCreativeInventory(kr.lunaslight.mod.util.LunaCompat.screenOf(client))) {
			working = false;
			return;
		}
		boolean inContainer = kr.lunaslight.mod.util.LunaCompat.screenOf(client) instanceof AbstractContainerScreen<?> hs && !isPlayerInventory(hs);
		boolean allNow = moveAllKey.isBound() && moveAllKey.isDown(client);
		boolean allEdge = allNow && !prevMoveAll;
		prevMoveAll = allNow;
		if (allEdge && inContainer) {
			Slot hovered = focusedSlot(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
			// 상자 쪽 칸에 마우스가 있으면 상자 → 인벤토리, 아니면(인벤토리 쪽이나 빈 곳) 인벤토리 → 상자
			boolean fromBox = hovered != null && !isPlayerSide(hovered);
			startAll(!fromBox);
		} else if (moveEdge && inContainer) {
			Slot hovered = focusedSlot(kr.lunaslight.mod.util.LunaCompat.screenOf(client));
			Item item = itemOf(hovered);
			if (item != null) {
				// 마우스가 올라간 쪽의 같은 종류를 전부 반대쪽으로: 인벤토리 → 상자(넣기), 상자 → 인벤토리(꺼내기)
				start(item, isPlayerSide(hovered));
			}
		}
		process();
	}

	private static boolean isPlayerInventory(AbstractContainerScreen<?> screen) {
		return screen.getMenu() == net.minecraft.client.Minecraft.getInstance().player.inventoryMenu;
	}

	/** 슬롯의 아이템(비었으면 null). */
	private static Item itemOf(Slot slot) {
		if (slot == null) {
			return null;
		}
		ItemStack s = slot.getItem();
		return s == null || s.isEmpty() ? null : s.getItem();
	}

	/** 슬롯이 내 인벤토리 쪽인지(상자·제작대 등은 false). */
	private static boolean isPlayerSide(Slot slot) {
		return slot != null && slot.container instanceof Inventory;
	}

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
			LunaCompat.warnOnce("dump:focusedSlot", t);
			return null;
		}
	}

	private void startAll(boolean playerSide) {
		targetItem = null;
		allItems = true;
		tried.clear();
		fromPlayer = playerSide;
		working = true;
		nextActionTick = tickCounter;
	}

	private void start(Item item, boolean playerSide) {
		allItems = false;
		tried.clear();
		targetItem = item;
		fromPlayer = playerSide;
		working = true;
		nextActionTick = tickCounter;
	}

	// ==================== 처리 ====================

	private void process() {
		if (!working || (targetItem == null && !allItems)) {
			return;
		}
		if (tickCounter < nextActionTick) {
			return;
		}
		if (!(kr.lunaslight.mod.util.LunaCompat.screenOf(client) instanceof AbstractContainerScreen)) {
			working = false; // 상자가 닫혔음
			return;
		}
		// 49-24차: 초당 속도 → 틱당 처리 칸 수(20 이하는 몇 틱에 한 칸)
		int perSecond = Math.max(1, slotsPerSecond.get());
		int perTick = Math.max(1, Math.round(perSecond / 20f));
		int syncId = client.player.containerMenu.containerId;
		for (int n = 0; n < perTick; n++) {
			int slot = findMatchingSlot();
			if (slot < 0) {
				working = false;
				return;
			}
			client.gameMode.handleContainerInput(syncId, slot, 0, ContainerInput.QUICK_MOVE, client.player);
			tried.add(slot);
		}
		nextActionTick = tickCounter + (perSecond >= 20 ? 1 : Math.max(1, 20 / perSecond));
	}

	/** 처리할 쪽(내 인벤토리 / 상자) 슬롯 중 대상 아이템이 있는 첫 칸(내 인벤토리 화면에선 제작칸/갑옷/보조손 제외). */
	private int findMatchingSlot() {
		var handler = client.player.containerMenu;
		boolean playerHandler = handler == client.player.inventoryMenu;
		int size = handler.slots.size();
		for (int i = 0; i < size; i++) {
			Slot slot = handler.getSlot(i);
			if (isPlayerSide(slot) != fromPlayer || tried.contains(i)) {
				continue;
			}
			if (playerHandler && (i < 9 || i > 44)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && (allItems || stack.getItem() == targetItem)) {
				return i;
			}
		}
		return -1;
	}

	// ==================== 하이라이트(화면 렌더 훅) ====================

	private void renderHighlight(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		if (!isEnabled() || !highlight.get() || !(screen instanceof AbstractContainerScreen<?> hs)) {
			return;
		}
		if (!moveKey.isBound()) {
			return;
		}
		Slot focused = focusedSlot(screen);
		if (focused == null || focused.getItem().isEmpty()) {
			return;
		}
		Item item = focused.getItem().getItem();
		boolean playerSide = isPlayerSide(focused);
		int ox, oy;
		try {
			java.lang.reflect.Field fx = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "x");
			java.lang.reflect.Field fy = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "y");
			fx.setAccessible(true);
			fy.setAccessible(true);
			ox = fx.getInt(hs);
			oy = fy.getInt(hs);
		} catch (Throwable t) {
			LunaCompat.warnOnce("dump:screenXY", t);
			return;
		}
		int color = highlightColor.getArgb();
		for (Slot slot : hs.getMenu().slots) {
			// 마우스가 올라간 쪽(인벤토리/상자)의 같은 종류만 - 그쪽이 처리 대상
			if (slot == focused || isPlayerSide(slot) != playerSide) {
				continue;
			}
			ItemStack s = slot.getItem();
			if (s.isEmpty() || s.getItem() != item) {
				continue;
			}
			int sx = ox + slot.x, sy = oy + slot.y;
			ctx.fill(sx, sy, sx + 16, sy + 16, color);
			// 얇은 테두리
			int edge = (color & 0x00FFFFFF) | 0xC8000000;
			ctx.fill(sx, sy, sx + 16, sy + 1, edge);
			ctx.fill(sx, sy + 15, sx + 16, sy + 16, edge);
			ctx.fill(sx, sy, sx + 1, sy + 16, edge);
			ctx.fill(sx + 15, sy, sx + 16, sy + 16, edge);
		}
	}
}
