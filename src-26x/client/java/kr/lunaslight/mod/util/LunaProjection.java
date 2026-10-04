package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/**
 * 49-21차: 월드 좌표 → 화면 좌표 투영 + 안티앨리어싱 2D 선.
 *
 * 49-16차에 크로스헤어 아웃라인 안에 있던 투영 수학을 공용으로 빼냄 - 아웃라인, 아이템 이름표,
 * 웨이포인트 라벨이 전부 같은 카메라 프레임을 쓴다. 마크 렌더 API에 기대지 않으므로 40개 버전
 * 전부 동일 코드(카메라 위치/각도/FOV만 LunaCompat·ZoomState에서 받음).
 *
 * 카메라 기저(마크 규약 = Entity.getRotationVector): yaw 0 = +Z, pitch 양수 = 아래.
 *   정면 f = (-sin yaw·cos pitch, -sin pitch, cos yaw·cos pitch)
 *   오른쪽 r = normalize(f × 월드 위), 위 u = r × f
 *   화면 x = (0.5 + (vx/vz)/(tan(fov/2)·aspect)·0.5)·sw,  화면 y = (0.5 - (vy/vz)/tan(fov/2)·0.5)·sh
 */
public final class LunaProjection {
	public static final double NEAR = 0.05;

	public final double camX, camY, camZ;
	private final double fx, fy, fz, rx, ry, rz, ux, uy, uz;
	public final double tanHalf, aspect;
	public final int sw, sh;
	/** 49-223차: 이 프레임의 화면 흔들림/맞았을 때 기울기(뷰 공간 4x4, 열 우선). 없으면 null. */
	private final float[] bob;

	private LunaProjection(Vec3 cam, double yawDeg, double pitchDeg, double fovDeg, int sw, int sh, double aspect, float[] bob) {
		this.bob = bob;
		this.camX = cam.x;
		this.camY = cam.y;
		this.camZ = cam.z;
		double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
		double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
		fx = -sy * cp;
		fy = -sp;
		fz = cy * cp;
		double rxx = -fz, rzz = fx;
		double rl = Math.sqrt(rxx * rxx + rzz * rzz);
		if (rl < 1e-6) {
			rl = 1; // 완전 수직 시야 - 특이점(사실상 안 생김)
		}
		rx = rxx / rl;
		ry = 0;
		rz = rzz / rl;
		ux = ry * fz - rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy - ry * fx;
		this.tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);
		this.aspect = aspect;
		this.sw = sw;
		this.sh = sh;
	}

	// 49-22차: 프레임당 한 번만 계산(투영기를 쓰는 모듈이 5개가 넘는데 전부 같은 카메라를 본다).
	// ModuleManager의 HUD 콜백이 매 프레임 beginFrame()을 불러 캐시를 무효화한다.
	private static long frameSerial = 1;
	private static long cachedSerial = -1;
	private static LunaProjection cached;

	/** 새 프레임 시작 - 캐시된 투영기를 버림(HUD 콜백 진입 시 호출). */
	public static void beginFrame() {
		frameSerial++;
	}

	// ==================== 49-223차: 화면 흔들림(bobView) + 맞았을 때 기울기 ====================
	// 사용자: "블록 테두리 움직일 때 약간씩 흔들리고 2개씩 보이는 경우가 있어". 월드 투영 = 투영 × (흔들림 × 기울기) × 카메라 회전인데
	// 우리 투영엔 가운데가 없어서 걷는 동안 바닐라 윤곽과 어긋났다. ViewBobCaptureMixin이 그 행렬을 프레임마다 적어 준다.

	private static final float[] BOB = new float[16];
	private static long bobSerial = Long.MIN_VALUE;
	private static java.lang.reflect.Method matGet;
	private static boolean matGetResolved;

	/** 행렬 스택(버전마다 MatrixStack/PoseStack)의 맨 위 위치 행렬을 기록. */
	public static void recordViewBob(Object stack) {
		try {
			Object entry = LunaCompat.callNoArg(stack, "peek");
			if (entry == null) {
				entry = LunaCompat.callNoArg(stack, "last");
			}
			Object mat = LunaCompat.callNoArg(entry, "getPositionMatrix");
			if (mat == null) {
				mat = LunaCompat.callNoArg(entry, "pose");
			}
			if (mat == null) {
				mat = LunaCompat.callNoArg(entry, "getModel");
			}
			recordViewBobMatrix(mat);
		} catch (Throwable ignored) {
		}
	}

	/** JOML Matrix4f(1.19.3+, 26.x)만 읽는다(get(float[]) = 열 우선). 그 전 버전의 마크 Matrix4f는 건너뜀. */
	public static void recordViewBobMatrix(Object mat) {
		if (mat == null) {
			return;
		}
		try {
			if (!matGetResolved) {
				matGetResolved = true;
				try {
					matGet = mat.getClass().getMethod("get", float[].class);
				} catch (NoSuchMethodException e) {
					matGet = null;
				}
			}
			if (matGet == null) {
				return;
			}
			matGet.invoke(mat, (Object) BOB);
			bobSerial = frameSerial;
		} catch (Throwable ignored) {
		}
	}

	/** 이번 프레임 월드가 쓴 흔들림 행렬(HUD 콜백은 beginFrame으로 번호를 하나 올린 뒤라 직전 번호). 단위 행렬이면 null. */
	private static float[] currentBob() {
		if (bobSerial != frameSerial - 1 && bobSerial != frameSerial) {
			return null;
		}
		float[] b = BOB;
		boolean identity = true;
		for (int i = 0; i < 16; i++) {
			float want = (i % 5 == 0) ? 1f : 0f;
			if (Math.abs(b[i] - want) > 1e-5f) {
				identity = false;
				break;
			}
		}
		return identity ? null : b.clone();
	}

	/** 우리 뷰 좌표(z = 정면) → 흔들림 적용. 마크 뷰 공간은 정면이 -z라 부호를 바꿔 곱한다. */
	private void applyBob(double[] v) {
		float[] b = bob;
		if (b == null) {
			return;
		}
		double x = v[0], y = v[1], z = -v[2];
		double nx = b[0] * x + b[4] * y + b[8] * z + b[12];
		double ny = b[1] * x + b[5] * y + b[9] * z + b[13];
		double nz = b[2] * x + b[6] * y + b[10] * z + b[14];
		v[0] = nx;
		v[1] = ny;
		v[2] = -nz;
	}

	/** 지금 프레임의 카메라로 투영기를 만든다(프레임당 1회 계산, 이후는 캐시). 카메라/FOV를 못 읽으면 null. */
	public static LunaProjection capture(Minecraft client) {
		// 49-288차(사용자: "26.x에서 화면을 돌리면 홀로그램이 화면에 붙어서 같이 돈다"): 프레임 번호(HUD 콜백의 beginFrame)만 믿고
		// 캐시하면, 번호가 안 바뀌는 경로에서 처음 카메라 그대로 계속 그려 화면에 달라붙는다. 이제 카메라 위치/각도/FOV/화면 크기가
		// 하나라도 바뀌면 다시 만든다(같으면 그대로 - 계산은 몇 번의 곱셈뿐).
		net.minecraft.client.Camera cam = mainCamera(client);
		if (cached != null && cam != null && client != null && client.getWindow() != null) {
			Vec3 pos = cam.position();
			if (pos != null && pos.x == cached.camX && pos.y == cached.camY && pos.z == cached.camZ
					&& cam.yRot() == cached.yawDeg && cam.xRot() == cached.pitchDeg && cam.getFov() == cached.fovKey
					&& client.getWindow().getGuiScaledWidth() == cached.sw && client.getWindow().getGuiScaledHeight() == cached.sh
					&& cachedSerial >= frameSerial - 1) {
				cachedSerial = frameSerial;
				return cached;
			}
		} else if (cachedSerial == frameSerial && cam == null) {
			return cached;
		}
		LunaProjection p = compute(client, cam);
		cached = p;
		cachedSerial = frameSerial;
		return p;
	}

	private static java.lang.reflect.Method mainCameraMethod;
	private static boolean mainCameraResolved;

	/** 26.3 GameRenderer#mainCamera() / 26.1~26.2 getMainCamera(). 없으면 null. */
	private static net.minecraft.client.Camera mainCamera(Minecraft client) {
		if (client == null || client.gameRenderer == null) {
			return null;
		}
		try {
			if (!mainCameraResolved) {
				mainCameraResolved = true;
				for (String n : new String[]{"mainCamera", "getMainCamera"}) {
					try {
						java.lang.reflect.Method m = client.gameRenderer.getClass().getMethod(n);
						if (net.minecraft.client.Camera.class.isAssignableFrom(m.getReturnType())) {
							mainCameraMethod = m;
							break;
						}
					} catch (NoSuchMethodException ignored) {
					}
				}
			}
			return mainCameraMethod == null ? null : (net.minecraft.client.Camera) mainCameraMethod.invoke(client.gameRenderer);
		} catch (Throwable t) {
			return null;
		}
	}

	/** 캐시 비교용(만들 때의 카메라 값). */
	private double yawDeg = Double.NaN, pitchDeg = Double.NaN, fovKey = Double.NaN;

	private static LunaProjection compute(Minecraft client, net.minecraft.client.Camera cam) {
		if (client == null || client.player == null || client.getWindow() == null) {
			return null;
		}
		if (cam != null && cam.position() != null) {
			// 49-288차: 26.x 카메라를 직접 읽는다(리플렉션 이름 찾기 없이) - 위치, 각도, 실제 FOV(시야 효과, 줌 포함)
			double fov = cam.getFov();
			if (!(fov > 1) || !(fov < 179)) {
				fov = ZoomState.lastFov > 1 && ZoomState.lastFov < 179 ? ZoomState.lastFov : LunaCompat.optionsFov(client);
			}
			if (!(fov > 1) || !(fov < 179)) {
				return null;
			}
			int sw = client.getWindow().getGuiScaledWidth();
			int sh = client.getWindow().getGuiScaledHeight();
			double aspect = (double) client.getWindow().getWidth() / Math.max(1, client.getWindow().getHeight());
			LunaProjection p = new LunaProjection(cam.position(), cam.yRot(), cam.xRot(), fov, sw, sh, aspect, currentBob());
			p.yawDeg = cam.yRot();
			p.pitchDeg = cam.xRot();
			p.fovKey = cam.getFov();
			return p;
		}
		Object camera = LunaCompat.getCamera(client);
		Vec3 camPos = LunaCompat.cameraPos(camera, client);
		if (camPos == null) {
			return null;
		}
		// 월드 렌더가 실제로 쓴 FOV(GameRendererFovMixin이 changingFov=true 호출만 기록). 없으면 옵션값.
		double fov = ZoomState.lastFov;
		if (!(fov > 1) || !(fov < 179)) {
			fov = LunaCompat.optionsFov(client);
		}
		if (!(fov > 1) || !(fov < 179)) {
			return null;
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		double aspect = (double) client.getWindow().getWidth()
				/ Math.max(1, client.getWindow().getHeight());
		return new LunaProjection(camPos, LunaCompat.cameraYaw(camera, client), LunaCompat.cameraPitch(camera, client),
				fov, sw, sh, aspect, currentBob());
	}

	/** 월드 좌표 → 뷰 공간(out[0]=오른쪽, out[1]=위, out[2]=정면 거리). */
	public void toView(double wx, double wy, double wz, double[] out) {
		double px = wx - camX, py = wy - camY, pz = wz - camZ;
		out[0] = px * rx + py * ry + pz * rz;
		out[1] = px * ux + py * uy + pz * uz;
		out[2] = px * fx + py * fy + pz * fz;
		applyBob(out);
	}

	/** 뷰 공간 → 화면(out[0]=x, out[1]=y). 카메라 뒤(정면 거리 < NEAR)면 false. */
	public boolean viewToScreen(double vx, double vy, double vz, double[] out) {
		if (vz < NEAR) {
			return false;
		}
		out[0] = (0.5 + (vx / vz) / (tanHalf * aspect) * 0.5) * sw;
		out[1] = (0.5 - (vy / vz) / tanHalf * 0.5) * sh;
		return true;
	}

	/** 월드 좌표 → 화면. out[0]=x, out[1]=y, out[2]=정면 거리. 카메라 뒤면 false. */
	public boolean project(double wx, double wy, double wz, double[] out) {
		double px = wx - camX, py = wy - camY, pz = wz - camZ;
		double vx = px * rx + py * ry + pz * rz;
		double vy = px * ux + py * uy + pz * uz;
		double vz = px * fx + py * fy + pz * fz;
		if (bob != null) {
			double[] t = {vx, vy, vz};
			applyBob(t);
			vx = t[0];
			vy = t[1];
			vz = t[2];
		}
		if (!viewToScreen(vx, vy, vz, out)) {
			return false;
		}
		out[2] = vz;
		return true;
	}

	/** 카메라에서 (wx,wy,wz)까지의 정면 거리(뷰 z). 카메라 뒤면 음수. */
	public double depthOf(double wx, double wy, double wz) {
		double px = wx - camX, py = wy - camY, pz = wz - camZ;
		if (bob != null) {
			double[] t = {px * rx + py * ry + pz * rz, px * ux + py * uy + pz * uz, px * fx + py * fy + pz * fz};
			applyBob(t);
			return t[2];
		}
		return px * fx + py * fy + pz * fz;
	}

	/** 뷰 공간 점 → 화면 x(근평면 검사는 호출부). */
	private double scrX(double[] v) {
		return (0.5 + (v[0] / v[2]) / (tanHalf * aspect) * 0.5) * sw;
	}

	private double scrY(double[] v) {
		return (0.5 - (v[1] / v[2]) / tanHalf * 0.5) * sh;
	}

	/**
	 * 49-255차: 면(뷰 공간 네 점 a, b, c, d - 둘레 순서)이 화면에서 평행사변형과 얼마나 다른지(GUI 픽셀). 가까운 면일수록 원근 때문에
	 * 커진다. 근평면 뒤 점이 있으면 -1.
	 */
	public double quadSkew(double[] a, double[] b, double[] c, double[] d) {
		if (a[2] < NEAR || b[2] < NEAR || c[2] < NEAR || d[2] < NEAR) {
			return -1;
		}
		double ex = scrX(a) - scrX(b) + scrX(c) - scrX(d);
		double ey = scrY(a) - scrY(b) + scrY(c) - scrY(d);
		return Math.sqrt(ex * ex + ey * ey);
	}

	/**
	 * 49-253차(설계도 홀로그램) → 49-255차: 뷰 공간 네 점(a, b, c, d - 둘레 순서, a→b = 가로, a→d = 세로)으로 된 면을 화면에서
	 * 평행사변형으로 맞춰 행렬(회전·배율·회전, 2x2 SVD)을 건다. 예전엔 a 꼭짓점에 붙여 맞춰서 c 쪽이 원근만큼 통째로 어긋났다
	 * (사용자: "홀로그램이 이상해") - 이제 네 점의 가운데를 지나게 맞춰 어긋남을 네 꼭짓점에 1/4씩 고루 나눈다.
	 * 행렬을 건 뒤에는 (0,0)~(size,size) 정사각형이 그 면이 된다. 그릴 게 없으면 false(행렬 안 걸림).
	 */
	private boolean pushQuad(GuiGraphicsExtractor ctx, double[] a, double[] b, double[] c, double[] d, float size) {
		if (a[2] < NEAR || b[2] < NEAR || c[2] < NEAR || d[2] < NEAR || !LunaCompat.guiRotateSupported(ctx)) {
			return false;
		}
		double ax = scrX(a), ay = scrY(a), bx = scrX(b), by = scrY(b);
		double cx = scrX(c), cy = scrY(c), dx = scrX(d), dy = scrY(d);
		double m00 = ((bx - ax) + (cx - dx)) / 2, m10 = ((by - ay) + (cy - dy)) / 2;   // 가로 변
		double m01 = ((dx - ax) + (cx - bx)) / 2, m11 = ((dy - ay) + (cy - by)) / 2;   // 세로 변
		if (Math.abs(m00 * m11 - m01 * m10) < 0.5) {
			return false;   // 거의 옆에서 보는 면 - 안 보인다
		}
		double ox = (ax + bx + cx + dx) / 4 - (m00 + m01) / 2;
		double oy = (ay + by + cy + dy) / 4 - (m10 + m11) / 2;
		double e = (m00 + m11) / 2, f = (m00 - m11) / 2, g = (m10 + m01) / 2, hh = (m10 - m01) / 2;
		double q = Math.sqrt(e * e + hh * hh), r = Math.sqrt(f * f + g * g);
		double sx = q + r, sy = q - r;
		double a1 = Math.atan2(g, f), a2 = Math.atan2(hh, e);
		double theta = (a2 - a1) / 2, phi = (a2 + a1) / 2;
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, (float) ox, (float) oy);
		LunaCompat.guiRotate(ctx, (float) phi);
		LunaCompat.guiScale(ctx, (float) (sx / size), (float) (sy / size));
		LunaCompat.guiRotate(ctx, (float) theta);
		return true;
	}

	/**
	 * 49-264차: 화면 점 o에서 가로 변 (ex, ey), 세로 변 (fx, fy)가 되도록 행렬을 건다 - 그 뒤 (0,0)~(size,size)가 그 평행사변형.
	 * 거의 선이면 false(행렬 안 걸림).
	 */
	private boolean pushAffine(GuiGraphicsExtractor ctx, double ox, double oy, double m00, double m10, double m01, double m11, float size) {
		if (Math.abs(m00 * m11 - m01 * m10) < 0.05) {
			return false;
		}
		double e = (m00 + m11) / 2, f = (m00 - m11) / 2, g = (m10 + m01) / 2, hh = (m10 - m01) / 2;
		double q = Math.sqrt(e * e + hh * hh), r = Math.sqrt(f * f + g * g);
		double sx = q + r, sy = q - r;
		double a1 = Math.atan2(g, f), a2 = Math.atan2(hh, e);
		double theta = (a2 - a1) / 2, phi = (a2 + a1) / 2;
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, (float) ox, (float) oy);
		LunaCompat.guiRotate(ctx, (float) phi);
		LunaCompat.guiScale(ctx, (float) (sx / size), (float) (sy / size));
		LunaCompat.guiRotate(ctx, (float) theta);
		return true;
	}

	/**
	 * 49-264차: 면(a, b, c, d)을 대각선 b-d로 나눈 두 삼각형으로 정확히 그린다. half0 = 그림의 왼쪽 위 반쪽(a, b, d에 맞춤),
	 * half1 = 오른쪽 아래 반쪽(c, d, b에 맞춤). 삼각형은 아핀 변환으로 꼭짓점이 딱 맞으므로 원근이 큰 가까운 면도 비틀어지지 않는다.
	 * 근평면 뒤 점이 있거나 회전을 못 쓰면 false.
	 */
	public boolean texTri(GuiGraphicsExtractor ctx, double[] a, double[] b, double[] c, double[] d, net.minecraft.resources.Identifier half0, net.minecraft.resources.Identifier half1, int size, int argb) {
		if (a[2] < NEAR || b[2] < NEAR || c[2] < NEAR || d[2] < NEAR || !LunaCompat.guiRotateSupported(ctx)) {
			return false;
		}
		double ax = scrX(a), ay = scrY(a), bx = scrX(b), by = scrY(b);
		double cx = scrX(c), cy = scrY(c), dx = scrX(d), dy = scrY(d);
		if (pushAffine(ctx, ax, ay, bx - ax, by - ay, dx - ax, dy - ay, 16f)) {
			kr.lunaslight.mod.gui.LunaGfx.drawImageRegion(ctx, half0, 0, 0, 16, 16, 0, 0, size, size, size, size, argb);
			LunaCompat.guiPop(ctx);
		}
		// 오른쪽 아래 반쪽: (1,0)→b, (0,1)→d, (1,1)→c 가 되는 평행사변형(원점 = b + d - c)
		if (pushAffine(ctx, bx + dx - cx, by + dy - cy, cx - dx, cy - dy, cx - bx, cy - by, 16f)) {
			kr.lunaslight.mod.gui.LunaGfx.drawImageRegion(ctx, half1, 0, 0, 16, 16, 0, 0, size, size, size, size, argb);
			LunaCompat.guiPop(ctx);
		}
		return true;
	}

	/** 면을 한 색으로 반투명하게 채운다. 근평면 뒤이거나 회전을 못 쓰면 false. */
	public boolean fillViewQuad(GuiGraphicsExtractor ctx, double[] a, double[] b, double[] c, double[] d, int color) {
		if (!pushQuad(ctx, a, b, c, d, 1f)) {
			return false;
		}
		ctx.fill(0, 0, 1, 1, color);
		LunaCompat.guiPop(ctx);
		return true;
	}

	/**
	 * 49-255차: 면에 텍스처 한 부분(u, v에서 rw × rh 텍셀 - 49-258차부터 직사각형도)을 입힌다(설계도 홀로그램에 블록 그림 - 사용자: "무슨 블록인지 보여야지").
	 * argb의 알파가 투명도, RGB가 색 곱하기(잎 같은 바이옴 색).
	 */
	public boolean texViewQuad(GuiGraphicsExtractor ctx, double[] a, double[] b, double[] c, double[] d, net.minecraft.resources.Identifier tex,
			int u, int v, int rw, int rh, int texW, int texH, int argb) {
		if (!pushQuad(ctx, a, b, c, d, 16f)) {
			return false;
		}
		boolean ok = kr.lunaslight.mod.gui.LunaGfx.drawImageRegion(ctx, tex, 0, 0, 16, 16, u, v, rw, rh, texW, texH, argb);
		LunaCompat.guiPop(ctx);
		return ok;
	}

	/** 뷰 공간 선분을 근평면에서 자르고 화면에 AA 선으로 그림. */
	public void drawViewSegment(GuiGraphicsExtractor ctx, double[] a, double[] b, float width, int color) {
		double ax = a[0], ay = a[1], az = a[2];
		double bx = b[0], by = b[1], bz = b[2];
		if (az < NEAR && bz < NEAR) {
			return;
		}
		if (az < NEAR) {
			double s = (NEAR - az) / (bz - az);
			ax += (bx - ax) * s;
			ay += (by - ay) * s;
			az = NEAR;
		} else if (bz < NEAR) {
			double s = (NEAR - bz) / (az - bz);
			bx += (ax - bx) * s;
			by += (ay - by) * s;
			bz = NEAR;
		}
		double x0 = (0.5 + (ax / az) / (tanHalf * aspect) * 0.5) * sw;
		double y0 = (0.5 - (ay / az) / tanHalf * 0.5) * sh;
		double x1 = (0.5 + (bx / bz) / (tanHalf * aspect) * 0.5) * sw;
		double y1 = (0.5 - (by / bz) / tanHalf * 0.5) * sh;
		lineAA(ctx, x0, y0, x1, y1, width, color, sw, sh);
	}

	// =====================================================================
	// 안티앨리어싱 선 (49-21차: "블록 테두리 안티앨리어싱이 너무 안 돼 있어")
	// =====================================================================

	/**
	 * 굵기 width(실수, ≥1)의 AA 선. 각 픽셀 열(또는 행)에서 선이 덮는 실제 넓이만큼 알파를
	 * 줘서 계단이 사라진다(Wu 알고리즘의 굵은 선 확장). 안쪽(완전히 덮인) 픽셀은 열끼리 묶어서
	 * fill 호출을 줄이고, 가장자리 픽셀만 개별로 찍는다. 화면 밖 구간은 먼저 잘라내서
	 * 투영이 폭주해도 루프가 화면 크기를 넘지 않는다.
	 */
	public static void lineAA(GuiGraphicsExtractor ctx, double x0, double y0, double x1, double y1,
			float width, int color, int sw, int sh) {
		if (Double.isNaN(x0) || Double.isNaN(y0) || Double.isNaN(x1) || Double.isNaN(y1)) {
			return;
		}
		// 화면(여유 4px)으로 클리핑 - Liang-Barsky
		double[] clipped = clip(x0, y0, x1, y1, -4, -4, sw + 4, sh + 4);
		if (clipped == null) {
			return;
		}
		x0 = clipped[0];
		y0 = clipped[1];
		x1 = clipped[2];
		y1 = clipped[3];
		int baseA = (color >>> 24) & 0xFF;
		int rgb = color & 0x00FFFFFF;
		if (baseA <= 0) {
			return;
		}
		if (lineQuad(ctx, x0, y0, x1, y1, width, color)) {
			return;
		}
		double w = Math.max(1f, width);
		if (Math.abs(x1 - x0) >= Math.abs(y1 - y0)) {
			if (x0 > x1) {
				double t = x0; x0 = x1; x1 = t;
				t = y0; y0 = y1; y1 = t;
			}
			double dx = x1 - x0;
			double g = dx < 1e-9 ? 0 : (y1 - y0) / dx;
			double hv = (w / 2.0) * Math.sqrt(1 + g * g);
			int cs = (int) Math.floor(x0), ce = (int) Math.ceil(x1) - 1;
			if (ce < cs) {
				ce = cs;
			}
			int runStart = Integer.MIN_VALUE, runR0 = 0, runR1 = 0;
			for (int c = cs; c <= ce; c++) {
				double hc = Math.min(x1, c + 1) - Math.max(x0, c);
				if (hc <= 0.001) {
					continue;
				}
				hc = Math.min(1, hc);
				double yc = y0 + g * (c + 0.5 - x0);
				double top = yc - hv, bot = yc + hv;
				int r0 = (int) Math.floor(top);
				int r1 = (int) Math.ceil(bot) - 1;
				if (r1 <= r0) {
					// 한 행 안에 다 들어감
					pixel(ctx, c, r0, c + 1, r0 + 1, rgb, baseA * hc * (bot - top));
					continue;
				}
				// 위/아래 가장자리 행(부분 덮임)
				pixel(ctx, c, r0, c + 1, r0 + 1, rgb, baseA * hc * ((r0 + 1) - top));
				pixel(ctx, c, r1, c + 1, r1 + 1, rgb, baseA * hc * (bot - r1));
				// 안쪽 행 [r0+1, r1) - 완전 덮임: 열끼리 묶어서 한 번에
				int i0 = r0 + 1, i1 = r1;
				if (i1 > i0) {
					if (runStart != Integer.MIN_VALUE && hc >= 0.999 && i0 == runR0 && i1 == runR1) {
						continue; // 이어짐
					}
					if (runStart != Integer.MIN_VALUE) {
						pixel(ctx, runStart, runR0, c, runR1, rgb, baseA);
						runStart = Integer.MIN_VALUE;
					}
					if (hc >= 0.999) {
						runStart = c;
						runR0 = i0;
						runR1 = i1;
					} else {
						pixel(ctx, c, i0, c + 1, i1, rgb, baseA * hc);
					}
				} else if (runStart != Integer.MIN_VALUE) {
					pixel(ctx, runStart, runR0, c, runR1, rgb, baseA);
					runStart = Integer.MIN_VALUE;
				}
			}
			if (runStart != Integer.MIN_VALUE) {
				pixel(ctx, runStart, runR0, ce + 1, runR1, rgb, baseA);
			}
		} else {
			if (y0 > y1) {
				double t = x0; x0 = x1; x1 = t;
				t = y0; y0 = y1; y1 = t;
			}
			double dy = y1 - y0;
			double g = dy < 1e-9 ? 0 : (x1 - x0) / dy;
			double hh = (w / 2.0) * Math.sqrt(1 + g * g);
			int rs = (int) Math.floor(y0), re = (int) Math.ceil(y1) - 1;
			if (re < rs) {
				re = rs;
			}
			int runStart = Integer.MIN_VALUE, runC0 = 0, runC1 = 0;
			for (int r = rs; r <= re; r++) {
				double vc = Math.min(y1, r + 1) - Math.max(y0, r);
				if (vc <= 0.001) {
					continue;
				}
				vc = Math.min(1, vc);
				double xc = x0 + g * (r + 0.5 - y0);
				double left = xc - hh, right = xc + hh;
				int c0 = (int) Math.floor(left);
				int c1 = (int) Math.ceil(right) - 1;
				if (c1 <= c0) {
					pixel(ctx, c0, r, c0 + 1, r + 1, rgb, baseA * vc * (right - left));
					continue;
				}
				pixel(ctx, c0, r, c0 + 1, r + 1, rgb, baseA * vc * ((c0 + 1) - left));
				pixel(ctx, c1, r, c1 + 1, r + 1, rgb, baseA * vc * (right - c1));
				int i0 = c0 + 1, i1 = c1;
				if (i1 > i0) {
					if (runStart != Integer.MIN_VALUE && vc >= 0.999 && i0 == runC0 && i1 == runC1) {
						continue;
					}
					if (runStart != Integer.MIN_VALUE) {
						pixel(ctx, runC0, runStart, runC1, r, rgb, baseA);
						runStart = Integer.MIN_VALUE;
					}
					if (vc >= 0.999) {
						runStart = r;
						runC0 = i0;
						runC1 = i1;
					} else {
						pixel(ctx, i0, r, i1, r + 1, rgb, baseA * vc);
					}
				} else if (runStart != Integer.MIN_VALUE) {
					pixel(ctx, runC0, runStart, runC1, r, rgb, baseA);
					runStart = Integer.MIN_VALUE;
				}
			}
			if (runStart != Integer.MIN_VALUE) {
				pixel(ctx, runC0, runStart, runC1, re + 1, rgb, baseA);
			}
		}
	}

	/**
	 * 49-22차: 화면 투영 빛기둥(핑/웨이포인트 레이저의 HUD 판). 월드 렌더 이벤트가 없는 버전에서도 되고
	 * 벽 너머로도 보인다. 바닥(x,y,z)에서 height만큼 위로 뻗는 세로 띠를 회전 사각형 + 길이 방향
	 * 그라데이션(아래 진하고 위로 투명) + 양옆 페더로 그린다. worldWidth = 기둥 굵기(블록).
	 */
	public void drawBeam(GuiGraphicsExtractor ctx, double x, double y, double z, double height, double worldWidth, int color) {
		double[] a = new double[3], b = new double[3];
		toView(x, y, z, a);
		toView(x, y + height, z, b);
		if (a[2] < NEAR && b[2] < NEAR) {
			return;
		}
		// 근평면 클리핑
		if (a[2] < NEAR) {
			double t = (NEAR - a[2]) / (b[2] - a[2]);
			a[0] += (b[0] - a[0]) * t;
			a[1] += (b[1] - a[1]) * t;
			a[2] = NEAR;
		} else if (b[2] < NEAR) {
			double t = (NEAR - b[2]) / (a[2] - b[2]);
			b[0] += (a[0] - b[0]) * t;
			b[1] += (a[1] - b[1]) * t;
			b[2] = NEAR;
		}
		double x0 = (0.5 + (a[0] / a[2]) / (tanHalf * aspect) * 0.5) * sw;
		double y0 = (0.5 - (a[1] / a[2]) / tanHalf * 0.5) * sh;
		double x1 = (0.5 + (b[0] / b[2]) / (tanHalf * aspect) * 0.5) * sw;
		double y1 = (0.5 - (b[1] / b[2]) / tanHalf * 0.5) * sh;
		// 화면 픽셀 굵기 = 월드 굵기 × (픽셀/블록 @ 바닥 거리)
		double pxPerBlock = sh / (2.0 * tanHalf * Math.max(0.5, a[2]));
		float width = (float) Math.max(1.5, Math.min(40, worldWidth * pxPerBlock));
		double[] clipped = clip(x0, y0, x1, y1, -width - 4, -width - 4, sw + width + 4, sh + width + 4);
		if (clipped == null) {
			return;
		}
		double dx = clipped[2] - clipped[0], dy = clipped[3] - clipped[1];
		double len = Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5) {
			return;
		}
		int alpha = (color >>> 24) & 0xFF;
		int rgb = color & 0x00FFFFFF;
		if (!LunaCompat.guiRotateSupported(ctx)) {
			// 회전 불가 버전: 단순 세로 그라데이션 띠(기둥은 대개 거의 수직이라 충분)
			int cx = (int) Math.round((clipped[0] + clipped[2]) / 2);
			int top = (int) Math.round(Math.min(clipped[1], clipped[3])), bottom = (int) Math.round(Math.max(clipped[1], clipped[3]));
			int hw = Math.max(1, Math.round(width / 2));
			ctx.fillGradient(cx - hw, top, cx + hw, bottom, rgb, (alpha << 24) | rgb);
			return;
		}
		// 로컬 좌표: 바닥점을 원점, +y 방향 = 기둥 방향(회전), 그라데이션은 fillGradient의 세로축
		float ang = (float) (Math.atan2(dy, dx) - Math.PI / 2);
		int hw = Math.max(1, Math.round(width / 2f * SUB));
		int glowHw = Math.max(hw + 2, Math.round(width * 1.1f * SUB));
		int length = (int) Math.round(len * SUB);
		int bottomCol = (alpha << 24) | rgb;
		int topCol = rgb; // 알파 0
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, (float) clipped[0], (float) clipped[1]);
		LunaCompat.guiRotate(ctx, ang);
		LunaCompat.guiScale(ctx, 1f / SUB, 1f / SUB);
		// 바깥 글로우(넓고 옅게) + 중심 띠: y가 0(바닥, 진함) → length(위, 투명)
		ctx.fillGradient(-glowHw, 0, glowHw, length * 3 / 4, ((alpha / 4) << 24) | rgb, topCol);
		ctx.fillGradient(-hw, 0, hw, length, bottomCol, topCol);
		LunaCompat.guiPop(ctx);
	}

	/** 페더 사각형 좌표 정밀도(1px를 이만큼으로 쪼갬 - 굵기 0.25px 단위). */
	private static final float SUB = 4f;

	/**
	 * 49-22차: "프레임 드랍" 대책. 선 하나를 픽셀 단위 fill 수백~수천 개로 찍는 대신, 행렬을 선 방향으로
	 * 회전시켜 놓고 **사각형 3장**(중심 띠 + 위/아래 1px 페더 그라데이션)만 그린다. GPU가 페더 띠의
	 * 알파를 보간해 주므로 계단이 사라지고(커버리지 AA와 시각적으로 동등), 그리기 요소 수는 선당 3개로
	 * 고정된다(블록 테두리 12선 = 36개). 굵기는 0.25px 단위 실수, 1px 미만이면 알파를 그만큼 줄여
	 * 가늘어 보이게 한다. 회전을 지원하지 않는 버전이면 false(픽셀 방식 폴백).
	 */
	private static boolean lineQuad(GuiGraphicsExtractor ctx, double x0, double y0, double x1, double y1, float width, int color) {
		if (!LunaCompat.guiRotateSupported(ctx)) {
			return false;
		}
		double dx = x1 - x0, dy = y1 - y0;
		double len = Math.sqrt(dx * dx + dy * dy);
		if (len < 0.05) {
			return true;
		}
		float w = Math.max(0.25f, width);
		int alpha = (color >>> 24) & 0xFF;
		if (w < 1f) {
			alpha = Math.round(alpha * w); // 1px보다 가는 선 = 알파를 낮춰 가늘어 보이게(커버리지 AA와 동일)
			w = 1f;
		}
		if (alpha < 2) {
			return true;
		}
		int rgb = color & 0x00FFFFFF;
		int core = (alpha << 24) | rgb;
		int clear = rgb; // 알파 0(같은 색) - 바깥으로 갈수록 투명
		// 단면 = 가운데 (w-1)px 완전 불투명 + 양쪽 1px 경사(삼각/사다리꼴 프로필). w=1이면 경사 둘만 만나는
		// 삼각형(밑변 2px, 시각 굵기 1px) - 예전처럼 1px 코어 + 경사로 그리면 2px로 두꺼워 보였음("너무 두꺼움").
		int hw = Math.round((w - 1f) / 2f * SUB);
		int feather = Math.round(SUB);
		int length = (int) Math.round(len * SUB);
		int ext = hw + feather / 2; // 사각 캡(모서리에서 두 선이 빈틈 없이 만나게)
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, (float) x0, (float) y0);
		LunaCompat.guiRotate(ctx, (float) Math.atan2(dy, dx));
		LunaCompat.guiScale(ctx, 1f / SUB, 1f / SUB);
		if (hw > 0) {
			ctx.fill(-ext, -hw, length + ext, hw, core);
		}
		ctx.fillGradient(-ext, -hw - feather, length + ext, -hw, clear, core);
		ctx.fillGradient(-ext, hw, length + ext, hw + feather, core, clear);
		LunaCompat.guiPop(ctx);
		return true;
	}

	private static void pixel(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int rgb, double alpha) {
		int a = (int) Math.round(Math.max(0, Math.min(255, alpha)));
		if (a < 2 || x1 <= x0 || y1 <= y0) {
			return;
		}
		ctx.fill(x0, y0, x1, y1, (a << 24) | rgb);
	}

	/** Liang-Barsky 선분 클리핑. 완전히 밖이면 null. */
	private static double[] clip(double x0, double y0, double x1, double y1,
			double minX, double minY, double maxX, double maxY) {
		double dx = x1 - x0, dy = y1 - y0;
		double t0 = 0, t1 = 1;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) {
					return null;
				}
				continue;
			}
			double t = q[i] / p[i];
			if (p[i] < 0) {
				if (t > t1) {
					return null;
				}
				if (t > t0) {
					t0 = t;
				}
			} else {
				if (t < t0) {
					return null;
				}
				if (t < t1) {
					t1 = t;
				}
			}
		}
		return new double[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
	}
}
