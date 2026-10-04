package kr.lunaslight.mod.module.impl.combat;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffects;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 49-23차 전투: "지금 때리면 크리티컬인지". 바닐라 PlayerEntity#attack의 크리티컬 조건 그대로 -
 * 떨어지는 중(fallDistance>0, 공중) + 사다리/물/탈것/질주 아님 + 실명 아님 + 공격 쿨타임 90% 이상.
 * 조건이 맞으면 크로스헤어 아래에 표시(마름모/글자/괄호), 쿨타임만 덜 찼으면 옵션으로 흐리게.
 */
public class CritIndicatorModule extends Module {

	public enum Style {
		DIAMOND("마름모"),
		TEXT("글자"),
		BRACKETS("괄호");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Style> style = register(new EnumSetting<>(
			"style", "모양", "표시 모양입니다.", Style.DIAMOND, Style.class).style());

	private final ColorSetting color = register(new ColorSetting(
			"color", "색", "표시 색입니다.", 0xFFFF5C5C));

	private final IntSetting offsetY = register(new IntSetting(
			"offset_y", "간격", "크로스헤어에서 아래로 떨어진 거리(픽셀)입니다.", 10, 4, 40, 1).unit("px"));

	private final BooleanSetting dimWhenCooling = register(new BooleanSetting(
			"dim_when_cooling", "쿨타임 표시", "공중이지만 공격 쿨타임이 덜 찼을 때는 반투명으로 표시합니다.", true));

	private static Field fallDistanceField;
	private static Method isOnGroundMethod;
	private static Field onGroundField;
	private static boolean resolved;

	public CritIndicatorModule() {
		super("crit_indicator", "크리티컬", ModuleCategory.COMBAT, "크리티컬 가능 여부를 크로스헤어 아래에 표시");
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	private void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;
		Class<?> pc = client.player.getClass();
		fallDistanceField = LunaCompat.findField(pc, "fallDistance");
		isOnGroundMethod = LunaCompat.findNoArgMethod(pc, "isOnGround");
		if (isOnGroundMethod == null) {
			onGroundField = LunaCompat.findField(pc, "onGround");
		}
	}

	/** 0 = 아님, 1 = 공중이지만 쿨타임 부족, 2 = 지금 치면 크리티컬. */
	private int state() {
		try {
			resolve();
			var p = client.player;
			double fall = 0;
			if (fallDistanceField != null) {
				Object v = fallDistanceField.get(p);
				if (v instanceof Number n) {
					fall = n.doubleValue();
				}
			}
			boolean onGround = true;
			if (isOnGroundMethod != null) {
				onGround = Boolean.TRUE.equals(isOnGroundMethod.invoke(p));
			} else if (onGroundField != null) {
				onGround = Boolean.TRUE.equals(onGroundField.get(p));
			}
			if (fall <= 0 || onGround || p.isClimbing() || p.isTouchingWater() || p.hasVehicle() || p.isSprinting()) {
				return 0;
			}
			try {
				if (p.hasStatusEffect(StatusEffects.BLINDNESS)) {
					return 0;
				}
			} catch (Throwable ignored) {
			}
			return p.getAttackCooldownProgress(0.5f) > 0.9f ? 2 : 1;
		} catch (Throwable t) {
			LunaCompat.warnOnce("critIndicator", t);
			return 0;
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		int st;
		int cx, cy;
		if (isPreview()) {
			st = 2;
			cx = previewCenterX();
			cy = previewCenterY() - offsetY.get() / 2;
			// 미리보기: 십자 크로스헤어 흉내
			context.fill(cx - 4, cy, cx + 5, cy + 1, 0xFFFFFFFF);
			context.fill(cx, cy - 4, cx + 1, cy + 5, 0xFFFFFFFF);
		} else {
			if (client.player == null || LunaCompat.isHudHidden(client)) {
				return;
			}
			st = state();
			if (st == 0 || (st == 1 && !dimWhenCooling.get())) {
				return;
			}
			// 49-195차(사용자: "크리티컬 오른쪽으로 한 칸 밀림"): 화면 폭/2가 아니라 바닐라 조준점의 가운데 픽셀
			// ((폭-15)/2+7 - 폭이 짝수면 폭/2보다 1 작다)에 맞춘다. 크로스헤어 기능과 같은 기준.
			cx = LunaCompat.crosshairCenterX(client);
			cy = LunaCompat.crosshairCenterY(client);
		}
		int argb = color.getArgb();
		if (st == 1) {
			argb = (argb & 0x00FFFFFF) | (0x55 << 24);
		}
		int y = cy + offsetY.get();
		switch (style.get()) {
			case DIAMOND -> {
				// 5×5 마름모(중심 픽셀 = cx, y)
				context.fill(cx, y - 2, cx + 1, y - 1, argb);
				context.fill(cx - 1, y - 1, cx + 2, y, argb);
				context.fill(cx - 2, y, cx + 3, y + 1, argb);
				context.fill(cx - 1, y + 1, cx + 2, y + 2, argb);
				context.fill(cx, y + 2, cx + 1, y + 3, argb);
			}
			case TEXT -> {
				String t = "크리티컬";
				int w = LunaCompat.getTextWidth(client.textRenderer, t);
				LunaCompat.drawHudText(context, client.textRenderer, t, cx - w / 2 + 1, y - 3, argb);
			}
			case BRACKETS -> {
				// 크로스헤어 양옆 [ ]
				int d = offsetY.get();
				context.fill(cx - d, cy - 4, cx - d + 1, cy + 5, argb);
				context.fill(cx - d, cy - 4, cx - d + 3, cy - 3, argb);
				context.fill(cx - d, cy + 4, cx - d + 3, cy + 5, argb);
				context.fill(cx + d, cy - 4, cx + d + 1, cy + 5, argb);
				context.fill(cx + d - 2, cy - 4, cx + d + 1, cy - 3, argb);
				context.fill(cx + d - 2, cy + 4, cx + d + 1, cy + 5, argb);
			}
		}
	}
}
