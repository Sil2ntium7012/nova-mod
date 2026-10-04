package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.ZoomState;
import org.lwjgl.glfw.GLFW;

/**
 * 줌.
 *
 * 49-21차(사용자: "줌이 뚜두둑 끊기면서 확대돼, 부드럽게 + 줌 감도 설정"):
 *  - 끊김 원인: 진행도(progress)를 **틱(20Hz)** 에서만 갱신하고 FOV 믹스인은 프레임마다 읽어서,
 *    1초에 20단계로 계단처럼 확대됐음. 이제 목표값만 여기서 정하고 실제 보간은 ZoomState가
 *    프레임 시각 기준으로 한다(GameRendererFovMixin이 매 프레임 ZoomState.tick()).
 *  - 줌 감도: 줌 중에는 마우스 감도를 설정 비율만큼 낮추고(옵션 값 임시 변경), 떼면 원래대로.
 *    끄거나 월드에서 나가도 반드시 복원.
 */
public class ZoomModule extends Module {

	private final KeybindSetting zoomKey = register(new KeybindSetting(
			"zoom_key", "줌 키", "누르는 동안 화면을 확대합니다.", GLFW.GLFW_KEY_C));

	private final FloatSetting zoomFov = register(new FloatSetting(
			"zoom_fov", "시야", "확대 시야(FOV)입니다. 작을수록 더 확대됩니다.", 20f, 1f, 90f, 1f));

	// 49-22차: 기본 부드러움 6 → 9(사용자: "줌 속도 조금 낮추고")
	private final IntSetting smoothness = register(new IntSetting(
			"smoothness", "부드러움", "클수록 천천히 확대됩니다.", 9, 1, 20, 1));

	private final BooleanSetting instant = register(new BooleanSetting(
			"instant", "즉시 줌", "보간 없이 바로 확대합니다.", false));

	private final IntSetting sensitivity = register(new IntSetting(
			"sensitivity", "감도", "확대 중 마우스 감도(%)입니다.", 40, 5, 100, 5).unit("%"));

	// 49-22차: 줌 중 휠로 확대 정도 조절, 기본보다 더 내리면 줌 종료
	private final BooleanSetting wheel = register(new BooleanSetting(
			"wheel", "휠 조절", "확대 중 휠로 배율을 조절합니다. 기본보다 세 칸 내리면 줌이 끝납니다.", true));

	private boolean keyHeld;
	private Double savedSensitivity;

	public ZoomModule() {
		super("zoom", "줌", ModuleCategory.VIEW, "키를 누르는 동안 화면 확대");
	}

	@Override
	protected void onDisable() {
		keyHeld = false;
		ZoomState.keyHeld = false;
		ZoomState.wheelLevel = 0;
		ZoomState.wheelEnded = false;
		ZoomState.target = 0f;
		ZoomState.progress = 0f;
		restoreSensitivity();
	}

	@Override
	public void onTick() {
		boolean held = client.currentScreen == null && zoomKey.isDown(client);
		ZoomState.targetFov = zoomFov.get();
		ZoomState.instant = instant.get();
		ZoomState.wheelEnabled = wheel.get();
		// 부드러움 1~20 → 초당 수렴 속도(클수록 느림)
		ZoomState.speedPerSecond = 24f / smoothness.get();
		if (!held) {
			// 키를 떼면 휠 단계/종료 상태 초기화(다음 줌은 기본 단계부터)
			ZoomState.wheelLevel = 0;
			ZoomState.wheelEnded = false;
		}
		ZoomState.keyHeld = held;
		ZoomState.target = held && !ZoomState.wheelEnded ? 1f : 0f;

		if (held && !keyHeld) {
			Double cur = LunaCompat.getMouseSensitivity(client.options);
			if (cur != null) {
				savedSensitivity = cur;
				LunaCompat.setMouseSensitivity(client.options, cur * (sensitivity.get() / 100.0));
			}
		} else if (!held && keyHeld) {
			restoreSensitivity();
		}
		keyHeld = held;
	}

	private void restoreSensitivity() {
		if (savedSensitivity != null) {
			LunaCompat.setMouseSensitivity(client.options, savedSensitivity);
			savedSensitivity = null;
		}
	}

	private boolean pollKey(int glfwKeyCode) {
		// 49-22차: 마우스 옆버튼 코드도 지원(LunaCompat.isKeyPressed가 분기)
		return LunaCompat.isKeyPressed(client, glfwKeyCode);
	}
}
