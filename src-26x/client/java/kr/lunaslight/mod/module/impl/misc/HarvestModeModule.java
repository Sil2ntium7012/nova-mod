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
		// 씨앗 한 세트만
		if (seedLimit.get() && --throwCooldown <= 0 && LunaCompat.screenOf(client) == null
				&& client.player.containerMenu == client.player.inventoryMenu) {
			Map<String, int[]> keep = new HashMap<>();   // 씨앗 id → {남길 칸, 개수}
			for (int i = 0; i < 36; i++) {
				ItemStack s = inv.getItem(i);
				if (!isSeed(s)) {
					continue;
				}
				String id = String.valueOf(LunaCompat.getItemId(s.getItem()));
				int[] k = keep.get(id);
				if (k == null) {
					keep.put(id, new int[]{i, s.getCount()});
					continue;
				}
				// 더 많은 칸(같으면 핫바)을 남기고 다른 칸 하나를 버린다 - 한 틱에 하나씩
				int drop;
				if (s.getCount() > k[1] || (s.getCount() == k[1] && i < 9 && k[0] >= 9)) {
					drop = k[0];
					k[0] = i;
					k[1] = s.getCount();
				} else {
					drop = i;
				}
				int menuSlot = drop < 9 ? 36 + drop : drop;
				client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, client.player);
				throwCooldown = 3;
				break;
			}
		}
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
