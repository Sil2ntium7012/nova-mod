package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-17차 수정(사용자: "가동시간이 켜지기 전부터 타이머가 돌아가야 함"):
 * 예전엔 모듈을 켠 시점부터 틱을 세서, 도중에 켜면 00:00부터 다시 시작했다.
 * 이제 **JVM 가동 시간**(ManagementFactory.getRuntimeMXBean().getUptime())을 읽는다 - 게임을
 * 실행한 순간부터의 시간이라 모듈을 언제 켜든 항상 맞는다. RuntimeMXBean을 못 쓰는 환경에서는
 * 클래스 로드 시각 기준으로 폴백.
 */
public class UptimeHudModule extends Module {

	private final PositionSetting position;
	private final ColorSetting textColor;

	/** RuntimeMXBean 실패 시 폴백 기준(모드 클래스 로드 시각 ≒ 게임 시작). */
	private static final long FALLBACK_START = System.currentTimeMillis();
	private static java.lang.management.RuntimeMXBean runtimeBean;
	private static boolean runtimeBeanResolved;

	/** 게임을 실행한 뒤 흐른 시간(초). */
	private static long uptimeSeconds() {
		if (!runtimeBeanResolved) {
			runtimeBeanResolved = true;
			try {
				runtimeBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
			} catch (Throwable ignored) {
				runtimeBean = null;
			}
		}
		if (runtimeBean != null) {
			try {
				return runtimeBean.getUptime() / 1000L;
			} catch (Throwable ignored) {
				runtimeBean = null;
			}
		}
		return (System.currentTimeMillis() - FALLBACK_START) / 1000L;
	}

	public UptimeHudModule() {
		super("uptime_hud", "플레이타임", ModuleCategory.HUD, "게임을 켠 뒤 지난 시간");   // 49-151차: 가동 시간 → 플레이타임
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 72)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		enableHudStyle();
	}

	private String formatDuration(long totalSeconds) {
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%02d:%02d", minutes, seconds);
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		String text = "플레이타임: " + formatDuration(uptimeSeconds());

		int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
