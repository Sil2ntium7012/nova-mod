package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 시점 유지(F5 홀드) - 키를 누르는 동안만 3인칭, 떼면 원래 시점.
 * 49-21차: "2인칭"(앞에서 보는 3인칭, Perspective.THIRD_PERSON_FRONT) 선택 추가.
 * 49-22차: 2인칭/3인칭을 **각각 키로 지정**(사용자 요청) - 시점 선택 설정은 제거. 두 키 중 나중에
 * 누른 쪽이 우선, 둘 다 떼면 원래 시점으로 복귀. 마우스 옆버튼도 지정 가능(LunaCompat.isKeyPressed).
 * Perspective 상수/옵션 접근은 버전마다 패키지·API가 달라 LunaCompat 리플렉션.
 */
public class FreeLookModule extends Module {

	private final KeybindSetting backKey = register(new KeybindSetting(
			"hold_key", "3인칭 키", "누르는 동안 뒤에서 보는 3인칭이 됩니다.", -1));

	private final KeybindSetting frontKey = register(new KeybindSetting(
			"front_key", "2인칭 키", "누르는 동안 앞에서 보는 2인칭이 됩니다.", -1));

	/** 49-32차(사용자: "꾹 눌러도 그 방향 안 보게, 딱 처음 눌렀을 때만"). */
	public enum Mode {
		TOGGLE("토글"),
		HOLD("홀드");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Mode> mode = register(new EnumSetting<>(
			"mode", "방식", "한 번 누르기는 눌렀다 뗄 때까지 시점이 유지되지 않고, 누를 때마다 켜고 꺼집니다.",
			Mode.TOGGLE, Mode.class));

	private boolean wasHeld;
	private boolean toggledFront;
	private boolean toggledBack;
	private boolean frontActive;
	private Object savedPerspective;
	private boolean lastBackDown;
	private boolean lastFrontDown;

	public FreeLookModule() {
		// 49-195차(사용자: "시점 유지는 2/3인칭으로 바꾸기"): 이름만 바꿨다(id free_look 그대로).
		super("free_look", "2/3인칭", ModuleCategory.VIEW, "키를 누르는 동안 3인칭 | 2인칭");
	}

	@Override
	protected void onDisable() {
		restoreIfNeeded();
		wasHeld = false;
		toggledBack = false;
		toggledFront = false;
	}

	@Override
	public void onTick() {
		boolean backDown = kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && backKey.isDown(client);
		boolean frontDown = kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && frontKey.isDown(client);
		boolean back = backDown;
		boolean front = frontDown;

		if (mode.get() == Mode.TOGGLE) {
			// 누르는 순간(엣지)에만 켜고 끈다 - 계속 누르고 있어도 상태가 그대로 유지된다.
			if (backDown && !lastBackDown) {
				toggledBack = !toggledBack;
				toggledFront = false;
			}
			if (frontDown && !lastFrontDown) {
				toggledFront = !toggledFront;
				toggledBack = false;
			}
			back = toggledBack;
			front = toggledFront;
		} else {
			toggledBack = false;
			toggledFront = false;
		}
		lastBackDown = backDown;
		lastFrontDown = frontDown;

		boolean held = back || front;
		// 둘 다 눌렸으면 이미 활성인 쪽 유지, 아니면 새로 눌린 쪽
		boolean wantFront = front && (!back || (wasHeld && frontActive));
		if (held && (!wasHeld || wantFront != frontActive)) {
			if (!wasHeld) {
				savedPerspective = LunaCompat.getPerspective(client.options);
			}
			Object target = wantFront
					? LunaCompat.thirdPersonFrontPerspective()
					: LunaCompat.thirdPersonBackPerspective();
			LunaCompat.setPerspective(client.options, target);
			frontActive = wantFront;
		} else if (!held && wasHeld) {
			restoreIfNeeded();
		}
		wasHeld = held;
	}

	private void restoreIfNeeded() {
		if (savedPerspective == null) {
			return;
		}
		LunaCompat.setPerspective(client.options, savedPerspective);
		savedPerspective = null;
	}
}
