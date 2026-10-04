package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 35차: MinecraftClient#getCurrentFps()가 1.16~1.19.2에 없는 것으로 확인됨(claude/nova-mod-todo.md
 * 35차) - 리플렉션으로 존재 여부를 한 번만 확인해두고, 없는 버전에서는 프레임 간 System.nanoTime
 * 델타를 직접 누적해 1초 단위로 재계산하는 computeFallbackFps()로 자동 전환.
 */
public class FpsHudModule extends Module {

	private static final java.lang.reflect.Method GET_CURRENT_FPS = resolveGetCurrentFps();

	private static java.lang.reflect.Method resolveGetCurrentFps() {
		try {
			return kr.lunaslight.mod.util.LunaCompat.getMethodCompat(net.minecraft.client.Minecraft.class, "getCurrentFps"); // 46차
		} catch (Throwable ignored) {
			return null;
		}
	}

	private final PositionSetting position;
	private final ColorSetting textColor;
	private final BooleanSetting colorByValue;

	// 폴백: 프레임 시간 기반 FPS 계산용 (getCurrentFps()를 못 쓰게 될 경우 대신 사용)
	private int fallbackFps = 0;
	private int frameCountInWindow = 0;
	private long windowStartNanos = 0L;

	public FpsHudModule() {
		super("fps_hud", "FPS", ModuleCategory.HUD, "초당 프레임");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 4)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		colorByValue = register(new BooleanSetting("color_by_value", "값별 색", "FPS가 낮아질수록 초록에서 노랑, 빨강으로 바뀝니다.", false));
		enableHudStyle();
	}

	private int computeFallbackFps() {
		long now = System.nanoTime();
		if (windowStartNanos == 0L) {
			windowStartNanos = now;
		}
		frameCountInWindow++;
		long elapsed = now - windowStartNanos;
		if (elapsed >= 1_000_000_000L) {
			fallbackFps = (int) (frameCountInWindow * 1_000_000_000L / elapsed);
			frameCountInWindow = 0;
			windowStartNanos = now;
		}
		return fallbackFps;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		int fps;
		if (GET_CURRENT_FPS != null) {
			try {
				fps = (int) GET_CURRENT_FPS.invoke(client);
			} catch (Throwable t) {
				fps = computeFallbackFps();
			}
		} else {
			fps = computeFallbackFps();
		}

		int color = textColor.getArgb();
		if (colorByValue.get()) {
			if (fps >= 120) {
				color = LunaTheme.SUCCESS;
			} else if (fps >= 60) {
				color = LunaTheme.WARNING;
			} else {
				color = LunaTheme.DANGER;
			}
		}

		String text = "FPS: " + fps;
		int textWidth = LunaCompat.getTextWidth(client.font, text);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
		drawHudLine(context, text, x, y, color);
	}
}
