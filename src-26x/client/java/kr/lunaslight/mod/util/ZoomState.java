package kr.lunaslight.mod.util;

/**
 * 48차: 줌 모듈(ZoomModule)이 갱신하고 GameRendererFovMixin이 매 프레임 읽는 정적 상태.
 * 모듈 클래스를 mixin에서 직접 참조하지 않게 분리(src/ 와 src-26x/ 양쪽 트리에 동일 파일).
 *
 * 49-21차: 보간을 여기(프레임 시각 기준)로 옮김 - 틱(20Hz)에서 보간하면 20단계 계단으로 확대돼
 * "뚜두둑 끊기는" 원인이었음. 믹스인이 프레임마다 tick()을 불러 target으로 지수 수렴.
 */
public final class ZoomState {
	private ZoomState() {
	}

	public static volatile float progress = 0f;   // 0 = 줌 없음, 1 = 완전 줌(보간된 현재값)
	public static volatile float target = 0f;     // 모듈이 정하는 목표(키 눌림 = 1)
	public static volatile float targetFov = 20f;
	public static volatile float speedPerSecond = 4f; // 클수록 빨리 수렴
	public static volatile boolean instant = false;
	private static long lastNanos = 0;

	/**
	 * 49-16차: 이번 프레임에 실제로 적용된 세로 FOV(도). GameRendererFovMixin이 매 프레임 기록하고
	 * 크로스헤어 아웃라인의 화면 좌표 투영이 읽는다(줌/달리기 FOV 보정까지 반영된 값이라
	 * 옵션값을 그냥 쓰는 것보다 정확). 아직 한 번도 기록되지 않았으면 0.
	 */
	public static volatile double lastFov = 0;

	// ==================== 49-22차: 휠 줌 ====================
	// 줌 키를 누른 채 휠 위 = 더 확대(단계당 ×0.8), 휠 아래 = 덜 확대. 처음(0단계)보다 더 내리면 줌이 끝난다
	// (사용자: "원래 정도로 돌아오면 줌 끝"). 키를 떼면 단계는 0으로 돌아간다.
	public static volatile boolean keyHeld = false;      // 줌 키가 눌려 있는지(모듈이 갱신)
	public static volatile boolean wheelEnabled = true;  // 휠 조절 설정
	public static volatile int wheelLevel = 0;           // 0 = 기본 줌, +1마다 ×0.8
	public static volatile boolean wheelEnded = false;   // 휠로 줌을 끝낸 상태(키를 뗄 때까지 유지)
	public static final int MAX_WHEEL_LEVEL = 8;

	/** 49-24차: 기본 줌보다 아래로 이만큼 더 굴려야 줌이 끝난다(사용자: "많이 땡겨야 취소돼야"). */
	public static final int CANCEL_STEPS = 3;

	/**
	 * MouseScrollMixin에서 호출. 줌 중이면 휠을 소비(true)하고 단계를 바꾼다.
	 * 49-24차: 줌이 아직 당겨지는 중(progress가 target에 못 미침)이면 휠을 먹기만 하고 무시(시점 어색함 방지).
	 * 기본(0)보다 아래는 -1, -2(덜 확대, 원래 시야 쪽으로) → -CANCEL_STEPS에서 줌 종료.
	 */
	public static boolean consumeScroll(double vertical) {
		if (!keyHeld || !wheelEnabled || wheelEnded || vertical == 0) {
			return false;
		}
		if (Math.abs(progress - target) > 0.06f) {
			return true; // 전환 중엔 무시(핫바도 안 바뀌게 소비만)
		}
		if (vertical > 0) {
			wheelLevel = Math.min(MAX_WHEEL_LEVEL, wheelLevel + 1);
		} else {
			wheelLevel--;
			if (wheelLevel <= -CANCEL_STEPS) {
				wheelEnded = true;
				wheelLevel = 0;
				target = 0f;
			}
		}
		return true;
	}

	/** 휠 단계가 반영된 실제 줌 FOV(음수 단계 = 기본 줌보다 덜 확대). applyZoom이 기본 시야를 넘지 않게 자른다. */
	public static float effectiveTargetFov() {
		float fov = targetFov;
		if (wheelLevel >= 0) {
			for (int i = 0; i < wheelLevel; i++) {
				fov *= 0.8f;
			}
		} else {
			for (int i = 0; i < -wheelLevel; i++) {
				fov /= 0.8f;
			}
		}
		return Math.max(1f, fov);
	}

	/** 프레임마다 호출 - 실제 시간으로 progress를 target에 수렴시킴(한 프레임에 여러 번 불려도 같은 값). */
	public static void tick() {
		long now = System.nanoTime();
		if (lastNanos == 0) {
			lastNanos = now;
		}
		double dt = Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
		lastNanos = now;
		if (instant) {
			progress = target;
			return;
		}
		float p = progress;
		float k = (float) (1 - Math.exp(-dt * speedPerSecond));
		p += (target - p) * k;
		if (Math.abs(target - p) < 0.0015f) {
			p = target;
		}
		progress = p;
	}

	/** 기본 FOV를 받아 줌이 적용된 FOV를 돌려줌. 줌 중이 아니면 그대로. */
	public static double applyZoom(double baseFov) {
		float p = progress;
		if (p <= 0.0005f) {
			return baseFov;
		}
		double eff = Math.min(baseFov, effectiveTargetFov());
		return baseFov - (baseFov - eff) * p;
	}
}
