package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.SlotDrawHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/**
 * 희귀도 테두리 - 인벤토리·핫바 칸에 등급 색을 깐다. HandledScreen이 칸을 그리기 직전(SlotDrawHook)에
 * 그리므로 인벤토리에서는 아이템과 툴팁 <b>아래</b>에 깔린다.
 *
 * <p><b>49-53차(4-33 · 4-41).</b> 두 가지를 요청대로 바꿨다.
 * <ul>
 *   <li><b>색은 이름 색으로 고정.</b> 등급 색 4개를 각각 고르게 해 뒀던 설정을 전부 없앴다 - 등급 색은
 *       마인크래프트가 아이템 이름에 쓰는 색 그대로여야 뜻이 통한다(고급 = 노랑, 희귀 = 청록, 영웅 = 보라).
 *       고를 이유가 없는 설정은 두지 않는다.</li>
 *   <li><b>모양은 아래가 차 있고 위로 갈수록 사라지는 그라데이션.</b> 예전엔 네 변을 같은 진하기로 둘러서
 *       칸이 통째로 테두리에 갇힌 느낌이었다. 이제 바닥은 또렷하고, 좌·우 기둥이 위로 가며 옅어지다
 *       사라지고, 위쪽 변은 아예 없다.</li>
 * </ul>
 *
 * <p>칠하는 곳은 <b>아이템 16×16 바깥의 1px 테두리</b>뿐이라 인벤토리든 핫바든 아이템을 가리지 않는다
 * (핫바는 HUD여서 우리가 아이템보다 나중에 그리는데, 그래도 겹치지 않는다).
 */
public class RarityOutlineModule extends Module implements SlotDrawHook.Handler {

	/** 바닥 변의 진하기. 좌·우 기둥은 여기서 시작해 위로 가며 0으로 사라진다. */
	private static final int ALPHA_BOTTOM = 0xB4;

	// 마인크래프트가 아이템 이름에 쓰는 등급 색 그대로.
	private static final int COMMON = 0xFFFFFF;
	private static final int UNCOMMON = 0xFFFF55;
	private static final int RARE = 0x55FFFF;
	private static final int EPIC = 0xFF55FF;

	private final BooleanSetting showCommon = register(new BooleanSetting(
			"show_common", "일반 등급 표시", "일반 등급 아이템에도 테두리를 그립니다.", false));

	public RarityOutlineModule() {
		super("rarity_outline", "희귀도 테두리", ModuleCategory.INVENTORY, "인벤토리/핫바 칸에 등급 색 표시");
		SlotDrawHook.register(this);
	}

	@Override
	public void beforeSlot(DrawContext ctx, Slot slot) {
		if (!isEnabled()) {
			return;
		}
		ItemStack stack = slot.getStack();
		if (stack == null || stack.isEmpty()) {
			return;
		}
		draw(ctx, slot.x, slot.y, colorFor(stack));
	}

	// ==================== 핫바 ====================

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			drawPreview(context);
			return;
		}
		if (client == null || client.player == null || client.currentScreen != null
				|| LunaCompat.isHudHidden(client)) {
			return;
		}
		int w = client.getWindow().getScaledWidth();
		int h = client.getWindow().getScaledHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		net.minecraft.entity.player.PlayerInventory inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return;
		}
		// 바닐라 renderHotbar와 같은 좌표 계산: x = 화면중앙 − 91 + 칸×20 + 2, y = 화면높이 − 19
		int left = w / 2 - 91;
		int y = h - 19;
		for (int i = 0; i < 9; i++) {
			ItemStack stack;
			try {
				stack = inv.getStack(i);
			} catch (Throwable t) {
				LunaCompat.warnOnce("rarity:hotbar", t);
				return;
			}
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			draw(context, left + i * 20 + 2, y, colorFor(stack));
		}
	}

	// ==================== 그리기 ====================

	/**
	 * 아이템 16×16 바로 바깥 테두리에 "바닥은 진하고 위로 갈수록 사라지는" 색을 깐다.
	 * 세로 기둥 둘은 {@code fillGradient} 한 번씩으로 끝내고(픽셀마다 그리지 않는다), 바닥은 1px 단색.
	 * 칸 하나에 그리기 세 번이라 핫바 아홉 칸을 다 그려도 비용이 없다.
	 */
	private static void draw(DrawContext ctx, int x, int y, int rgb) {
		if (rgb < 0) {
			return;
		}
		int left = x - 1;
		int right = x + 17;
		int top = y - 1;
		int bottom = y + 17;
		int fade = rgb;                               // 알파 0 = 투명
		int solid = rgb | (ALPHA_BOTTOM << 24);
		ctx.fillGradient(left, top, left + 1, bottom, fade, solid);        // 왼쪽 기둥
		ctx.fillGradient(right - 1, top, right, bottom, fade, solid);      // 오른쪽 기둥
		ctx.fill(left, bottom - 1, right, bottom, solid);                  // 바닥
	}

	/** 등급 → 이름 색. 표시하지 않을 등급이면 −1. */
	private int colorFor(ItemStack stack) {
		String rarity;
		try {
			Object r = stack.getRarity();
			rarity = r == null ? "COMMON" : ((Enum<?>) r).name();
		} catch (Throwable t) {
			LunaCompat.warnOnce("rarity", t);
			return -1;
		}
		return switch (rarity) {
			case "UNCOMMON" -> UNCOMMON;
			case "RARE" -> RARE;
			case "EPIC" -> EPIC;
			default -> showCommon.get() ? COMMON : -1;
		};
	}

	// ==================== 미리보기 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	/** 네 등급 칸을 실제 크기로 나란히(인벤토리에서 보이는 그대로). */
	private void drawPreview(DrawContext ctx) {
		int[] colors = {showCommon.get() ? COMMON : -1, UNCOMMON, RARE, EPIC};
		int gap = 22;
		int totalW = colors.length * gap - (gap - 18);
		int x = previewX() + (previewW() - totalW) / 2 + 1;
		int y = previewY() + (previewH() - 18) / 2 + 1;
		for (int i = 0; i < colors.length; i++) {
			int sx = x + i * gap;
			ctx.fill(sx - 1, y - 1, sx + 17, y + 17, 0x60000000); // 빈 칸 바탕(바닐라 칸 느낌)
			draw(ctx, sx, y, colors[i]);
		}
	}
}
