package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.FreeView;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-349차(사용자: "배그처럼 자유시점도 가능하게 해줘 기본키 ALT로 해서 기능에 추가좀 - 화면 돌리는 거 3인칭 되고 좌우로 돌아보는 거"):
 * <b>자유 시점</b> - 키(기본 왼쪽 Alt)를 누르는 동안 3인칭으로 바뀌고, 마우스는 몸을 돌리지 않고 카메라만 둘러본다. 떼면 원래 시점과
 * 원래 각도로 돌아온다(0.15초 부드럽게). 움직임(WASD)은 몸이 보는 방향 그대로.
 *
 * <p>구현: {@link FreeView} 상태 + EntityTurnMixin(마우스 회전 가로채기) + CameraFreeViewMixin(카메라 각도에 오프셋). 2/3인칭 기능(FreeLookModule)과
 * 같은 방식으로 시점을 저장했다 되돌린다.
 */
public class FreeViewModule extends Module {

	private final KeybindSetting key = register(new KeybindSetting(
			"key", "둘러보기 키", "누르는 동안 몸은 그대로 두고 카메라만 둘러봅니다.", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT));
	private final BooleanSetting thirdPerson = register(new BooleanSetting(
			"third_person", "3인칭으로", "누르는 동안 뒤에서 보는 3인칭이 됩니다(떼면 원래 시점).", true));

	// 49-354차(사용자: "자유시점 시점 돌리면 그 방향에 맞게 방향키도 다르게 되게 해줘")
	private final BooleanSetting cameraMove = register(new BooleanSetting(
			"camera_move", "카메라 방향으로 이동", "둘러보는 동안 WASD가 카메라가 보는 방향 기준으로 움직입니다(끄면 몸 방향 기준).", true));

	private boolean active;
	private Object savedPerspective;

	public FreeViewModule() {
		super("free_view", "자유 시점", ModuleCategory.VIEW, "키를 누르는 동안 몸은 그대로, 카메라만 둘러보기");
	}

	@Override
	protected void onDisable() {
		stop();
	}

	@Override
	public void onTick() {
		FreeView.moveWithCamera = cameraMove.get();
		boolean down = client.player != null && client.currentScreen == null && key.isDown(client);
		if (down && !active) {
			start();
		} else if (!down && active) {
			stop();
		}
	}

	private void start() {
		active = true;
		FreeView.setActive(true);
		if (thirdPerson.get()) {
			savedPerspective = LunaCompat.getPerspective(client.options);
			LunaCompat.setPerspective(client.options, LunaCompat.thirdPersonBackPerspective());
		}
	}

	private void stop() {
		if (!active) {
			return;
		}
		active = false;
		FreeView.setActive(false);
		if (savedPerspective != null) {
			LunaCompat.setPerspective(client.options, savedPerspective);
			savedPerspective = null;
		}
	}
}
