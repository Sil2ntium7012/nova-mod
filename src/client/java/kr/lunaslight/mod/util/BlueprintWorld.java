package kr.lunaslight.mod.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-266차: 설계도 홀로그램을 월드 안에 진짜 블록 모델로 그린다(리터메티카 방식). 사용자: "그냥 블록 자체를 홀로그램으로 못 띄워?"
 * 화면 위에 덮어 그리던 방식(LunaProjection)은 면 하나하나를 평행사변형 그림으로 흉내 내서 원근, 계단 모양, 잔디 덧칠, 입체감이 다
 * 어긋났다. 이제 블록 모델의 쿼드(바닐라가 블록을 그릴 때 쓰는 그것)를 반투명 레이어에 그대로 넣는다 - 모양, 그림, 바이옴 색,
 * 면 밝기가 실제 블록과 같고, 진짜 블록 뒤에 있으면 깊이 판정으로 가려진다.
 * 월드 렌더 이벤트가 있는 버전(1.15~1.21.11)만. 26.x와 1.14.4, 그리고 여기서 실패한 칸은 예전 화면 방식으로 그린다.
 * 버전마다 다른 API(모델 → 쿼드, 쿼드 그리기, 반투명 레이어)는 리플렉션으로 한 번 찾아 둔다.
 */
public final class BlueprintWorld {
	private BlueprintWorld() {
	}

	/** 블록 상태 하나의 쿼드: 방향별(0~5 = 아래, 위, 북, 남, 서, 동 / 6 = 방향 없음) 쿼드와 쿼드별 색(-1 = 색 없음). */
	public static final class Model {
		final Object[][] quads = new Object[7][];
		final int[][] tints = new int[7][];
	}

	/**
	 * 49-309차: 면(위/아래/옆)에 붙은 쿼드가 하나도 없고 방향 없는 쿼드만 있는 모델 - 잔디, 꽃, 묘목처럼 X자로 엇갈린 판. 이런 블록은
	 * 상자로 덮지 않고 모델 자체를 파랗게 칠한다(상자를 씌우면 풀 위에 네모가 떠 보였다).
	 */
	public static boolean crossLike(Model m) {
		if (m == null) {
			return false;
		}
		for (int d = 0; d < 6; d++) {
			if (m.quads[d] != null && m.quads[d].length > 0) {
				return false;
			}
		}
		return m.quads[6] != null && m.quads[6].length > 0;
	}

	private static boolean broken, resolved, drawResolved, hooked;
	private static Class<?> quadClass;
	private static Method getModel, getQuads, getParts, partQuads, quadDraw, tintIndex, getBuffer, setSeed;
	private static Object random;
	private static boolean quadHasAlpha;
	private static Object layer;

	// 이번 프레임
	private static MatrixStack matrices;
	private static Object consumer;
	private static Vec3d cam;
	private static Sink sink;
	private static Object flushTarget;

	/** 이 버전에서 월드에 그릴 수 있는지(한 번 실패하면 계속 false). */
	public static boolean usable() {
		return !broken && (LunaCompat.worldRenderAvailable() || hooked);
	}

	// ==================== 49-267차: 월드 렌더 이벤트가 없는 옛 버전(1.14.4, 1.15.2) ====================
	// 사용자: "나머지도 다 해줘". Fabric API 0.28.5(1.14/1.15)엔 월드 렌더 이벤트가 없어 버전별 믹스인(compat/blueprint-world14,
	// compat/blueprint-world15)이 월드 그리기 끝 무렵에 설계도 모듈의 onWorldRender를 직접 부른다.
	//  · 1.15.2: MatrixStack / VertexConsumer가 이미 있다 → Ctx15(matrixStack, camera, consumers)를 넘기고, 이벤트 밖이라 end()에서
	//    반투명 레이어를 직접 그려(draw) 비운다.
	//  · 1.14.4: 둘 다 없다(GL 직접 그리기 시대) → Sink(쿼드 하나씩 받아 Tessellator로 그리는 판)를 넘긴다.

	/** 믹스인이 처음 불렸다 - 이 버전도 월드에 그릴 수 있다. */
	public static void markHooked() {
		hooked = true;
	}

	/** 1.15.2 믹스인이 넘기는 이번 프레임 정보(LunaCompat.getMatrices / getWorldRenderCameraPos / call("consumers")가 읽는 이름). */
	public static final class Ctx15 {
		private final Object matrices, camera, consumers;

		public Ctx15(Object matrices, Object camera, Object consumers) {
			this.matrices = matrices;
			this.camera = camera;
			this.consumers = consumers;
		}

		public Object matrixStack() {
			return matrices;
		}

		public Object camera() {
			return camera;
		}

		public Object consumers() {
			return consumers;
		}
	}

	/** 1.14.4: 쿼드를 받아 직접 그리는 판(compat/blueprint-world14). */
	public interface Sink {
		Vec3d cameraPos();

		/** q = BakedQuad, (ox, oy, oz) = 카메라 기준 블록 위치, 색과 진하기, grow = 키우기. */
		void quad(Object q, double ox, double oy, double oz, float r, float g, float b, float a, double grow);

		void flush();
	}

	public static void fail(Throwable t) {
		broken = true;
		LunaCompat.warnOnce("blueprint:world", t);
	}

	// ==================== 준비 ====================

	private static void resolve() throws Exception {
		resolved = true;
		MinecraftClient client = MinecraftClient.getInstance();
		Object brm = client.getBlockRenderManager();
		quadClass = LunaCompat.classForName("net.minecraft.client.render.model.BakedQuad");
		getModel = LunaCompat.getMethodCompat(brm.getClass(), "getModel", BlockState.class);
		for (String n : new String[]{"getTintIndex", "tintIndex", "getColorIndex"}) {
			Method m = LunaCompat.findMethod(quadClass, n);
			if (m != null && m.getReturnType() == int.class) {
				tintIndex = m;
				break;
			}
		}
	}

	/** VertexConsumer로 그리는 길(1.15+)만 필요한 것. */
	private static void resolveDraw() throws Exception {
		drawResolved = true;
		// 쿼드 그리기: VertexConsumer.quad(MatrixStack.Entry, BakedQuad, r, g, b[, a], light, overlay)
		Class<?> vc = LunaCompat.classForName("net.minecraft.client.render.VertexConsumer");
		for (Method m : vc.getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if ((p.length == 7 || p.length == 8) && p[1] == quadClass && p[2] == float.class && p[p.length - 1] == int.class
					&& p[p.length - 2] == int.class) {
				quadDraw = m;
				quadHasAlpha = p.length == 8;
				break;
			}
		}
		if (quadDraw == null) {
			throw new IllegalStateException("VertexConsumer.quad 없음");
		}
		// 반투명 레이어(블록 그림판, 뒷면 안 그림)
		Class<?> trl = LunaCompat.classForName("net.minecraft.client.render.TexturedRenderLayers");
		for (String n : new String[]{"getBlockTranslucentCull", "getItemEntityTranslucentCull", "getEntityTranslucentCull"}) {
			Method m = LunaCompat.findMethod(trl, n);
			if (m != null && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
				layer = m.invoke(null);
				break;
			}
		}
		if (layer == null) {
			throw new IllegalStateException("반투명 레이어 없음");
		}
	}

	/** 모델에서 쿼드 찾는 방법(버전마다 다름)을 처음 모델을 볼 때 정한다. */
	private static void resolveModel(Object model) throws Exception {
		for (Method m : model.getClass().getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 3 && p[0] == BlockState.class && List.class.isAssignableFrom(m.getReturnType())
					&& LunaCompat.nameMatches(model.getClass(), "getQuads", m.getName())) {
				getQuads = m;   // ≤1.21.4: BakedModel.getQuads(state, direction, random)
				random = newRandom(p[2]);
				break;
			}
			if (p.length == 1 && List.class.isAssignableFrom(m.getReturnType())
					&& LunaCompat.nameMatches(model.getClass(), "getParts", m.getName())) {
				getParts = m;   // 1.21.5+: BlockStateModel.getParts(random) → BlockModelPart.getQuads(direction)
				random = newRandom(p[0]);
			}
		}
		if (getQuads == null && getParts == null) {
			throw new IllegalStateException("모델 쿼드 API 없음: " + model.getClass().getName());
		}
		setSeed = LunaCompat.findMethod(random.getClass(), "setSeed", long.class);
		if (setSeed == null) {
			setSeed = random.getClass().getMethod("setSeed", long.class);
		}
	}

	private static Object newRandom(Class<?> type) throws Exception {
		if (type.isAssignableFrom(java.util.Random.class)) {
			return new java.util.Random(42L);
		}
		Method create = LunaCompat.findMethod(type, "create", long.class);
		if (create == null) {
			throw new IllegalStateException("Random.create 없음");
		}
		return create.invoke(null, 42L);
	}

	// ==================== 블록 상태 ====================

	/** 설계도 항목(id + 속성) → 이 버전의 블록 상태. 없는 블록이면 null. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static BlockState stateOf(String id, String props) {
		try {
			Block block = blockById(id);
			if (block == null) {
				return null;
			}
			BlockState st = block.getDefaultState();
			for (Map.Entry<String, String> e : Blueprint.parseProps(props).entrySet()) {
				net.minecraft.state.property.Property p = block.getStateManager().getProperty(e.getKey());
				if (p == null) {
					continue;
				}
				Method parse = LunaCompat.findMethod(net.minecraft.state.property.Property.class, "parse", String.class);
				Object v0 = parse == null ? null : parse.invoke(p, e.getValue());
				java.util.Optional v = v0 instanceof java.util.Optional o ? o : java.util.Optional.empty();
				if (v.isPresent()) {
					st = (BlockState) st.with(p, (Comparable) v.get());
				}
			}
			return st;
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:state", t);
			return null;
		}
	}

	private static Block blockById(String id) {
		String ns = "minecraft", path = id;
		int colon = id.indexOf(':');
		if (colon >= 0) {
			ns = id.substring(0, colon);
			path = id.substring(colon + 1);
		}
		Identifier ident = LunaCompat.identifier(ns, path);
		if (ident == null) {
			return null;
		}
		for (String holder : new String[]{"net.minecraft.registry.Registries", "net.minecraft.util.registry.Registry"}) {
			try {
				Object registry = LunaCompat.getFieldCompat(LunaCompat.classForName(holder), "BLOCK").get(null);
				try {
					Object has = LunaCompat.getMethodCompat(registry.getClass(), "containsId", Identifier.class).invoke(registry, ident);
					if (Boolean.FALSE.equals(has)) {
						return null;
					}
				} catch (Throwable ignored) {
				}
				Object b = LunaCompat.getMethodCompat(registry.getClass(), "get", Identifier.class).invoke(registry, ident);
				return b instanceof Block bl ? bl : null;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/**
	 * 블록 상태의 쿼드를 모은다(색은 pos 자리의 바이옴 색). 그릴 쿼드가 하나도 없으면(상자, 표지판처럼 모델이 따로 그려지는 블록) null -
	 * 그 칸은 화면 방식으로 그린다.
	 */
	@SuppressWarnings("unchecked")
	public static Model model(BlockState state, BlockPos pos) {
		if (broken || state == null) {
			return null;
		}
		try {
			if (!resolved) {
				resolve();
			}
			MinecraftClient client = MinecraftClient.getInstance();
			Object m = getModel.invoke(client.getBlockRenderManager(), state);
			if (m == null) {
				return null;
			}
			if (getQuads == null && getParts == null) {
				resolveModel(m);
			}
			net.minecraft.util.math.Direction[] dirs = net.minecraft.util.math.Direction.values();
			Model out = new Model();
			int total = 0;
			List<Object> parts = null;
			if (getParts != null) {
				setSeed.invoke(random, 42L);
				parts = (List<Object>) getParts.invoke(m, random);
			}
			for (int d = 0; d < 7; d++) {
				Object dir = d < 6 ? dirs[d] : null;
				List<Object> qs = new ArrayList<>();
				if (getQuads != null) {
					setSeed.invoke(random, 42L);
					List<?> l = (List<?>) getQuads.invoke(m, state, dir, random);
					if (l != null) {
						qs.addAll(l);
					}
				} else if (parts != null) {
					for (Object part : parts) {
						if (partQuads == null) {
							partQuads = findPartQuads(part.getClass());
						}
						List<?> l = (List<?>) partQuads.invoke(part, dir);
						if (l != null) {
							qs.addAll(l);
						}
					}
				}
				out.quads[d] = qs.toArray();
				int[] tints = new int[qs.size()];
				for (int i = 0; i < tints.length; i++) {
					int ti = tintIndex == null ? -1 : (int) tintIndex.invoke(qs.get(i));
					tints[i] = ti < 0 ? -1 : 0xFFFFFF & blockColor(client, state, pos, ti);
				}
				out.tints[d] = tints;
				total += tints.length;
			}
			return total == 0 ? null : out;
		} catch (Throwable t) {
			fail(t);
			return null;
		}
	}

	private static Method colorMethod;
	private static Object colors;

	/** 바이옴 색(풀, 잎 등). BlockColors.getColor(state, world, pos, tintIndex) - 1.14~1.15는 꺼내는 이름이 달라 리플렉션. */
	private static int blockColor(MinecraftClient client, BlockState state, BlockPos pos, int ti) throws Exception {
		if (colors == null) {
			colors = LunaCompat.callNoArg(client, "getBlockColors");
			if (colors == null) {
				colors = LunaCompat.callNoArg(client, "getBlockColorMap");
			}
			if (colors == null) {
				return -1;
			}
			for (Method m : colors.getClass().getMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 4 && p[0] == BlockState.class && p[2] == BlockPos.class && p[3] == int.class && m.getReturnType() == int.class) {
					colorMethod = m;
					break;
				}
			}
		}
		return colorMethod == null ? -1 : (int) colorMethod.invoke(colors, state, client.world, pos, ti);
	}

	private static Method findPartQuads(Class<?> cls) {
		for (Method m : cls.getMethods()) {
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 1 && p[0] == net.minecraft.util.math.Direction.class && List.class.isAssignableFrom(m.getReturnType())) {
				m.setAccessible(true);
				return m;
			}
		}
		throw new IllegalStateException("BlockModelPart.getQuads 없음: " + cls.getName());
	}

	// ==================== 그리기 ====================

	/** 이번 프레임 그리기 시작. alpha = 진하기(0~1). false면 이번엔 못 그림. */
	public static boolean begin(Object context, float alpha) {
		if (!usable()) {
			return false;
		}
		try {
			if (!resolved) {
				resolve();
			}
			sink = null;
			flushTarget = null;
			if (context instanceof Sink sk) {
				sink = sk;
				cam = sk.cameraPos();
				return cam != null;
			}
			if (!drawResolved) {
				resolveDraw();
			}
			matrices = LunaCompat.getMatrices(context);
			cam = LunaCompat.getWorldRenderCameraPos(context);
			Object consumers = LunaCompat.call(context, "consumers");
			if (matrices == null || cam == null || consumers == null) {
				return false;
			}
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
					throw new IllegalStateException("getBuffer 없음: " + consumers.getClass().getName());
				}
			}
			Object buf = getBuffer.invoke(consumers, layer);
			if (buf == null) {
				return false;
			}
			consumer = quadHasAlpha ? buf : alphaProxy(buf, alpha);
			if (context instanceof Ctx15) {
				flushTarget = consumers;   // 이벤트 밖에서 그렸으니 끝나면 직접 그려 비운다
			}
			return true;
		} catch (Throwable t) {
			fail(t);
			return false;
		}
	}

	public static void end() {
		try {
			if (sink != null) {
				sink.flush();
			} else if (flushTarget != null && consumer != null) {
				flushLayer(flushTarget);
			}
		} catch (Throwable t) {
			fail(t);
		} finally {
			matrices = null;
			consumer = null;
			sink = null;
			flushTarget = null;
		}
	}

	private static Method drawLayer;

	/** VertexConsumerProvider.Immediate#draw(RenderLayer) - 1.15.2 믹스인 길에서 이번 프레임 것을 바로 그린다. */
	private static void flushLayer(Object consumers) throws Exception {
		if (drawLayer == null || !drawLayer.getDeclaringClass().isInstance(consumers)) {
			drawLayer = null;
			for (Method m : consumers.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && layer != null && m.getParameterTypes()[0].isInstance(layer)
						&& LunaCompat.nameMatches(consumers.getClass(), "draw", m.getName())) {
					m.setAccessible(true);
					drawLayer = m;
					break;
				}
			}
			if (drawLayer == null) {
				throw new IllegalStateException("Immediate.draw(layer) 없음: " + consumers.getClass().getName());
			}
		}
		drawLayer.invoke(consumers, layer);
	}

	/**
	 * 블록 하나. (x, y, z) = 월드 좌표, r/g/b = 색 곱하기(1 = 그대로), cull = 안 그릴 방향 비트(0~5), grow = 0보다 크면 그만큼
	 * 키워서(틀린 블록 자리에 진짜 블록과 겹쳐 깜빡이지 않게).
	 */
	public static void block(Model m, int x, int y, int z, float r, float g, float b, float a, int cull, double grow) throws Exception {
		if (m != null && sink != null && cam != null) {
			double ox = x - cam.x, oy = y - cam.y, oz = z - cam.z;
			for (int d = 0; d < 7; d++) {
				if (d < 6 && (cull >> d & 1) != 0) {
					continue;
				}
				Object[] qs = m.quads[d];
				int[] ts = m.tints[d];
				for (int i = 0; i < qs.length; i++) {
					int t = ts[i];
					float cr = r, cg = g, cb = b;
					if (t >= 0) {
						cr *= ((t >> 16) & 0xFF) / 255f;
						cg *= ((t >> 8) & 0xFF) / 255f;
						cb *= (t & 0xFF) / 255f;
					}
					sink.quad(qs[i], ox, oy, oz, cr, cg, cb, a, grow);
				}
			}
			return;
		}
		if (m == null || matrices == null || consumer == null) {
			return;
		}
		matrices.push();
		try {
			matrices.translate(x - cam.x, y - cam.y, z - cam.z);
			if (grow > 0) {
				float s = (float) (1 + grow * 2);
				matrices.translate(0.5, 0.5, 0.5);
				matrices.scale(s, s, s);
				matrices.translate(-0.5, -0.5, -0.5);
			}
			Object entry = matrices.peek();
			for (int d = 0; d < 7; d++) {
				if (d < 6 && (cull >> d & 1) != 0) {
					continue;
				}
				Object[] qs = m.quads[d];
				int[] ts = m.tints[d];
				for (int i = 0; i < qs.length; i++) {
					int t = ts[i];
					float cr = r, cg = g, cb = b;
					if (t >= 0) {
						cr *= ((t >> 16) & 0xFF) / 255f;
						cg *= ((t >> 8) & 0xFF) / 255f;
						cb *= (t & 0xFF) / 255f;
					}
					if (quadHasAlpha) {
						quadDraw.invoke(consumer, entry, qs[i], cr, cg, cb, a, 0xF000F0, 655360);
					} else {
						quadDraw.invoke(consumer, entry, qs[i], cr, cg, cb, 0xF000F0, 655360);
					}
				}
			}
		} finally {
			matrices.pop();
		}
	}

	// ==================== 알파를 받지 않는 옛 quad(≤1.20.x) ====================
	// 옛 VertexConsumer.quad에는 알파 인자가 없어 늘 1(불투명)로 꼭짓점을 넣는다. 기본 메서드 quad를 이 대리 객체 위에서 돌려
	// 그 안에서 부르는 꼭짓점 한 번에 넣기(vertex 14개 인자)를 가로채 알파만 바꾼다. 나머지는 원래 버퍼로 그대로.

	private static final Map<Method, Integer> KIND = new HashMap<>();

	private static Object alphaProxy(Object delegate, float alpha) {
		Class<?> vc = quadDraw.getDeclaringClass();
		InvocationHandler h = (p, m, args) -> {
			if (m.getDeclaringClass() == Object.class) {
				return switch (m.getName()) {
					case "equals" -> p == args[0];
					case "hashCode" -> System.identityHashCode(p);
					default -> "BlueprintAlpha";
				};
			}
			Integer kind = KIND.get(m);
			if (kind == null) {
				kind = kindOf(vc, m);
				KIND.put(m, kind);
			}
			switch (kind) {
				case 1 -> {
					return InvocationHandler.invokeDefault(p, m, args);   // quad → 그 안의 vertex가 다시 여기로
				}
				case 2 -> args[6] = (Float) args[6] * alpha;
				case 3 -> args[3] = scaleArgb((Integer) args[3], alpha);
				case 4 -> args[3] = Math.round((Integer) args[3] * alpha);
				case 5 -> args[3] = (Float) args[3] * alpha;
				default -> {
				}
			}
			Object r = m.invoke(delegate, args);
			return r == delegate ? p : r;
		};
		return Proxy.newProxyInstance(vc.getClassLoader(), new Class<?>[]{vc}, h);
	}

	/** 1 = quad(기본 메서드), 2 = vertex(…14, 알파 float), 3 = vertex(…11, 색 int), 4 = color(int×4), 5 = color(float×4), 0 = 그대로. */
	private static int kindOf(Class<?> vc, Method m) {
		Class<?>[] p = m.getParameterTypes();
		if (p.length >= 2 && p[1] == quadClass && m.isDefault()) {
			return 1;
		}
		if (p.length == 14 && p[0] == float.class && p[6] == float.class) {
			return 2;
		}
		if (p.length == 11 && p[0] == float.class && p[3] == int.class) {
			return 3;
		}
		if (p.length == 4 && LunaCompat.nameMatches(vc, "color", m.getName())) {
			return p[0] == int.class ? 4 : p[0] == float.class ? 5 : 0;
		}
		return 0;
	}

	private static int scaleArgb(int argb, float alpha) {
		int a = Math.round((argb >>> 24) * alpha);
		return (argb & 0xFFFFFF) | (a << 24);
	}
}
