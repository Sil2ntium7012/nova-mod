package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;

import java.util.HashMap;
import java.util.Map;

/**
 * 49-242차(사용자: "망토가 물결을 치게, 부드러운 건 부드러운 거고 중력의 영향을 받게"): 망토를 위에서 아래로 16마디로
 * 나눈 사슬(CapeChainMixin)의 각 마디 각도를 플레이어마다 따로 흉내 낸다.
 *
 * <p>맨 윗마디는 바닐라 각도(속도/점프에 따른 들림) 그대로. 아랫마디는
 * <ul>
 *   <li><b>중력</b>: 윗마디보다 조금씩 더 수직 쪽으로 처진다(바람이 세면 덜 처진다).</li>
 *   <li><b>관성</b>: 목표 각도를 스프링-댐퍼로 따라가서, 걷기 시작/멈춤/점프 때 아랫자락이 늦게 따라오며 출렁인다.</li>
 *   <li><b>물결</b>: 움직이는 동안 위에서 아래로 흘러 내려가는 사인 물결(속도에 따라 커지고 빨라짐). 서 있으면 아주 잔잔하게.</li>
 * </ul>
 * 버전과 무관한 순수 계산이라 두 트리가 같은 파일을 쓴다(모델에 적용은 각 트리의 CapeChainMixin).
 */
public final class CapePhysics {
	private CapePhysics() {
	}

	public static final int SEGMENTS = 16;

	private static final class Sim {
		final float[] a = new float[SEGMENTS];
		final float[] v = new float[SEGMENTS];
		final float[] out = new float[SEGMENTS];
		float phase;
		long t;
		boolean init;
		long used;
	}

	private static final Map<Integer, Sim> SIMS = new HashMap<>();
	private static final float[] FLAT = new float[SEGMENTS];

	/**
	 * 마디 i(1..15)의 윗마디 대비 굽힘(라디안). rootDeg = 바닐라가 윗마디에 준 각도(도), lean = 앞으로 나아가는 정도(바닐라 capeLean).
	 * 물결이 꺼져 있으면 전부 0(바닐라처럼 곧은 판).
	 */
	public static synchronized float[] step(int id, float rootDeg, float lean) {
		if (!CapeSmoothModule.waveOn()) {
			return FLAT;
		}
		long now = System.nanoTime();
		Sim s = SIMS.get(id);
		if (s == null) {
			if (SIMS.size() > 128) {
				SIMS.values().removeIf(x -> now - x.used > 10_000_000_000L);
			}
			s = new Sim();
			SIMS.put(id, s);
		}
		s.used = now;
		if (!s.init) {
			for (int i = 0; i < SEGMENTS; i++) {
				s.a[i] = rootDeg;
			}
			s.t = now;
			s.init = true;
		}
		float dt = Math.max(0f, Math.min(0.1f, (now - s.t) / 1e9f));
		s.t = now;
		float strength = CapeSmoothModule.waveStrength() / 5f;   // 1~10 → 0.2~2
		float wind = Math.max(0f, Math.min(1f, lean / 60f));
		float droop = 0.055f * (1f - 0.75f * wind);
		float amp = (0.8f + 6.5f * wind) * strength;
		float speed = 2.6f + 8f * wind;
		int n = Math.max(1, (int) Math.ceil(dt / 0.008f));
		float h = dt / n;
		for (int k = 0; k < n; k++) {
			s.phase += h * speed;
			s.a[0] = rootDeg;
			for (int i = 1; i < SEGMENTS; i++) {
				float depth = i / (float) (SEGMENTS - 1);
				float target = s.a[i - 1] * (1f - droop) + amp * (float) Math.sin(s.phase - i * 0.55f) * (0.35f + 0.65f * depth);
				s.v[i] += (90f * (target - s.a[i]) - 11f * s.v[i]) * h;
				s.a[i] += s.v[i] * h;
				if (s.a[i] < -8f) {
					s.a[i] = -8f;   // 몸 쪽으로 너무 파고들지 않게
					s.v[i] = 0f;
				} else if (s.a[i] > 125f) {
					s.a[i] = 125f;
					s.v[i] = 0f;
				}
			}
		}
		s.out[0] = 0f;
		for (int i = 1; i < SEGMENTS; i++) {
			s.out[i] = (s.a[i] - s.a[i - 1]) * 0.017453292f;
		}
		return s.out;
	}
}
