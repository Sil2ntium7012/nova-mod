package kr.lunaslight.mod.util;

import kr.lunaslight.mod.module.impl.render.CapeSmoothModule;

import java.util.HashMap;
import java.util.Map;

/**
 * 49-242차(사용자: "망토가 물결을 치게, 부드러운 건 부드러운 거고 중력의 영향을 받게"): 망토를 위에서 아래로 16마디로
 * 나눈 사슬(CapeChainMixin)의 각 마디 각도를 플레이어마다 따로 흉내 낸다.
 *
 * <p>49-272차(사용자: "너무 끊겨보이고 중력이 이상해")에서 새로 짰다. 예전 판은 (1) 굽힘 부호가 반대라 처져야 할 아랫자락이
 * 오히려 뒤로 말려 올라갔고, (2) 마디마다 윗마디를 덜 감쇠된 스프링으로 따라가는 사슬이라 흔들림이 아래로 갈수록 불어나
 * 마디가 꺾여 보였다. 지금은
 * <ul>
 *   <li>각 마디의 목표 각도(몸 기준, 0 = 등에 붙어 수직)를 바로 정한다: 맨 윗마디 = 바닐라 각도, 아래로 갈수록 중력에 처짐
 *       (바람이 세면 덜 처짐). 모양이 매끈한 곡선이라 꺾임이 없다.</li>
 *   <li><b>관성</b>: 마디마다 임계 감쇠 스프링으로 목표를 따라간다(넘치지 않음). 아랫마디일수록 느려서 걷기 시작/멈춤/점프 때
 *       아랫자락이 늦게 따라온다. 해석해로 풀어 프레임이 들쭉날쭉해도 매끈하다.</li>
 *   <li><b>물결</b>: 관성 뒤에 얹는 위에서 아래로 흐르는 사인 물결(아래로 갈수록 커짐, 속도에 비례). 서 있으면 아주 잔잔하게.</li>
 * </ul>
 * 결과는 "윗마디 대비 아랫마디가 뒤로 더 들린 각도(라디안)"이고, 모델 부품 부호로 바꾸는 건 각 트리의 CapeChainMixin 몫.
 * 버전과 무관한 순수 계산이라 두 트리가 같은 파일을 쓴다.
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
		float wind;
		long t;
		boolean init;
		long used;
	}

	private static final Map<Integer, Sim> SIMS = new HashMap<>();
	private static final float[] FLAT = new float[SEGMENTS];

	/**
	 * 마디 i(1..15)의 윗마디 대비 굽힘(라디안, +면 아랫마디가 몸에서 더 멀어짐). rootDeg = 바닐라가 윗마디에 준 각도(도),
	 * lean = 앞으로 나아가는 정도(바닐라 capeLean). 물결이 꺼져 있으면 전부 0(바닐라처럼 곧은 판).
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
		if (!s.init || now - s.used > 1_000_000_000L) {
			// 처음이거나 오래 안 그렸다(시야 밖, 일시 정지) - 지금 모양에서 바로 시작
			for (int i = 0; i < SEGMENTS; i++) {
				s.a[i] = target(rootDeg, 0f, i);
				s.v[i] = 0f;
			}
			s.wind = 0f;
			s.t = now - 16_000_000L;
			s.init = true;
		}
		s.used = now;
		float dt = Math.max(0f, Math.min(0.05f, (now - s.t) / 1e9f));
		s.t = now;
		if (dt <= 0f) {
			return s.out;   // 같은 프레임에 또 불림 - 지난 결과 그대로
		}
		// 바람(속도) 세기도 살짝 다듬어 물결 크기/빠르기가 툭툭 바뀌지 않게
		float windNow = Math.max(0f, Math.min(1f, lean / 60f));
		s.wind += (windNow - s.wind) * (1f - (float) Math.exp(-dt * 6f));
		float wind = s.wind;
		float strength = CapeSmoothModule.waveStrength() / 5f;   // 1~10 → 0.2~2
		float amp = (0.9f + 5.5f * wind) * strength;
		s.phase += dt * (2.4f + 7.5f * wind);
		if (s.phase > 1000f) {
			s.phase -= (float) (Math.PI * 2 * 150);
		}
		s.a[0] = rootDeg;
		s.v[0] = 0f;
		float prev = rootDeg;
		for (int i = 1; i < SEGMENTS; i++) {
			float d = i / (float) (SEGMENTS - 1);
			float goal = target(rootDeg, wind, i);
			// 임계 감쇠 스프링 해석해: 아랫마디일수록 느리게(관성)
			float w = 15f - 8.5f * d;
			float x0 = s.a[i] - goal;
			float c = s.v[i] + w * x0;
			float e = (float) Math.exp(-w * dt);
			s.a[i] = goal + (x0 + c * dt) * e;
			s.v[i] = (s.v[i] - w * c * dt) * e;
			float ang = s.a[i] + amp * d * (float) Math.sin(s.phase - d * 5.2f);
			if (ang < -2f) {
				ang = -2f;   // 등 속으로 파고들지 않게
			} else if (ang > 120f) {
				ang = 120f;
			}
			s.out[i] = (ang - prev) * 0.017453292f;
			prev = ang;
		}
		s.out[0] = 0f;
		return s.out;
	}

	/** 마디 i의 목표 각도(도): 윗마디 각도에서 아래로 갈수록 수직(0) 쪽으로 처진다. 바람이 세면 덜 처진다. */
	private static float target(float rootDeg, float wind, int i) {
		float d = i / (float) (SEGMENTS - 1);
		float sag = 0.5f * (1f - 0.8f * wind);
		float base = Math.max(0f, rootDeg);
		return rootDeg - base * sag * (float) Math.pow(d, 1.3);
	}
}
