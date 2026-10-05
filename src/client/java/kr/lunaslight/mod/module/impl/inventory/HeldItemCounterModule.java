package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import kr.lunaslight.mod.util.WindowAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

/**
 * <b>손 아이템 개수</b> - 손에 든 아이템이 인벤토리에 모두 몇 개 있는지 아이콘 옆에 띄운다.
 *
 * <p>49-157차에 [소모품 개수](key_item_count)의 한 줄로 합쳤다가, 49-179차(사용자: "전에 손에 들고 있는 아이템 수
 * 알려주는 기능 어디갔어 - 왜 아이템 개수로 되돌려놔")에 따로 된 기능으로 되살렸다. 설정 id(held_item_counter)는
 * 예전 그대로라 옛 설정 파일의 위치와 켜짐이 그대로 이어진다.
 */
public class HeldItemCounterModule extends Module {

	private static final int ICON = 16;

	private final PositionSetting position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, -140, 3)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "개수 글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	// 49-195차(사용자: "이름 아이템 개수로, 전체 합산은 기능 삭제하고 기본 설정으로"): [전체 합산] 설정을 빼고 늘 인벤토리 전체를 센다.

	public HeldItemCounterModule() {
		super("held_item_counter", "아이템 개수", ModuleCategory.HUD, "손에 든 아이템이 모두 몇 개 있는지");
		enableHudStyle();
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		ItemStack hand = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
		int total;
		if (isPreview() && (hand == null || hand.isEmpty())) {
			// 미리보기에서 손이 비었으면 예시(조약돌)
			net.minecraft.item.Item cobble = LunaCompat.itemById("minecraft:cobblestone");
			hand = cobble == null ? ItemStack.EMPTY : new ItemStack(cobble, 64);
			total = 192;
		} else {
			if (hand == null || hand.isEmpty()) {
				return;
			}
			total = countAll(hand);
		}
		if (hand.isEmpty()) {
			return;
		}
		String n = String.valueOf(total);
		int w = ICON + 3 + LunaCompat.getTextWidth(client.textRenderer, n);
		int x;
		int y;
		if (isPreviewBoxed()) {
			// 49-195차(사용자: "미리보기 안 보이고"): 설정 미리보기 상자 안 가운데에(예전엔 화면 실제 자리 - 상자 밖이라 안 보였다)
			x = previewCenterX() - w / 2;
			y = previewCenterY() - ICON / 2;
		} else {
			x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
			y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), ICON);
		}
		drawHudPanel(context, x, y, w, ICON);
		context.drawItem(hand, x, y);
		LunaCompat.drawHudText(context, client.textRenderer, n, x + ICON + 3, y + 4, textColor.getArgb());
	}

	private int countAll(ItemStack hand) {
		int total = 0;
		try {
			Object inv = LunaCompat.getPlayerInventory(client.player);
			int size = LunaCompat.invSize(inv);
			for (int i = 0; i < size; i++) {
				ItemStack s = LunaCompat.invGetStack(inv, i);
				if (s != null && !s.isEmpty() && s.getItem() == hand.getItem() && sameData(s, hand)) {
					total += s.getCount();
				}
			}
		} catch (Throwable ignored) {
			return hand.getCount();
		}
		return Math.max(total, hand.getCount());
	}

	/**
	 * 49-299차(사용자: "nbt 다르면 안 뜨게 해 주고 아이템 개수에 포함"): 서버 아이템은 같은 바닐라 아이템에 데이터(이름, 모델, 태그)만 달라
	 * 다른 아이템인 경우가 많다 - 아이템 종류뿐 아니라 데이터까지 같은 것만 센다(손에 든 것과 합쳐질 수 있는 것만).
	 * 1.20.5+ areItemsAndComponentsEqual, 1.17~1.20.4 canCombine, 그 전 areTagsEqual(전부 static (ItemStack, ItemStack)).
	 */
	private static java.lang.reflect.Method sameMethod;
	private static boolean sameResolved;

	private static boolean sameData(ItemStack a, ItemStack b) {
		try {
			if (!sameResolved) {
				sameResolved = true;
				for (String n : new String[]{"areItemsAndComponentsEqual", "canCombine", "areTagsEqual"}) {
					java.lang.reflect.Method m = LunaCompat.findMethod(ItemStack.class, n, ItemStack.class, ItemStack.class);
					if (m != null && (m.getReturnType() == boolean.class) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
						sameMethod = m;
						break;
					}
				}
			}
			return sameMethod == null || Boolean.TRUE.equals(sameMethod.invoke(null, a, b));
		} catch (Throwable t) {
			return true;
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
