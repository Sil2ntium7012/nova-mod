package kr.lunaslight.mod.module.impl.combat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.lang.reflect.Method;

/**
 * 49-23차 전투: 방패 쿨타임. 도끼에 맞아 방패가 무력화되면(5초) 방패 아이콘 + 남은 시간 막대/초를 표시.
 * ItemCooldownManager#getCooldownProgress(Item,float)(≤1.21.1) / (ItemStack,float)(1.21.2+)를 리플렉션으로.
 */
public class ShieldCooldownModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 154)));

	private final ColorSetting barColor = register(new ColorSetting(
			"bar_color", "게이지 색", "쿨타임 게이지의 색입니다.", 0xFFFF6B6B));

	private final BooleanSetting showSeconds = register(new BooleanSetting(
			"show_seconds", "방패 쿨타임", "게이지 옆에 남은 시간을 초로 표시합니다.", true));

	private static Method progressMethod;
	private static boolean progressResolved;
	private static boolean progressTakesStack;
	private static ItemStack shieldStack;

	public ShieldCooldownModule() {
		super("shield_cooldown", "방패", ModuleCategory.COMBAT, "무력화된 방패의 남은 쿨타임");
		enableHudStyle();
	}

	/** 남은 쿨타임 비율(0 = 없음, 1 = 방금 무력화). */
	private float progress() {
		try {
			Object cm = client.player.getCooldowns();
			if (cm == null) {
				return 0f;
			}
			if (!progressResolved) {
				progressResolved = true;
				for (Method m : cm.getClass().getMethods()) {
					if (m.getParameterCount() == 2 && LunaCompat.nameMatches(cm.getClass(), "getCooldownProgress", m.getName())
							&& m.getParameterTypes()[1] == float.class) {
						progressMethod = m;
						progressTakesStack = m.getParameterTypes()[0] == ItemStack.class;
						break;
					}
				}
				shieldStack = new ItemStack(Items.SHIELD);
			}
			if (progressMethod == null) {
				return 0f;
			}
			Object r = progressMethod.invoke(cm, progressTakesStack ? shieldStack : Items.SHIELD, 0f);
			return r instanceof Number n ? n.floatValue() : 0f;
		} catch (Throwable t) {
			LunaCompat.warnOnce("shieldCooldown", t);
			return 0f;
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		float p;
		if (isPreview()) {
			p = 0.62f;
		} else {
			if (client.player == null || LunaCompat.isHudHidden(client)) {
				return;
			}
			p = progress();
			if (p <= 0f) {
				return;
			}
		}
		if (shieldStack == null) {
			shieldStack = new ItemStack(Items.SHIELD);
		}
		int barW = 50;
		String sec = String.format(java.util.Locale.ROOT, "%.1f초", p * 5f);
		int secW = showSeconds.get() ? LunaCompat.getTextWidth(client.font, sec) + 4 : 0;
		int w = 16 + 4 + barW + secW;
		int h = 16;
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), h);
		drawHudPanel(context, x, y, w, h);
		context.item(shieldStack, x, y);
		int bx = x + 20;
		int by = y + 5;
		context.fill(bx, by, bx + barW, by + 6, 0x66000000);
		context.fill(bx, by, bx + Math.round(barW * p), by + 6, barColor.getArgb());
		if (showSeconds.get()) {
			LunaCompat.drawHudText(context, client.font, sec, bx + barW + 4, y + Math.round(8f - LunaCompat.textVisualCenter()), 0xFFFFFFFF);   // 아이콘(16px) 세로 중심
		}
	}
}
