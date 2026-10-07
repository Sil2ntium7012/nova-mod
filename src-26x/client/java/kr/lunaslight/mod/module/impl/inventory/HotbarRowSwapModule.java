package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.KeyHook;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.ScrollHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 49-23차: "인벤토리 3줄을 Alt 눌러서 쉽게 바꿀 수 있는 기능 - Alt 누르고 휠 위아래로 인벤 1줄을 핫바랑 스왑".
 *
 * Alt(변경 가능)를 누른 채 휠을 굴리면 인벤토리 줄(핫바 위 3줄)과 핫바를 통째로 바꾼다. 방식 두 가지:
 *  - 4줄 순환(기본): 휠 아래 = 바로 윗줄이 핫바로 내려오고 나머지가 한 칸씩 밀림(핫바 → 맨 윗줄),
 *    휠 위 = 반대. 계속 굴리면 4줄을 차례로 돌아 원위치.
 *  - 윗줄과 교환: 방향에 상관없이 핫바 ↔ 바로 윗줄만 맞바꿈.
 *
 * 실제 이동은 AutoRefill과 같은 clickSlot(SWAP) - 서버가 플레이어 인벤토리 핸들러(syncId 0)로 처리하므로
 * 멀티에서도 동작(칸 하나당 패킷 하나, 빈 칸끼리는 생략). 화면이 열려 있거나 다른 상자를 보고 있으면 안 함.
 * 1.15.2는 SlotActionType 패키지가 달라 컴파일 제외(no_container_modules).
 */
public class HotbarRowSwapModule extends Module {

	public enum Mode {
		ROTATE("4줄 순환"),
		SWAP_ABOVE("윗줄 교환");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final KeybindSetting modifier = register(new KeybindSetting(
			"modifier", "키", "이 키를 누른 채 휠을 굴리면 줄이 바뀝니다.", InputConstants.KEY_LALT));

	private final EnumSetting<Mode> mode = register(new EnumSetting<>(
			"mode", "방식", "4줄 순환은 휠 방향으로 줄이 한 칸씩 돌고, 윗줄 교환은 핫바와 바로 윗줄만 바꿉니다.", Mode.ROTATE, Mode.class));

	private final BooleanSetting invert = register(new BooleanSetting(
			"invert", "방향 반전", "휠 위아래 방향을 반대로 합니다.", false));

	private final BooleanSetting numberKeys = register(new BooleanSetting(
			"number_keys", "1/2/3 키", "누를 키와 1/2/3을 함께 누르면 그 줄(위에서부터)과 핫바가 바로 바뀝니다.", true));

	private long lastSwapNanos;
	private final boolean[] numWas = new boolean[3];

	public HotbarRowSwapModule() {
		// 49-53차(4-39): 설명에 "Alt"를 박아 두지 않는다 - 누를 키는 [누를 키] 설정에서 바꿀 수 있어서,
		// 특정 키 이름을 적어 두면 바꾼 사람에게는 거짓말이 된다.
		super("hotbar_row_swap", "핫바 교체", ModuleCategory.INVENTORY, "휠/숫자키로 인벤토리 줄과 핫바 교체");
		ScrollHook.register(this::onScroll);
		KeyHook.register(this::onKey);
	}

	/** 49-24차: Alt+1~3 - 키보드 믹스인(1.15.2~1.21.8)에서 바닐라 슬롯 선택까지 막고 처리. */
	private boolean onKey(int key, int scancode, int action, int modifiers) {
		if (!isEnabled() || !numberKeys.get() || action != 1 || key < InputConstants.KEY_1 || key > InputConstants.KEY_3
				|| kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null || client.player == null || !modifier.isDown(client)) {
			return false;
		}
		swapRow(key - InputConstants.KEY_1);
		return true;
	}

	@Override
	public void onTick() {
		// 1.21.9+(키보드 믹스인 미적용)에서는 틱 폴링으로 대신(바닐라 슬롯 선택은 같이 일어남)
		if (KeyHook.isMixinActive() || !numberKeys.get() || client.player == null) {
			return;
		}
		boolean mod = kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && modifier.isDown(client);
		for (int i = 0; i < 3; i++) {
			boolean now = mod && LunaCompat.isKeyPressed(client, InputConstants.KEY_1 + i);
			if (now && !numWas[i]) {
				swapRow(i);
			}
			numWas[i] = now;
		}
	}

	/** 인벤토리 줄 row(0 = 맨 위) ↔ 핫바 전체 교환. */
	private void swapRow(int row) {
		if (client.player == null || client.gameMode == null
				|| client.player.containerMenu != client.player.inventoryMenu) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastSwapNanos < 90_000_000L) {
			return;
		}
		lastSwapNanos = now;
		int syncId = client.player.inventoryMenu.containerId;
		var inv = LunaCompat.getPlayerInventory(client.player);
		for (int c = 0; c < 9; c++) {
			swap(syncId, inv, row, c);
		}
	}

	@Override
	public boolean hasPreview() {
		return false;   // 49-234차(사용자: "핫바 교체하기 미리보기도 없애줘")
	}

	/** 미리보기: 마크 인벤토리 느낌의 3줄 + 핫바, 핫바와 바로 윗줄 사이에 교환 화살표. */
	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!isPreview()) {
			return;
		}
		int cols = 9;
		int cell = 18;
		int panelW = cols * cell + 14;
		int panelH = 3 * cell + 4 + cell + 14;
		int px = previewCenterX() - panelW / 2;
		int py = previewCenterY() - panelH / 2;
		kr.lunaslight.mod.gui.LunaDraw.mcPanel(context, px, py, panelW, panelH);
		int gx = px + 7;
		int gy = py + 7;
		Item[] sample = {Items.DIAMOND_SWORD, Items.COBBLESTONE, Items.OAK_LOG, Items.BREAD, null, Items.TORCH, null, null, Items.ARROW};
		for (int r = 0; r < 4; r++) {
			int y = gy + r * cell + (r == 3 ? 4 : 0);
			for (int c = 0; c < cols; c++) {
				int x = gx + c * cell;
				kr.lunaslight.mod.gui.LunaDraw.mcSlot(context, x, y);
				Item it = r == 3 ? sample[c] : (r == 2 && c < 4 ? new Item[]{Items.IRON_PICKAXE, Items.COOKED_BEEF, Items.WATER_BUCKET, Items.ENDER_PEARL}[c] : null);
				if (it != null) {
					context.item(new ItemStack(it), x + 1, y + 1);
				}
			}
		}
		// 핫바 줄 강조 + 교환 표시
		int hy = gy + 3 * cell + 4;
		kr.lunaslight.mod.gui.LunaDraw.roundRectOutline(context, gx - 1, hy - 1, cols * cell + 2, cell + 2, 0, 0xFFA9D973);
		String hint = modifier.getKeyName() + " + 휠" + (numberKeys.get() ? " / 1/2/3" : "");   // 49-88차(8-12)
		int hw = LunaCompat.getTextWidth(client.font, hint);
		LunaCompat.drawHudText(context, client.font, hint, previewCenterX() - hw / 2, py + panelH + 4, 0xFFE6E8EA);
	}

	private boolean onScroll(double horizontal, double vertical) {
		if (!isEnabled() || vertical == 0 || client.player == null || client.gameMode == null
				|| kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null || !modifier.isDown(client)) {
			return false;
		}
		long now = System.nanoTime();
		if (now - lastSwapNanos < 90_000_000L) {
			return true; // 너무 빠른 연속 휠은 먹기만(패킷 폭주 방지)
		}
		lastSwapNanos = now;
		boolean down = vertical < 0;
		if (invert.get()) {
			down = !down;
		}
		perform(down);
		return true;
	}

	/** 인벤토리 줄 row(0 = 맨 위, 2 = 핫바 바로 위)의 c번째 칸 슬롯 id(PlayerScreenHandler: 9~35). */
	private static int slotId(int row, int c) {
		return 9 + row * 9 + c;
	}

	private void perform(boolean down) {
		if (client.player.containerMenu != client.player.inventoryMenu) {
			return;
		}
		int syncId = client.player.inventoryMenu.containerId;
		var inv = LunaCompat.getPlayerInventory(client.player);
		if (mode.get() == Mode.SWAP_ABOVE) {
			for (int c = 0; c < 9; c++) {
				swap(syncId, inv, 2, c);
			}
			return;
		}
		// 순환: 핫바 c칸을 축으로 세 번 교환하면 네 줄이 한 칸씩 돈다.
		//  아래: (윗줄0)→(윗줄1)→(윗줄2) 순서 = 핫바←줄2, 줄2←줄1, 줄1←줄0, 줄0←옛 핫바
		//  위  : 반대 순서 = 핫바←줄0, 줄0←줄1, 줄1←줄2, 줄2←옛 핫바
		for (int c = 0; c < 9; c++) {
			if (down) {
				swap(syncId, inv, 0, c);
				swap(syncId, inv, 1, c);
				swap(syncId, inv, 2, c);
			} else {
				swap(syncId, inv, 2, c);
				swap(syncId, inv, 1, c);
				swap(syncId, inv, 0, c);
			}
		}
	}

	/** 인벤토리 줄 row의 c칸 ↔ 핫바 c칸. 둘 다 비어 있으면 생략. */
	private void swap(int syncId, net.minecraft.world.entity.player.Inventory inv, int row, int c) {
		AutoRefillModule.resync();   // 49-305차: 자동 채우기가 옮긴 아이템을 도로 끌어오지 않게
		ItemStack a = inv.getItem(9 + row * 9 + c);
		ItemStack b = inv.getItem(c);
		if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) {
			return;
		}
		client.gameMode.handleContainerInput(syncId, slotId(row, c), c, ContainerInput.SWAP, client.player);
	}
}
