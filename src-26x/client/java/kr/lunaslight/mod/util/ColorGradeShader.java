package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 49-170차(사용자: "색 보정 전면 수정 - 게임 화면을 따스하게 하거나 진하게, 진짜 사진을 예쁘게 하는 것처럼"):
 * 바닐라 후처리(post effect) 체인에 Luna 셰이더를 끼워 <b>채도·대비·색온도·생동감·선명도·비네트·페이드</b>를 진짜로
 * 계산한다. 예전 방식(반투명 색 막)은 "어떤 색 쪽으로 당기기"만 가능해 흑백·흐림처럼 보였다.
 *
 * <p>버전마다 셰이더 포맷이 셋이라 자산도 세 벌(loom-common이 버전에 맞는 한 벌만 jar에 넣는다):
 * <ul>
 *   <li>UBO(1.21.6+, 26.x): {@code lunaslight:post_effect/grade.json} + {@code shaders/post/grade.fsh}. 값은 패스의
 *       uniform 버퍼(GradeConfig, std140 vec4 3개)를 새 GpuBuffer로 갈아 끼워 넣는다.</li>
 *   <li>uniform(1.21.2~1.21.5): {@code grade_uni}. 패스가 매 프레임 uniform 목록(name, values)을 프로그램에 넣으므로
 *       그 목록을 새 값으로 바꾼다.</li>
 *   <li>옛 program(1.17~1.21.1): {@code lunaslight:shaders/post/grade_legacy.json} + 프로그램은 바닐라 규칙상
 *       {@code minecraft} 네임스페이스({@code shaders/program/luna_grade}). GlUniform에 직접 넣는다.</li>
 * </ul>
 * 셋 다 리플렉션(이름은 yarn/mojang 후보를 모두 시도, 구조는 타입으로 찾음)이고, 어느 단계든 실패하면
 * {@link #available()}이 false가 되어 ColorGradingModule이 예전 색 막으로 돌아간다. 1.16 이하는 시도하지 않는다.
 *
 * <p>바닐라는 카메라 엔티티가 바뀔 때(접속, 관전) 후처리를 지우므로 매 프레임 "지금 걸린 게 우리 것인지" 보고 다시 건다.
 */
public final class ColorGradeShader {

	private ColorGradeShader() {
	}

	private static final String ID_UBO = "grade";
	private static final String ID_UNI = "grade_uni";
	private static final String ID_LEGACY = "shaders/post/grade_legacy.json";

	private static volatile boolean broken;
	private static boolean installed;
	private static Object boundProcessor;   // 값이 들어간 프로세서(바뀌면 다시 넣는다)
	private static final float[] LAST = new float[12];
	private static boolean lastValid;
	private static Object ourBuffer;         // UBO 모드에서 우리가 만든 GpuBuffer(교체 시 닫는다)

	/** 이 버전에서 셰이더 보정을 시도할 수 있는가(실패해서 꺼진 뒤에는 false). */
	public static boolean available() {
		return !broken && LunaVersion.isWithin("1.17", null);
	}

	private static boolean modern() {
		return LunaVersion.isWithin("1.21.2", null);
	}

	/** 매 프레임(HUD 그리기 직전) - 켜져 있으면 후처리를 걸고 값을 넣고, 꺼져 있으면 뗀다. p = P0(4), P1(4), P2(4). */
	public static void apply(Minecraft client, boolean on, float[] p) {
		if (!available() || client == null || client.gameRenderer == null) {
			return;
		}
		try {
			if (!on) {
				if (installed) {
					uninstall(client);
				}
				return;
			}
			if (modern()) {
				applyModern(client, p);
			} else {
				applyLegacy(client, p);
			}
		} catch (Throwable t) {
			broken = true;
			LunaCompat.warnOnce("colorGradeShader", t);
			try {
				uninstall(client);
			} catch (Throwable ignored) {
			}
		}
	}

	/**
	 * 49-215차: 26.3은 GameRenderer가 후처리를 한 개만 거는 게 아니라 <b>매 프레임 목록</b>(requestedPostEffects)을 새로
	 * 만든다 - setPostEffect/currentPostEffect가 없어졌다(javap 실측). 그래서 26.3에서는 이 값을 채워 두면
	 * GameRendererPostMixin이 GameRenderer#update 끝에서 목록에 더한다. null이면 안 건다.
	 */
	public static volatile Identifier requestedPostEffect;

	private static boolean requestListMode(Object gr) {
		try {
			gr.getClass().getMethod("getRequestedPostEffects");
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	public static void uninstall(Minecraft client) {
		requestedPostEffect = null;
		installed = false;
		boundProcessor = null;
		lastValid = false;
		if (client == null || client.gameRenderer == null) {
			return;
		}
		try {
			Object gr = client.gameRenderer;
			if (modern()) {
				Object cur = call(gr, new String[]{"getPostProcessorId", "currentPostEffect"});
				if (cur != null && isOurs(cur)) {
					call(gr, new String[]{"clearPostProcessor", "clearPostEffect"});
				}
			} else {
				Object cur = fieldByTypeName(gr, "PostEffectProcessor", "ShaderEffect", "PostChain");
				if (cur != null && legacyOurs(cur)) {
					call(gr, new String[]{"disablePostProcessor", "disableShader", "shutdownEffect"});
				}
			}
		} catch (Throwable ignored) {
		}
		closeOurBuffer();
	}

	private static boolean isOurs(Object id) {
		String s = String.valueOf(id);
		return s.startsWith("lunaslight:") && (s.endsWith(ID_UBO) || s.endsWith(ID_UNI) || s.endsWith(ID_LEGACY));
	}

	// ==================== 1.21.2+ ====================

	private static void applyModern(Minecraft client, float[] p) throws Exception {
		Object gr = client.gameRenderer;
		Object cur = call(gr, new String[]{"getPostProcessorId", "currentPostEffect"});
		Object loader = call(client, new String[]{"getShaderLoader", "getShaderManager"});
		if (loader == null) {
			throw new IllegalStateException("shader loader 없음");
		}
		// 포맷은 버전으로 정한다(1.21.6+ / 26.x = UBO, 1.21.2~1.21.5 = uniform 목록). 다른 쪽 json은 jar에 없다.
		Identifier want = LunaCompat.identifier("lunaslight", LunaVersion.isWithin("1.21.6", null) ? ID_UBO : ID_UNI);
		Object processor = loadPostEffect(loader, want);
		if (processor == null || want == null) {
			throw new IllegalStateException("후처리 json을 못 읽음(grade/grade_uni)");
		}
		if (requestListMode(gr)) {
			requestedPostEffect = want;
			installed = true;
		} else if (cur == null || !want.equals(cur)) {
			Method set = findAny(gr.getClass(), new String[]{"setPostProcessor", "setPostEffect"}, Identifier.class);
			if (set == null) {
				throw new IllegalStateException("setPostProcessor 없음");
			}
			set.invoke(gr, want);
			installed = true;
		}
		if (processor != boundProcessor || !lastValid || !Arrays.equals(LAST, p)) {
			pushModern(processor, p);
			boundProcessor = processor;
			System.arraycopy(p, 0, LAST, 0, 12);
			lastValid = true;
		}
	}

	private static Object loadPostEffect(Object loader, Identifier id) {
		try {
			Method m = findAny(loader.getClass(), new String[]{"loadPostEffect", "getPostChain"}, Identifier.class, Set.class);
			if (m == null) {
				return null;
			}
			return m.invoke(loader, id, Set.of(LunaCompat.identifier("minecraft", "main")));
		} catch (Throwable t) {
			return null;
		}
	}

	/** 프로세서 안의 패스 목록에서 우리 셰이더 패스(값 자리가 있는 것)를 찾아 값을 넣는다. */
	private static void pushModern(Object processor, float[] p) throws Exception {
		List<?> passes = listField(processor);
		if (passes == null) {
			throw new IllegalStateException("passes 없음");
		}
		boolean done = false;
		for (Object pass : passes) {
			// UBO: Map<String, GpuBuffer>
			for (Field mapF : fieldsOfType(pass.getClass(), Map.class)) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) mapF.get(pass);
				if (map == null || !map.containsKey("GradeConfig")) {
					continue;
				}
				Object old = map.get("GradeConfig");
				Object buf = createUbo(p);
				try {
					map.put("GradeConfig", buf);
				} catch (UnsupportedOperationException e) {
					Map<String, Object> copy = new java.util.HashMap<>(map);
					copy.put("GradeConfig", buf);
					mapF.set(pass, copy);
				}
				closeBuffer(ourBuffer);
				if (old != buf) {
					closeBuffer(old);   // 목록에서 빠졌으니 프로세서가 닫아 줄 일이 없다 - 여기서 닫는다
				}
				ourBuffer = buf;
				done = true;
				break;
			}
			if (done) {
				break;
			}
			// uniform 목록: List<record(String name, List<Float> values)> - 샘플러 목록 등 다른 List도 있으니 전부 본다
			for (Field listF : fieldsOfType(pass.getClass(), List.class)) {
				List<?> list = (List<?>) listF.get(pass);
				if (list == null || list.isEmpty() || !hasName(list, "P0")) {
					continue;
				}
				List<Object> out = new ArrayList<>();
				for (Object u : list) {
					String name = String.valueOf(call(u, new String[]{"name"}));
					float[] v = name.equals("P0") ? Arrays.copyOfRange(p, 0, 4)
							: name.equals("P1") ? Arrays.copyOfRange(p, 4, 8)
							: name.equals("P2") ? Arrays.copyOfRange(p, 8, 12) : null;
					if (v == null) {
						out.add(u);
						continue;
					}
					List<Float> vals = new ArrayList<>();
					for (float f : v) {
						vals.add(f);
					}
					java.lang.reflect.Constructor<?> c = u.getClass().getDeclaredConstructor(String.class, List.class);
					c.setAccessible(true);
					out.add(c.newInstance(name, vals));
				}
				try {
					listF.set(pass, out);
				} catch (Throwable t) {
					throw new IllegalStateException("uniform 목록 교체 실패: " + t);
				}
				done = true;
				break;
			}
			if (done) {
				break;
			}
		}
		if (!done) {
			throw new IllegalStateException("값을 넣을 패스를 못 찾음");
		}
	}

	private static boolean hasName(List<?> list, String name) {
		for (Object u : list) {
			try {
				if (name.equals(String.valueOf(call(u, new String[]{"name"})))) {
					return true;
				}
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	/** std140: vec4 x3 = 48바이트. */
	private static Object createUbo(float[] p) throws Exception {
		Class<?> rs = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
		Object device = rs.getMethod("getDevice").invoke(null);
		ByteBuffer bb = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
		for (int i = 0; i < 12; i++) {
			bb.putFloat(p[i]);
		}
		bb.flip();
		int usage = 128; // GpuBuffer.USAGE_UNIFORM
		try {
			Class<?> gb = kr.lunaslight.mod.util.LunaCompat.requireClass("com.mojang.blaze3d.buffers.GpuBuffer", "com.mojang.renderpearl.api.buffers.GpuBuffer");
			usage = gb.getField("USAGE_UNIFORM").getInt(null);
		} catch (Throwable ignored) {
		}
		for (Method m : device.getClass().getMethods()) {
			if (!m.getName().equals("createBuffer") || m.getParameterCount() != 3) {
				continue;
			}
			Class<?>[] pt = m.getParameterTypes();
			if (pt[0] == java.util.function.Supplier.class && pt[1] == int.class && pt[2] == ByteBuffer.class) {
				java.util.function.Supplier<String> name = () -> "Nova 색 보정";
				return m.invoke(device, name, usage, bb);
			}
		}
		throw new IllegalStateException("createBuffer(Supplier,int,ByteBuffer) 없음");
	}

	private static void closeOurBuffer() {
		Object b = ourBuffer;
		ourBuffer = null;
		closeBuffer(b);
	}

	/** GpuBuffer 닫기 - 이미 닫힌 것(프로세서가 리로드로 먼저 닫은 우리 버퍼)은 건너뛴다. */
	private static void closeBuffer(Object b) {
		if (!(b instanceof AutoCloseable c)) {
			return;
		}
		try {
			Object closed = call(b, new String[]{"isClosed"});
			if (closed instanceof Boolean bo && bo) {
				return;
			}
			c.close();
		} catch (Throwable ignored) {
		}
	}

	// ==================== 1.17 ~ 1.21.1 ====================

	private static boolean legacyOurs(Object processor) {
		try {
			Object name = call(processor, new String[]{"getName"});
			return name != null && String.valueOf(name).contains("grade_legacy");
		} catch (Throwable t) {
			return false;
		}
	}

	private static void applyLegacy(Minecraft client, float[] p) throws Exception {
		Object gr = client.gameRenderer;
		Object cur = fieldByTypeName(gr, "PostEffectProcessor", "ShaderEffect", "PostChain");
		if (cur == null || !legacyOurs(cur)) {
			Method load = findAny(gr.getClass(), new String[]{"loadPostProcessor", "loadShader", "loadEffect"}, Identifier.class);
			if (load == null) {
				throw new IllegalStateException("loadPostProcessor 없음");
			}
			load.invoke(gr, LunaCompat.identifier("lunaslight", ID_LEGACY));
			cur = fieldByTypeName(gr, "PostEffectProcessor", "ShaderEffect", "PostChain");
			if (cur == null || !legacyOurs(cur)) {
				throw new IllegalStateException("후처리 json을 못 읽음(grade_legacy)");
			}
			installed = true;
			// 바닐라는 loadPostProcessor 뒤 postProcessorEnabled를 true로 둔다
		}
		if (cur != boundProcessor || !lastValid || !Arrays.equals(LAST, p)) {
			List<Object> passes = new ArrayList<>();
			for (Field f : fieldsOfType(cur.getClass(), List.class)) {
				Object v = f.get(cur);
				if (v instanceof List<?> l) {
					passes.addAll(l);
				}
			}
			boolean done = false;
			for (Object pass : passes) {
				Object program;
				try {
					program = call(pass, new String[]{"getProgram", "getEffect", "getShader"});
				} catch (Throwable t) {
					continue;
				}
				if (program == null) {
					continue;
				}
				Object u0 = callWith(program, new String[]{"getUniformByName", "getUniform"}, "P0");
				if (u0 == null) {
					continue;
				}
				setVec4(u0, p, 0);
				setVec4(callWith(program, new String[]{"getUniformByName", "getUniform"}, "P1"), p, 4);
				setVec4(callWith(program, new String[]{"getUniformByName", "getUniform"}, "P2"), p, 8);
				done = true;
				break;
			}
			if (!done) {
				throw new IllegalStateException("P0 uniform이 있는 패스를 못 찾음");
			}
			boundProcessor = cur;
			System.arraycopy(p, 0, LAST, 0, 12);
			lastValid = true;
		}
	}

	private static void setVec4(Object uniform, float[] p, int off) throws Exception {
		if (uniform == null) {
			return;
		}
		Method set = null;
		for (Method m : uniform.getClass().getMethods()) {
			if (m.getParameterCount() == 4 && m.getReturnType() == void.class) {
				Class<?>[] pt = m.getParameterTypes();
				if (pt[0] == float.class && pt[1] == float.class && pt[2] == float.class && pt[3] == float.class) {
					set = m;
					break;
				}
			}
		}
		if (set == null) {
			throw new IllegalStateException("GlUniform.set(ffff) 없음");
		}
		set.invoke(uniform, p[off], p[off + 1], p[off + 2], p[off + 3]);
	}

	// ==================== 리플렉션 도우미(이름은 yarn/mojang 둘 다 시도, 구조는 타입으로) ====================

	private static Method findAny(Class<?> cls, String[] names, Class<?>... params) {
		for (String n : names) {
			Method m = LunaCompat.findAnyMethod(cls, n, params);
			if (m != null) {
				return m;
			}
		}
		return null;
	}

	private static Object call(Object target, String[] names) throws Exception {
		Method m = findAny(target.getClass(), names);
		if (m == null) {
			return null;
		}
		return m.invoke(target);
	}

	private static Object callWith(Object target, String[] names, String arg) throws Exception {
		Method m = findAny(target.getClass(), names, String.class);
		if (m == null) {
			return null;
		}
		return m.invoke(target, arg);
	}

	/** owner의 필드 중 타입 이름(단순 이름, 매핑 무관하게 yarn/mojang 후보)이 맞는 첫 필드 값. 런타임 이름이 intermediary면 classOrNull로 푼다. */
	private static Object fieldByTypeName(Object owner, String... simpleNames) throws Exception {
		Class<?>[] wanted = new Class<?>[]{
				LunaCompat.classOrNull("net.minecraft.client.gl.PostEffectProcessor"),
				LunaCompat.classOrNull("net.minecraft.client.gl.ShaderEffect"),
				LunaCompat.classOrNull("net.minecraft.client.renderer.PostChain")};
		for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				boolean match = false;
				for (Class<?> w : wanted) {
					if (w != null && f.getType() == w) {
						match = true;
						break;
					}
				}
				if (!match) {
					for (String n : simpleNames) {
						if (f.getType().getSimpleName().equals(n)) {
							match = true;
							break;
						}
					}
				}
				if (match) {
					f.setAccessible(true);
					return f.get(owner);
				}
			}
		}
		return null;
	}

	private static List<?> listField(Object owner) throws Exception {
		List<Field> fs = fieldsOfType(owner.getClass(), List.class);
		return fs.isEmpty() ? null : (List<?>) fs.get(0).get(owner);
	}

	private static List<Field> fieldsOfType(Class<?> cls, Class<?> type) {
		List<Field> out = new ArrayList<>();
		for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
					f.setAccessible(true);
					out.add(f);
				}
			}
		}
		return out;
	}
}
