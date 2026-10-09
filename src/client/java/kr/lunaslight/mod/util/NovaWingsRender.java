package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 49-270차: 노바 날개 그리기(본 트리 1.15~1.21.11). 월드 그리기 이벤트(WingsModule.onWorldRender, 1.15.2는 BlueprintWorld15Mixin)에서
 * 주변 플레이어마다 {@link NovaWings#build}로 만든 꼭짓점을 엔티티 반투명 레이어(날개 그림, 양면)에 넣는다. 플레이어 렌더러를
 * 버전마다 건드리지 않으려고 몸 위치, 몸 방향, 웅크림만 읽어 직접 붙인다(자는 중, 수영 자세, 겉날개 비행은 안 그린다).
 */
public final class NovaWingsRender {
	private NovaWingsRender() {
	}

	private static final float[] BUF = new float[8 * 4 * 64];
	private static final Map<Identifier, Object> LAYERS = new HashMap<>();
	private static boolean broken;
	private static Method getBuffer, vertex, next, entryMatrix, getLight, drawLayer;
	private static boolean vertexEntry;
	private static Field lastX, lastY, lastZ, bodyYaw, prevBodyYaw;

	public static void fail(Throwable t) {
		broken = true;
		LunaCompat.warnOnce("wings:render", t);
	}

	/** 이번 프레임 날개들. flush = 이벤트 밖(1.15.2 믹스인)이라 끝나고 바로 그려 비운다. */
	public static void draw(Object context, boolean others, boolean flush) {
		if (broken) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) {
			return;
		}
		try {
			MatrixStack matrices = LunaCompat.getMatrices(context);
			Vec3d cam = LunaCompat.getWorldRenderCameraPos(context);
			Object consumers = LunaCompat.call(context, "consumers");
			if (matrices == null || cam == null || consumers == null) {
				return;
			}
			float td = tickDelta(mc);
			boolean firstPerson = firstPerson(mc);
			double time = (System.nanoTime() / 1e9);
			java.util.Set<Object> used = flush ? new java.util.HashSet<>() : null;
			for (Object o : mc.world.getPlayers()) {
				if (!(o instanceof Entity p)) {
					continue;
				}
				if (p == mc.player && firstPerson && mc.getCameraEntity() == p) {
					continue;
				}
				if (p.isInvisible() || flag(p, "isSleeping") || flag(p, "isInSwimmingPose") || flag(p, "isFallFlying") || flag(p, "isGliding")) {
					continue;
				}
				if (AfkWatch.afkEntity(p)) {
					continue;   // 49-312차: 자리 비움이면 치장(날개) 숨김
				}
				String key = NovaWings.keyFor(p.getUuid().toString(), others);
				if (key == null) {
					continue;
				}
				NovaWings.Asset a = NovaWings.asset(key);
				Identifier tex = NovaWings.texture(a);
				if (tex == null) {
					continue;
				}
				Vec3d now = LunaCompat.getPos(p);
				if (now == null) {
					continue;
				}
				resolveFields(p);
				double px = lerp(td, lastX == null ? now.x : lastX.getDouble(p), now.x);
				double py = lerp(td, lastY == null ? now.y : lastY.getDouble(p), now.y);
				double pz = lerp(td, lastZ == null ? now.z : lastZ.getDouble(p), now.z);
				if ((px - cam.x) * (px - cam.x) + (pz - cam.z) * (pz - cam.z) > 96 * 96) {
					continue;
				}
				float yaw = bodyYaw == null ? 0 : bodyYaw.getFloat(p);
				float prev = prevBodyYaw == null ? yaw : prevBodyYaw.getFloat(p);
				float by = prev + wrap(yaw - prev) * td;
				// 49-272차: 웅크린 "자세"만 본다(쉬프트를 눌러도 날거나 자리가 좁으면 자세가 다르다). 자세 메서드가 없을 때만 쉬프트.
				Object pose = LunaCompat.callNoArg(p, "isInSneakingPose");
				boolean sneak = pose instanceof Boolean b ? b : flag(p, "isSneaking");
				int n = NovaWings.build(a, time + (p.getUuid().hashCode() & 0xFF) / 97.0, by, sneak, px - cam.x, py - cam.y, pz - cam.z, BUF);
				if (n == 0) {
					continue;
				}
				Object layer = layer(tex);
				if (layer == null) {
					return;
				}
				VertexConsumer vc = buffer(consumers, layer);
				if (vc == null) {
					return;
				}
				int light = light(mc, p, td);
				for (int i = 0; i < n; i++) {
					int b = i * 8;
					vertex(vc, matrices, BUF[b], BUF[b + 1], BUF[b + 2]);
					vc.color(1f, 1f, 1f, 1f);
					vc.texture(BUF[b + 3], BUF[b + 4]);
					vc.overlay(655360);
					vc.light(light);
					vc.normal(BUF[b + 5], BUF[b + 6], BUF[b + 7]);
					if (next != null) {
						next.invoke(vc);
					}
				}
				if (used != null) {
					used.add(layer);
				}
			}
			if (used != null) {
				for (Object layer : used) {
					flushLayer(consumers, layer);
				}
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static double lerp(float t, double a, double b) {
		return a + (b - a) * t;
	}

	private static float wrap(float d) {
		d %= 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	private static boolean flag(Object e, String name) {
		return Boolean.TRUE.equals(LunaCompat.callNoArg(e, name));
	}

	private static boolean firstPerson(MinecraftClient mc) {
		try {
			Object p = LunaCompat.getPerspective(mc.options);
			if (p instanceof Number n) {
				return n.intValue() == 0;
			}
			Object f = LunaCompat.callNoArg(p, "isFirstPerson");
			return !(f instanceof Boolean b) || b;
		} catch (Throwable t) {
			return true;
		}
	}

	private static float tickDelta(MinecraftClient mc) {
		Object counter = LunaCompat.callNoArg(mc, "getRenderTickCounter");
		if (counter != null) {
			return LunaCompat.getTickDelta(counter);
		}
		Object f = LunaCompat.callNoArg(mc, "getTickDelta");
		return f instanceof Float v ? v : 1f;
	}

	private static Field field(Class<?> c, String... names) {
		for (String n : names) {
			try {
				Field f = LunaCompat.getFieldCompat(c, n);
				f.setAccessible(true);
				return f;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static boolean fieldsResolved;

	private static void resolveFields(Entity p) {
		if (fieldsResolved) {
			return;
		}
		fieldsResolved = true;
		lastX = field(Entity.class, "lastRenderX", "prevX", "lastX");
		lastY = field(Entity.class, "lastRenderY", "prevY", "lastY");
		lastZ = field(Entity.class, "lastRenderZ", "prevZ", "lastZ");
		Class<?> living = LunaCompat.classOrNull("net.minecraft.entity.LivingEntity");
		if (living != null) {
			bodyYaw = field(living, "bodyYaw", "field_6283");
			prevBodyYaw = field(living, "lastBodyYaw", "prevBodyYaw", "field_6220");
		}
	}

	private static int light(MinecraftClient mc, Entity p, float td) {
		try {
			Object disp = LunaCompat.callNoArg(mc, "getEntityRenderDispatcher");
			if (disp == null) {
				disp = LunaCompat.callNoArg(mc, "getEntityRenderManager");
			}
			if (disp == null) {
				return 0xF000F0;
			}
			if (getLight == null) {
				for (Method m : disp.getClass().getMethods()) {
					Class<?>[] t = m.getParameterTypes();
					if (t.length == 2 && t[1] == float.class && t[0].isAssignableFrom(Entity.class) && m.getReturnType() == int.class
							&& LunaCompat.nameMatches(disp.getClass(), "getLight", m.getName())) {
						getLight = m;
						break;
					}
				}
				if (getLight == null) {
					return 0xF000F0;
				}
			}
			return (int) getLight.invoke(disp, p, td);
		} catch (Throwable t) {
			return 0xF000F0;
		}
	}

	private static Object layer(Identifier tex) throws Exception {
		Object hit = LAYERS.get(tex);
		if (hit != null) {
			return hit;
		}
		Object layer = null;
		Class<?> rl = LunaCompat.classOrNull("net.minecraft.client.render.RenderLayer");
		Method m = rl == null ? null : LunaCompat.findMethod(rl, "getEntityTranslucent", Identifier.class);
		if (m == null) {
			Class<?> rls = LunaCompat.classOrNull("net.minecraft.client.render.RenderLayers");
			m = rls == null ? null : LunaCompat.findMethod(rls, "entityTranslucent", Identifier.class);
		}
		if (m == null) {
			throw new IllegalStateException("entityTranslucent 레이어 없음");
		}
		layer = m.invoke(null, tex);
		LAYERS.put(tex, layer);
		return layer;
	}

	private static VertexConsumer buffer(Object consumers, Object layer) throws Exception {
		if (getBuffer == null || !getBuffer.getDeclaringClass().isInstance(consumers)) {
			getBuffer = null;
			for (Method m : consumers.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && LunaCompat.nameMatches(consumers.getClass(), "getBuffer", m.getName())) {
					m.setAccessible(true);
					getBuffer = m;
					break;
				}
			}
			if (getBuffer == null) {
				throw new IllegalStateException("getBuffer 없음");
			}
		}
		Object b = getBuffer.invoke(consumers, layer);
		if (b instanceof VertexConsumer vc) {
			resolveVertex(vc);
			return vc;
		}
		return null;
	}

	private static Class<?> vertexFor;

	private static void resolveVertex(VertexConsumer vc) throws Exception {
		Class<?> cls = vc.getClass();
		if (vertexFor == cls) {
			return;
		}
		vertex = null;
		next = null;
		entryMatrix = null;
		Class<?> entryCls = LunaCompat.classOrNull("net.minecraft.client.util.math.MatrixStack$Entry");
		for (Method m : cls.getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (p.length != 4 || p[1] != float.class || p[2] != float.class || p[3] != float.class
					|| !LunaCompat.nameMatches(cls, "vertex", m.getName())) {
				continue;
			}
			if (entryCls != null && p[0] == entryCls) {
				vertex = m;
				vertexEntry = true;
				break;
			}
			if (p[0].getName().endsWith("Matrix4f")) {
				vertex = m;
				vertexEntry = false;
			}
		}
		if (vertex == null) {
			throw new IllegalStateException("vertex(행렬, x, y, z) 없음: " + cls.getName());
		}
		vertex.setAccessible(true);
		for (Method m : cls.getMethods()) {
			if (m.getParameterCount() == 0 && m.getReturnType() == void.class && LunaCompat.nameMatches(cls, "next", m.getName())) {
				next = m;
				break;
			}
		}
		vertexFor = cls;
	}

	private static void vertex(VertexConsumer vc, MatrixStack matrices, float x, float y, float z) throws Exception {
		Object peek = matrices.peek();
		if (vertexEntry) {
			vertex.invoke(vc, peek, x, y, z);
			return;
		}
		if (entryMatrix == null) {
			for (String name : new String[]{"getPositionMatrix", "getModel"}) {
				entryMatrix = LunaCompat.findMethod(peek.getClass(), name);
				if (entryMatrix != null) {
					break;
				}
			}
			if (entryMatrix == null) {
				throw new IllegalStateException("위치 행렬 없음");
			}
		}
		vertex.invoke(vc, entryMatrix.invoke(peek), x, y, z);
	}

	private static void flushLayer(Object consumers, Object layer) throws Exception {
		if (drawLayer == null || !drawLayer.getDeclaringClass().isInstance(consumers)) {
			drawLayer = null;
			for (Method m : consumers.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(layer)
						&& LunaCompat.nameMatches(consumers.getClass(), "draw", m.getName())) {
					m.setAccessible(true);
					drawLayer = m;
					break;
				}
			}
			if (drawLayer == null) {
				return;
			}
		}
		drawLayer.invoke(consumers, layer);
	}
}
