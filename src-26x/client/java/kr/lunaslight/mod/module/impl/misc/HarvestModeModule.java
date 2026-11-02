package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.BackgroundTick;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 49-488차(사용자: "작물 캐기 모드 기능에 넣어서 작물 이외의 것들은 안 캐지게, 공격은 되게. 작물 캐기 모드면 비트랑 밀 씨앗 같은 거 각각
 * 한 세트만 들어오게, 인벤 다 차면 알림 오고 화면에 타이틀"): <b>작물 캐기 모드</b>.
 * <ul>
 *   <li>켜져 있으면 작물(밀, 당근, 감자, 비트(너굴 벼), 네더 사마귀(너굴 고추), 호박, 수박, 사탕수수, 코코아, 달콤한 열매 …)만 캔다 - 다른 블록은
 *       때려도 안 부서진다(HarvestModeMixin이 블록 부수기 시작을 막음). 몹, 동물 공격은 그대로.</li>
 *   <li>[씨앗 한 세트만]: 씨앗(이름이 _seeds인 아이템, 피처 꼬투리)은 종류마다 한 칸만 남기고 나머지 칸은 버린다(Ctrl+Q와 같음).</li>
 *   <li>[가득 차면 알림]: 인벤토리(36칸)에 빈 칸이 없어지면 소리 + 화면 가운데 타이틀, 다시 빈 칸이 생기면 다음에 또 알린다.</li>
 *   <li>[켜기/끄기 키]: 기능이 꺼져 있어도 이 키로 켜고 끈다(BackgroundTick).</li>
 * </ul>
 */
public class HarvestModeModule extends Module implements BackgroundTick {

	public static volatile boolean on;

	private final KeybindSetting toggleKey = register(new KeybindSetting(
			"toggle_key", "켜기/끄기 키", "누르면 작물 캐기 모드를 켜고 끕니다.", -1));
	private final BooleanSetting seedLimit = register(new BooleanSetting(
			"seed_limit", "씨앗 한 세트만", "씨앗은 종류마다 한 칸만 남기고 나머지 칸은 버립니다.", true));
	private final BooleanSetting fullAlert = register(new BooleanSetting(
			"full_alert", "가득 차면 알림", "인벤토리가 가득 차면 소리와 화면 가운데 글씨로 알립니다.", true));
	// 49-500차(사용자 사진: 벼 모종 192개 - "씨앗 한 세트 넘게 들어오잖아, 자동 줍기 있어서 자동으로 버려야 해. 키 누르면 씨앗 한 곳 제외 다 버리고
	// 도구랑 기타 아이템 자리를 제외하고 남은 칸 모두 벼나 밀로 채우는 시스템" + "인벤에 있는 거 이용해서"):
	//  - 버리기: 바닥에 던지면 서 있는 자리에서 2초 뒤 다시 주워져서 끝이 없었다. 너굴마을이면 /쓰레기통을 열어 넘친 씨앗을 넣고 닫는다
	//    (쓰레기통이 안 열리면 던진다). 캐는 중(공격 키 누름)에는 창이 끼어들지 않게 손을 뗐을 때나 빈칸이 1칸 이하일 때.
	//  - 정리 키: 씨앗은 한 칸(가장 많은 칸)만 남기고 버린 뒤, 빈칸마다 인벤의 벼나 밀을 하나씩 나눠 넣는다 - 빈칸이 없으니 자동 줍기로
	//    씨앗이 새 칸을 못 차지하고, 벼와 밀은 그 칸들에 쌓인다.
	private final KeybindSetting arrangeKey = register(new KeybindSetting(
			"arrange_key", "정리 키", "누르면 씨앗은 한 칸만 남기고 버린 뒤, 남은 빈칸을 인벤의 벼나 밀로 하나씩 채웁니다.", -1));
	private final BooleanSetting useTrash = register(new BooleanSetting(
			"use_trash", "너굴 쓰레기통으로 버리기", "너굴마을에서는 바닥에 던지지 않고 /쓰레기통에 넣어 없앱니다(던지면 다시 주워짐).", true));

	private boolean keyWas;
	private long fullShownAt;
	private static final long SHOW_MS = 2500;
	private boolean fullWarned;
	private int throwCooldown;

	public HarvestModeModule() {
		super("harvest_mode", "작물 캐기 모드", ModuleCategory.FEATURE, "작물만 캐지고 공격은 그대로 | 씨앗 한 세트 | 가득 차면 알림");
	}

	@Override
	protected void onEnable() {
		on = true;
		fullWarned = false;
		LunaCompat.sendActionBar(client, "§a작물 캐기 모드 켜짐 §7(작물만 캐져요)");
	}

	@Override
	protected void onDisable() {
		on = false;
		LunaCompat.sendActionBar(client, "§7작물 캐기 모드 꺼짐");
	}

	@Override
	public void backgroundTick() {
		on = false;
		toggleByKey();
		arrangeByKey();
		trashTick();
	}

	private void toggleByKey() {
		boolean down = client.player != null && LunaCompat.screenOf(client) == null && toggleKey.isBound() && toggleKey.isDown(client);
		if (down && !keyWas) {
			setEnabled(!isEnabled());
		}
		keyWas = down;
	}

	@Override
	public void onTick() {
		on = true;
		toggleByKey();
		arrangeByKey();
		trashTick();
		if (!isEnabled() || client.player == null || client.gameMode == null) {
			return;
		}
		var inv = client.player.getInventory();
		// 가득 참
		boolean full = true;
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).isEmpty()) {
				full = false;
				break;
			}
		}
		if (full && !fullWarned && fullAlert.get()) {
			fullWarned = true;
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 0.8f);
			fullShownAt = System.currentTimeMillis();   // 49-489차: 26.2부터 Gui.setTitle이 없어 직접 그린다(onHudRender)
		} else if (!full) {
			fullWarned = false;
		}
		// 씨앗 한 세트만(종류마다 한 칸)
		boolean attacking = client.options.keyAttack.isDown();
		attackIdle = attacking ? 0 : attackIdle + 1;
		if (seedLimit.get() && trashPhase == 0 && LunaCompat.screenOf(client) == null
				&& client.player.containerMenu == client.player.inventoryMenu && --throwCooldown <= 0) {
			java.util.List<Integer> extra = extraSeeds(false);
			if (!extra.isEmpty()) {
				if (trashOk()) {
					int free = 0;
					for (int i = 0; i < 36; i++) {
						if (inv.getItem(i).isEmpty()) {
							free++;
						}
					}
					// 캐는 중엔 창을 안 띄운다 - 손을 1초 떼거나 빈칸이 거의 없을 때
					if ((attackIdle >= 20 || free <= 1) && System.currentTimeMillis() - lastTrashAt > 5000) {
						discard(extra);
					}
				} else {
					discard(extra.subList(0, 1));   // 던지기는 한 틱에 한 칸씩
					throwCooldown = 3;
				}
			}
		}
	}

	private int attackIdle;

	// ==================== 49-500차: 버리기(쓰레기통/던지기) + 정리 키 ====================

	private boolean arrangeWas;
	private int trashPhase;          // 0 없음, 1 쓰레기통 열리기 기다림, 2 넣었음(다음 틱에 닫기)
	private long trashSentAt;
	private long lastTrashAt;
	private Object screenBeforeTrash;
	private final java.util.List<Integer> trashSlots = new java.util.ArrayList<>();
	private final java.util.Map<Integer, net.minecraft.world.item.Item> trashItems = new java.util.HashMap<>();
	private boolean fillPending;

	private boolean trashOk() {
		return useTrash.get() && kr.lunaslight.mod.util.NeogulData.onNeogul(client);
	}

	private void arrangeByKey() {
		boolean down = client.player != null && LunaCompat.screenOf(client) == null && arrangeKey.isBound() && arrangeKey.isDown(client);
		if (down && !arrangeWas && trashPhase == 0) {
			java.util.List<Integer> extra = extraSeeds(true);
			fillPending = true;
			if (!extra.isEmpty()) {
				discard(extra);
			}
		}
		arrangeWas = down;
		if (fillPending && trashPhase == 0 && client.player != null && LunaCompat.screenOf(client) == null) {
			fillPending = false;
			fillEmpty();
		}
	}

	/** 버릴 씨앗 칸(인벤 0~35). oneTotal이면 씨앗 전체에서 한 칸만, 아니면 종류마다 한 칸 남긴다(많은 칸, 같으면 핫바). */
	private java.util.List<Integer> extraSeeds(boolean oneTotal) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		if (client.player == null) {
			return out;
		}
		var inv = client.player.getInventory();
		Map<String, Integer> keep = new HashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (!isSeed(s)) {
				continue;
			}
			String id = oneTotal ? "*" : String.valueOf(LunaCompat.getItemId(s.getItem()));
			Integer k = keep.get(id);
			if (k == null || better(inv.getItem(i), i, inv.getItem(k), k)) {
				keep.put(id, i);
			}
		}
		for (int i = 0; i < 36; i++) {
			if (isSeed(inv.getItem(i)) && !keep.containsValue(i)) {
				out.add(i);
			}
		}
		return out;
	}

	private static boolean better(ItemStack a, int ai, ItemStack b, int bi) {
		return a.getCount() > b.getCount() || (a.getCount() == b.getCount() && ai < 9 && bi >= 9);
	}

	/** 너굴마을이면 /쓰레기통에, 아니면 바닥에 던진다. */
	private void discard(java.util.List<Integer> slots) {
		if (slots.isEmpty() || client.player == null || client.gameMode == null) {
			return;
		}
		if (trashOk()) {
			trashSlots.clear();
			trashItems.clear();
			for (int i : slots) {
				trashSlots.add(i);
				trashItems.put(i, client.player.getInventory().getItem(i).getItem());
			}
			trashPhase = 1;
			trashSentAt = System.currentTimeMillis();
			lastTrashAt = trashSentAt;
			screenBeforeTrash = LunaCompat.screenOf(client);
			LunaCompat.sendChatOrCommand(client, "/쓰레기통");
			return;
		}
		for (int i : slots) {
			throwSlot(i);
		}
	}

	private void throwSlot(int i) {
		if (client.player.containerMenu != client.player.inventoryMenu) {
			return;
		}
		int menuSlot = i < 9 ? 36 + i : i;
		client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, client.player);
	}

	/** 쓰레기통이 열리면 넘친 씨앗을 Shift 클릭으로 넣고, 다음 틱에 닫는다. 3초 안에 안 열리면 던진다. */
	private void trashTick() {
		if (trashPhase == 0 || client.player == null || client.gameMode == null) {
			return;
		}
		Object screen = LunaCompat.screenOf(client);
		if (trashPhase == 2) {
			trashPhase = 0;
			if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
				client.player.closeContainer();
			}
			return;
		}
		if (screen != screenBeforeTrash && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> hs
				&& !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
			var menu = hs.getMenu();
			var inv = client.player.getInventory();
			int moved = 0;
			for (int idx : trashSlots) {
				ItemStack now = inv.getItem(idx);
				if (now.isEmpty() || now.getItem() != trashItems.get(idx) || !isSeed(now)) {
					continue;   // 그새 바뀐 칸은 건드리지 않는다
				}
				for (int m = 0; m < menu.slots.size(); m++) {
					var sl = menu.slots.get(m);
					if (sl.container == inv && sl.getContainerSlot() == idx) {
						client.gameMode.handleContainerInput(menu.containerId, m, 0, ContainerInput.QUICK_MOVE, client.player);
						moved++;
						break;
					}
				}
			}
			if (moved > 0) {
				LunaCompat.sendActionBar(client, "§7넘친 씨앗 " + moved + "칸을 쓰레기통에 버렸어요");
			}
			trashPhase = 2;
			return;
		}
		if (System.currentTimeMillis() - trashSentAt > 3000) {
			// 쓰레기통이 안 열렸다 - 던진다
			trashPhase = 0;
			if (screen == null) {
				for (int idx : trashSlots) {
					ItemStack now = client.player.getInventory().getItem(idx);
					if (!now.isEmpty() && now.getItem() == trashItems.get(idx)) {
						throwSlot(idx);
					}
				}
			}
		}
	}

	/** 빈칸마다 벼나 밀을 하나씩(남은 씨앗 종류에 맞는 작물 우선). */
	private void fillEmpty() {
		if (client.player == null || client.gameMode == null || client.player.containerMenu != client.player.inventoryMenu) {
			return;
		}
		var inv = client.player.getInventory();
		var menu = client.player.inventoryMenu;
		String seedId = null;
		for (int i = 0; i < 36; i++) {
			if (isSeed(inv.getItem(i))) {
				seedId = String.valueOf(LunaCompat.getItemId(inv.getItem(i).getItem()));
				break;
			}
		}
		String want = seedId != null && seedId.endsWith("wheat_seeds") ? "minecraft:wheat" : "minecraft:beetroot";
		String other = want.equals("minecraft:wheat") ? "minecraft:beetroot" : "minecraft:wheat";
		int have = countOf(want);
		if (have == 0) {
			want = other;
			have = countOf(want);
		}
		if (have == 0) {
			LunaCompat.sendActionBar(client, "§7인벤에 벼나 밀이 없어요");
			return;
		}
		java.util.List<Integer> empty = new java.util.ArrayList<>();
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).isEmpty()) {
				empty.add(i);
			}
		}
		if (empty.isEmpty()) {
			LunaCompat.sendActionBar(client, "§7빈칸이 없어요");
			return;
		}
		int filled = 0;
		int e = 0;
		for (int src = 0; src < 36 && e < empty.size(); src++) {
			ItemStack st = inv.getItem(src);
			if (st.isEmpty() || !want.equals(String.valueOf(LunaCompat.getItemId(st.getItem())))) {
				continue;
			}
			int srcMenu = src < 9 ? 36 + src : src;
			client.gameMode.handleContainerInput(menu.containerId, srcMenu, 0, ContainerInput.PICKUP, client.player);
			// 한 칸에 하나씩(우클릭 = 하나 놓기). 원래 칸에도 하나는 남긴다.
			while (e < empty.size() && menu.getCarried().getCount() > 1) {
				int dst = empty.get(e++);
				client.gameMode.handleContainerInput(menu.containerId, dst < 9 ? 36 + dst : dst, 1, ContainerInput.PICKUP, client.player);
				filled++;
			}
			client.gameMode.handleContainerInput(menu.containerId, srcMenu, 0, ContainerInput.PICKUP, client.player);
		}
		if (!menu.getCarried().isEmpty()) {
			// 혹시 손에 남았으면 빈칸이나 원래 자리로
			for (int i = 0; i < 36 && !menu.getCarried().isEmpty(); i++) {
				ItemStack st = inv.getItem(i);
				if (st.isEmpty() || ItemStack.isSameItemSameComponents(st, menu.getCarried())) {
					client.gameMode.handleContainerInput(menu.containerId, i < 9 ? 36 + i : i, 0, ContainerInput.PICKUP, client.player);
				}
			}
		}
		LunaCompat.sendActionBar(client, "§a빈칸 " + filled + "칸을 " + (want.endsWith("wheat") ? "밀" : "벼") + "로 채웠어요");
	}

	private int countOf(String id) {
		int n = 0;
		var inv = client.player.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && id.equals(String.valueOf(LunaCompat.getItemId(st.getItem())))) {
				n += st.getCount();
			}
		}
		return n;
	}

	@Override
	public void onHudRender(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker tickCounter) {
		long now = System.currentTimeMillis();
		boolean preview = isPreview();
		if (!preview && (fullShownAt == 0 || now - fullShownAt > SHOW_MS)) {
			return;
		}
		long age = preview ? 1000 : now - fullShownAt;
		float a = age < 200 ? age / 200f : age > SHOW_MS - 600 ? Math.max(0f, (SHOW_MS - age) / 600f) : 1f;
		if (a <= 0.02f) {
			return;
		}
		int alpha = Math.round(255 * a) << 24;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int cy = sh / 2 - 40;
		drawScaled(ctx, "인벤토리가 가득 찼어요", sw / 2, cy, 3f, alpha | 0xFF5555);
		drawScaled(ctx, "작물을 더 담을 수 없어요", sw / 2, cy + 32, 1.5f, alpha | 0xDDDDDD);
	}

	private void drawScaled(net.minecraft.client.gui.GuiGraphicsExtractor ctx, String s, int cx, int y, float scale, int argb) {
		int w = LunaCompat.getTextWidth(client.font, s);
		if (!LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.drawHudText(ctx, client.font, s, cx - w / 2, y, argb);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, cx - w * scale / 2f, y);
			LunaCompat.guiScale(ctx, scale, scale);
			LunaCompat.drawHudText(ctx, client.font, s, 0, 0, argb);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private static boolean isSeed(ItemStack s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		String id = String.valueOf(LunaCompat.getItemId(s.getItem()));
		return id.endsWith("_seeds") || id.endsWith("pitcher_pod");
	}

	/** HarvestModeMixin: 켜져 있을 때 이 블록을 부숴도 되나(작물만). */
	public static boolean breakable(BlockState st) {
		if (st == null) {
			return false;
		}
		Block b = st.getBlock();
		return b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof SugarCaneBlock || b instanceof CocoaBlock
			|| b instanceof SweetBerryBushBlock || b == Blocks.PUMPKIN || b == Blocks.MELON;
	}
}
