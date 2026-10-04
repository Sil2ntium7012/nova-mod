package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;

/**
 * 49-195차(사용자: "그래픽에서 화면 테두리 어두워지는 거 설정 끄는 거 만들기 - 비네팅"): 비네팅 끄기.
 *
 * <p>바닐라는 [화려하게] 그래픽이면 화면 가장자리를 어둡게 칠한다(밝은 곳일수록 옅고 어두운 곳일수록 진하게).
 * 켜면 그 테두리를 안 그린다. 신호기 빛 끄기처럼 설정 없이 켜고 끄기만 있다([그래픽] 설정 페이지).
 * 실제로 막는 곳은 HudElementHideMixin(InGameHud.renderVignetteOverlay / 26.x extractVignette).
 */
public class VignetteModule extends Module {

	private static volatile boolean hidden;

	public VignetteModule() {
		super("vignette", "비네팅 끄기", ModuleCategory.VIEW, "화면 가장자리 어두운 테두리 숨김");
		settingsPage(SettingsPage.GRAPHICS);
	}

	@Override
	protected void onEnable() {
		hidden = true;
	}

	@Override
	public void onTick() {
		hidden = isEnabled();
	}

	@Override
	protected void onDisable() {
		hidden = false;
	}

	/** HudElementHideMixin이 프레임마다 보는 값. */
	public static boolean isHidden() {
		return hidden;
	}
}
