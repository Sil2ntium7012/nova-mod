package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;

/**
 * 49-201차(사용자: "망토 부드럽게 움직이게 해줘"): <b>망토</b> - 플레이어 망토의 흔들림을 부드럽게.
 *
 * <p>바닐라 망토 각도(펄럭임, 앞뒤 기울기, 옆 기울기)는 틱마다 속도에서 바로 계산돼서 걷기 시작/멈춤, 점프, 방향 전환 때
 * 한 번에 툭툭 꺾인다. 이 기능은 그 세 값을 시간에 따라 천천히 따라가게(지수 감쇠) 만들어 천처럼 늘어지며 움직이게 한다.
 * 실제 처리는 PlayerStateHook(렌더 상태를 만든 직후, 1.21.2+ / 26.x). 그보다 옛 버전은 렌더 상태가 없어 적용되지 않는다.
 */
public class CapeSmoothModule extends Module {

	private static volatile boolean on;
	private static volatile int strength = 5;

	private final IntSetting smooth = register(new IntSetting(
			"smooth", "부드러움", "클수록 망토가 천천히, 더 부드럽게 따라옵니다.", 5, 1, 10, 1));

	// 49-240차: 노바 망토(런처에서 산 망토). 흔들림 기능을 꺼도 망토는 보이게 - 값은 정적 사본으로 따로 읽는다.
	private final kr.lunaslight.mod.module.setting.BooleanSetting novaCapes = register(new kr.lunaslight.mod.module.setting.BooleanSetting(
			"nova_capes", "노바 망토", "런처에서 산 노바 망토를 바닐라 망토 자리에 보여줍니다.", true));
	private final kr.lunaslight.mod.module.setting.BooleanSetting othersCapes = register(new kr.lunaslight.mod.module.setting.BooleanSetting(
			"others_capes", "다른 사람 노바 망토", "다른 Nova 유저가 낀 노바 망토도 보여줍니다.", true));
	// 49-242차(사용자: "망토가 물결을 치게, 중력의 영향을 받게"): 마디 사슬 물결(CapePhysics). 흔들림 기능과 따로 켜고 끈다.
	private final kr.lunaslight.mod.module.setting.BooleanSetting wave = register(new kr.lunaslight.mod.module.setting.BooleanSetting(
			"wave", "물결", "망토가 마디마다 굽으며 중력에 처지고, 움직이면 물결칩니다(1.21.2 이상).", true));
	private final IntSetting waveStrength = register(new IntSetting(
			"wave_strength", "물결 세기", "클수록 물결이 크게 칩니다.", 5, 1, 10, 1));
	private static CapeSmoothModule instance;

	public static boolean waveOn() {
		CapeSmoothModule m = instance;
		return m == null || m.wave.get();
	}

	public static int waveStrength() {
		CapeSmoothModule m = instance;
		return m == null ? 5 : m.waveStrength.get();
	}

	public static boolean novaCapesShown() {
		CapeSmoothModule m = instance;
		return m == null || m.novaCapes.get();
	}

	public static boolean othersCapesShown() {
		CapeSmoothModule m = instance;
		return m == null || m.othersCapes.get();
	}

	public CapeSmoothModule() {
		super("cape_smooth", "망토", ModuleCategory.VIEW, "노바 망토와 부드러운 흔들림");
		defaultEnabled(true);
		instance = this;
	}

	@Override
	protected void onEnable() {
		on = true;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		on = isEnabled();
		strength = smooth.get();
	}

	public static boolean on() {
		return on;
	}

	/** 초당 따라가는 빠르기(클수록 빠름). 부드러움 5 → 6. */
	public static float rate() {
		return 30f / Math.max(1, strength);
	}
}
