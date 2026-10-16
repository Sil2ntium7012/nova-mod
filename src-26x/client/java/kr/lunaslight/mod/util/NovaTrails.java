package kr.lunaslight.mod.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 49-364차(사용자: "클라이언트에 발자취 추가된 거 있는데 마저 그것도 추가좀"): 노바 발자취 - 걸을 때 발밑에 남는 효과.
 * 런처 상점(10-16): 별가루 발자취 {@code stardust}(크림빛 반짝이 별 + 금빛 가루가 발자국마다 톡톡 튀고 천천히 떠오름),
 * 벚꽃 발자취 {@code petal}(연분홍 꽃잎이 발밑에서 살랑 흩날림). 내 것 = .luna-launch.json cosmetics.trail(서명 종류 "trail"),
 * 남의 것 = nova_player_tiers.cosmetic_trail - 소유 확인은 날개와 같은 {@link NovaWings#TRAIL} 칸.
 *
 * <p>버전 이름이 안 섞이게 이 클래스는 마크 타입을 안 쓴다: TrailsModule이 틱마다 플레이어 위치를 넘기면 여기서 발자국을 찍고
 * 작은 입자를 움직이며, 그릴 때는 카메라를 향한 사각형 꼭짓점(카메라 기준 좌표)을 만들어 버전별 렌더러(NovaWingsRender.drawQuads,
 * 1.14.4 NovaWingsGl14.drawQuads)에 넘긴다. 그림은 jar 안 암호화 묶음 "trail-fx"(64×16: 별, 가루, 꽃잎 둘 - 흰색, 꼭짓점 색으로 물들임).
 * 꼭짓점 한 개 = 9칸(x y z u v nx ny nz 색(ARGB 비트)).
 */
public final class NovaTrails {
	private NovaTrails() {
	}

	public static final int STRIDE = 9;
	public static final String ASSET = "trail-fx";
	private static final int MAX = 900;
	private static final double STEP = 0.45;
	private static final double FAR = 48;

	private static final int STAR = 0, DOT = 1, PETAL_A = 2, PETAL_B = 3;
	private static final int[] STAR_COLORS = {0xFFFFF7C9, 0xFFFFFBE3};
	private static final int[] DUST_COLORS = {0xFFFFCF4D, 0xFFFFE58A, 0xFFFFF0B0};
	private static final int[] PETAL_COLORS = {0xFFFFE3EE, 0xFFFF9CC2, 0xFFFFB7D0};

	private static final class P {
		double x, y, z, px, py, pz, vx, vy, vz, ground;
		float size, rot, prot, vrot, phase;
		int age, life, sprite, color;
		boolean petal, rest;
	}

	private static final class Walker {
		double sx, sz, lastY;
		boolean side;
		boolean seen;
	}

	private static final List<P> PS = new ArrayList<>();
	private static final Map<String, Walker> WALKERS = new HashMap<>();
	private static final Random R = new Random();
	private static float[] buf = new float[STRIDE * 4 * 64];

	/** 틱 시작: 입자를 한 칸 움직이고 다 된 것은 지운다. */
	public static void beginTick() {
		for (Walker w : WALKERS.values()) {
			w.seen = false;
		}
		Iterator<P> it = PS.iterator();
		while (it.hasNext()) {
			P p = it.next();
			p.px = p.x;
			p.py = p.y;
			p.pz = p.z;
			p.prot = p.rot;
			if (++p.age >= p.life) {
				it.remove();
				continue;
			}
			if (p.rest) {
				continue;
			}
			if (p.petal) {
				// 꽃잎: 톡 튀었다가 살랑살랑 좌우로 흔들리며 천천히 내려앉는다
				p.vy = Math.max(-0.022, p.vy - 0.0026);
				p.vx *= 0.94;
				p.vz *= 0.94;
				double sway = Math.sin(p.age * 0.28 + p.phase) * 0.006;
				p.x += p.vx + sway;
				p.z += p.vz + Math.cos(p.age * 0.23 + p.phase) * 0.004;
				p.y += p.vy;
				p.rot += p.vrot;
				double rest = p.ground + p.size * 0.7;
				if (p.y <= rest && p.vy < 0) {
					p.y = rest;
					p.rest = true;   // 땅에 내려앉으면 그대로 있다가 사라진다
				}
			} else {
				// 별가루: 천천히 떠오르고(별), 가루는 톡 튀었다가 느려진다
				p.vx *= 0.93;
				p.vz *= 0.93;
				p.vy = p.sprite == STAR ? p.vy * 0.97 : p.vy * 0.92 + 0.0004;
				p.x += p.vx;
				p.y += p.vy;
				p.z += p.vz;
				p.rot += p.vrot;
			}
		}
	}

	/** 틱마다 플레이어 하나(보이는 사람만). 발자국 거리만큼 걸었으면 발밑에 찍는다. */
	public static void player(String uuid, double x, double y, double z, boolean others) {
		if (uuid == null) {
			return;
		}
		Walker w = WALKERS.get(uuid);
		if (w == null) {
			w = new Walker();
			w.sx = x;
			w.sz = z;
			w.lastY = y;
			WALKERS.put(uuid, w);
		}
		w.seen = true;
		double dx = x - w.sx, dz = z - w.sz;
		double d2 = dx * dx + dz * dz;
		double dy = Math.abs(y - w.lastY);
		w.lastY = y;
		if (d2 > 9) {   // 순간이동 - 이어 찍지 않는다
			w.sx = x;
			w.sz = z;
			return;
		}
		if (d2 < STEP * STEP || dy > 0.06) {
			return;   // 덜 걸었거나 뛰는/떨어지는 중(내려앉으면 그 자리에 찍힌다)
		}
		List<String> keys = NovaWings.TRAIL.keysFor(uuid, others);
		if (keys.isEmpty()) {
			w.sx = x;
			w.sz = z;
			return;
		}
		double len = Math.sqrt(d2);
		double nx = -dz / len, nz = dx / len;   // 걷는 방향의 옆 - 왼발 오른발 번갈아
		double off = (w.side ? 0.13 : -0.13);
		w.side = !w.side;
		w.sx = x;
		w.sz = z;
		spawn(keys.get(0), x + nx * off, y, z + nz * off);
	}

	/** 틱 끝: 이번 틱에 안 보인 사람은 잊는다. */
	public static void endTick() {
		WALKERS.values().removeIf(w -> !w.seen);
	}

	public static void clear() {
		PS.clear();
		WALKERS.clear();
	}

	private static double rnd(double a, double b) {
		return a + R.nextDouble() * (b - a);
	}

	private static void spawn(String key, double x, double y, double z) {
		if (PS.size() > MAX) {
			return;
		}
		if ("petal".equals(key)) {
			int n = 2 + (R.nextInt(3) == 0 ? 1 : 0);
			for (int i = 0; i < n; i++) {
				P p = base(x + rnd(-0.08, 0.08), y + 0.05, z + rnd(-0.08, 0.08), y);
				p.petal = true;
				p.sprite = R.nextBoolean() ? PETAL_A : PETAL_B;
				p.color = PETAL_COLORS[R.nextInt(PETAL_COLORS.length)];
				p.size = (float) rnd(0.075, 0.11);
				p.life = 40 + R.nextInt(25);
				p.vx = rnd(-0.025, 0.025);
				p.vz = rnd(-0.025, 0.025);
				p.vy = rnd(0.03, 0.055);
				p.vrot = (float) rnd(-0.18, 0.18);
				p.rot = (float) rnd(0, Math.PI * 2);
				p.prot = p.rot;
				PS.add(p);
			}
			return;
		}
		// stardust(모르는 키도 별가루로)
		P s = base(x + rnd(-0.05, 0.05), y + 0.06, z + rnd(-0.05, 0.05), y);
		s.sprite = STAR;
		s.color = STAR_COLORS[R.nextInt(STAR_COLORS.length)];
		s.size = (float) rnd(0.085, 0.13);
		s.life = 24 + R.nextInt(14);
		s.vx = rnd(-0.008, 0.008);
		s.vz = rnd(-0.008, 0.008);
		s.vy = rnd(0.012, 0.024);
		s.vrot = (float) rnd(-0.04, 0.04);
		PS.add(s);
		int n = 2 + R.nextInt(2);
		for (int i = 0; i < n; i++) {
			P p = base(x + rnd(-0.1, 0.1), y + 0.04, z + rnd(-0.1, 0.1), y);
			p.sprite = DOT;
			p.color = DUST_COLORS[R.nextInt(DUST_COLORS.length)];
			p.size = (float) rnd(0.03, 0.05);
			p.life = 16 + R.nextInt(14);
			p.vx = rnd(-0.03, 0.03);
			p.vz = rnd(-0.03, 0.03);
			p.vy = rnd(0.035, 0.06);
			PS.add(p);
		}
	}

	private static P base(double x, double y, double z, double ground) {
		P p = new P();
		p.x = p.px = x;
		p.y = p.py = y;
		p.z = p.pz = z;
		p.ground = ground;
		p.phase = (float) rnd(0, Math.PI * 2);
		return p;
	}

	public static boolean any() {
		return !PS.isEmpty();
	}

	/** 그릴 꼭짓점 버퍼(build가 채운 것). */
	public static float[] buffer() {
		return buf;
	}

	/** 카메라를 향한 사각형들을 카메라 기준 좌표로 만든다. 돌려주는 값 = 꼭짓점 수(4의 배수). */
	public static int build(double cx, double cy, double cz, float td) {
		int need = PS.size() * 4 * STRIDE;
		if (buf.length < need) {
			buf = new float[need + STRIDE * 4 * 64];
		}
		int v = 0;
		for (P p : PS) {
			double x = p.px + (p.x - p.px) * td - cx;
			double y = p.py + (p.y - p.py) * td - cy;
			double z = p.pz + (p.z - p.pz) * td - cz;
			double dist = Math.sqrt(x * x + y * y + z * z);
			if (dist > FAR || dist < 1e-3) {
				continue;
			}
			// 보는 쪽(d = 카메라 → 입자), 오른쪽 = d × 위, 위 = 오른쪽 × d
			double dxv = x / dist, dyv = y / dist, dzv = z / dist;
			double rx = -dzv, ry = 0, rz = dxv;   // d × (0,1,0)
			double rl = Math.sqrt(rx * rx + rz * rz);
			if (rl < 1e-4) {
				rx = 1;
				rz = 0;
				rl = 1;
			}
			rx /= rl;
			rz /= rl;
			double ux = ry * dzv - rz * dyv, uy = rz * dxv - rx * dzv, uz = rx * dyv - ry * dxv;
			float age = p.age + td;
			float life = p.life;
			float a = Math.min(1f, age / 3f);
			float tail = (life - age) / (life * 0.35f);
			if (tail < 1f) {
				a *= Math.max(0f, tail);
			}
			if (a <= 0.01f) {
				continue;
			}
			float size = p.size;
			if (p.sprite == STAR) {
				size *= 0.8f + 0.25f * (float) Math.sin(age * 0.7f + p.phase);   // 반짝임
			} else if (!p.petal) {
				size *= Math.max(0.35f, 1f - age / life * 0.6f);
			}
			float rot = p.prot + (p.rot - p.prot) * td;
			double c = Math.cos(rot) * size, s = Math.sin(rot) * size;
			// 돌린 오른쪽/위
			double Rx = rx * c + ux * s, Ry = uy * s, Rz = rz * c + uz * s;
			double Ux = -rx * s + ux * c, Uy = uy * c, Uz = -rz * s + uz * c;
			int alpha = Math.max(0, Math.min(255, Math.round(((p.color >>> 24) & 0xFF) * a)));
			float col = Float.intBitsToFloat((alpha << 24) | (p.color & 0xFFFFFF));
			float u0 = p.sprite * 0.25f + 0.001f, u1 = p.sprite * 0.25f + 0.249f;
			float nx = (float) -dxv, ny = (float) -dyv, nz = (float) -dzv;
			v = put(v, x - Rx - Ux, y - Ry - Uy, z - Rz - Uz, u0, 0.999f, nx, ny, nz, col);
			v = put(v, x + Rx - Ux, y + Ry - Uy, z + Rz - Uz, u1, 0.999f, nx, ny, nz, col);
			v = put(v, x + Rx + Ux, y + Ry + Uy, z + Rz + Uz, u1, 0.001f, nx, ny, nz, col);
			v = put(v, x - Rx + Ux, y - Ry + Uy, z - Rz + Uz, u0, 0.001f, nx, ny, nz, col);
		}
		return v;
	}

	private static int put(int v, double x, double y, double z, float u, float t, float nx, float ny, float nz, float col) {
		int b = v * STRIDE;
		buf[b] = (float) x;
		buf[b + 1] = (float) y;
		buf[b + 2] = (float) z;
		buf[b + 3] = u;
		buf[b + 4] = t;
		buf[b + 5] = nx;
		buf[b + 6] = ny;
		buf[b + 7] = nz;
		buf[b + 8] = col;
		return v + 1;
	}

	/** 색 칸(ARGB 비트)을 정수로. */
	public static int color(float f) {
		return Float.floatToRawIntBits(f);
	}
}
