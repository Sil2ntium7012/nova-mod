package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-255차: 설계도 홀로그램에 입힐 블록 그림(사용자: "홀로그램에 무슨 블록인지 보여야지 - 라이트매티카처럼").
 *
 * <p>블록 id에서 textures/block/&lt;이름&gt;.png 후보를 만들어(계단/반 블록/담은 재료 블록, 원목 나무는 통나무 등) 리소스에 실제로 있는
 * 첫 것을 쓴다. 모델/아틀라스 API는 버전마다 이름이 다 달라서 쓰지 않는다 - 그림 파일은 1.14부터 같은 자리에 있다.
 * PNG 머리에서 가로·세로를 읽어 리소스팩 고해상도와 움직이는 그림(세로로 긴 띠, 첫 칸만 씀)도 맞게 그린다.
 * 상자, 침대처럼 그림 파일이 따로 없는 블록은 null(호출부가 색 칸으로 그림).
 */
public final class BlueprintTex {
	private BlueprintTex() {
	}

	public static final class Tex {
		public final Identifier id;
		/** 그림 가로(= 한 칸 크기), 파일 세로(움직이는 그림이면 가로의 몇 배). */
		public final int w, h;
		/** 색 곱하기(0xRRGGBB) - 잎, 풀 같은 바이옴 색 블록. 없으면 0xFFFFFF. */
		public final int tint;

		Tex(Identifier id, int w, int h, int tint) {
			this.id = id;
			this.w = w;
			this.h = h;
			this.tint = tint;
		}
	}

	/** [옆면, 윗면, 아랫면(없으면 null)]. */
	private static final Map<String, Tex[]> CACHE = new HashMap<>();
	private static final Tex[] NONE = new Tex[3];

	public static void clear() {
		CACHE.clear();
	}

	/** 블록 id("minecraft:oak_stairs") → [옆면, 윗면] 그림. 못 찾으면 둘 다 null. */
	public static Tex[] of(String blockId) {
		if (blockId == null || blockId.isEmpty()) {
			return NONE;
		}
		Tex[] hit = CACHE.get(blockId);
		if (hit != null) {
			return hit;
		}
		Tex[] out = NONE;
		try {
			int colon = blockId.indexOf(':');
			String ns = colon < 0 ? "minecraft" : blockId.substring(0, colon);
			String path = colon < 0 ? blockId : blockId.substring(colon + 1);
			Tex side = null;
			for (String c : sideCandidates(path)) {
				side = load(ns, c);
				if (side != null) {
					break;
				}
			}
			if (side != null) {
				Tex top = null;
				for (String c : topCandidates(path)) {
					top = load(ns, c);
					if (top != null) {
						break;
					}
				}
				// 49-261차: 아랫면(잔디 블록 바닥은 흙 등). 없으면 윗면 그림을 쓴다(호출부).
				Tex bottom = null;
				for (String c : bottomCandidates(path)) {
					bottom = load(ns, c);
					if (bottom != null) {
						break;
					}
				}
				out = new Tex[]{side, top == null ? side : top, bottom};
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:tex", t);
		}
		CACHE.put(blockId, out);
		return out;
	}

	// ==================== 후보 이름 ====================

	private static final String[] SHAPES = {"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button"};

	private static String strip(String p) {
		for (String pre : new String[]{"waxed_", "infested_"}) {
			if (p.startsWith(pre)) {
				p = p.substring(pre.length());
			}
		}
		return p;
	}

	static List<String> sideCandidates(String raw) {
		List<String> out = new ArrayList<>();
		String p = strip(raw);
		out.add(p);
		out.add(p + "_side");
		out.add(p + "_front");
		if (p.endsWith("_door")) {
			out.add(p + "_bottom");
		}
		if (p.endsWith("_wood")) {
			out.add(p.substring(0, p.length() - 5) + "_log");
		}
		if (p.endsWith("_hyphae")) {
			out.add(p.substring(0, p.length() - 7) + "_stem");
		}
		if (p.endsWith("_carpet")) {
			String b = p.substring(0, p.length() - 7);
			out.add(b + "_wool");
			out.add(b + "_block");
		}
		if (p.endsWith("_pane")) {
			out.add(p.substring(0, p.length() - 5));
		}
		if (p.endsWith("_wall_torch")) {
			out.add(p.replace("_wall_torch", "_torch"));
		}
		for (String sh : SHAPES) {
			if (p.endsWith(sh)) {
				String b = p.substring(0, p.length() - sh.length());
				addBase(out, b);
				break;
			}
		}
		out.add(p + "_top");
		return out;
	}

	private static void addBase(List<String> out, String b) {
		out.add(b);
		out.add(b + "s");               // brick → bricks, stone_brick → stone_bricks
		out.add(b + "_planks");         // oak → oak_planks
		out.add(b + "_block");          // purpur → purpur_block
		out.add(b + "_block_side");     // quartz → quartz_block_side
		out.add(b + "_side");
		if (b.startsWith("smooth_")) {
			String r = b.substring(7);
			out.add(r + "_top");            // smooth_sandstone → sandstone_top
			out.add(r + "_block_bottom");   // smooth_quartz → quartz_block_bottom
		}
		if (b.startsWith("cut_") && b.endsWith("sandstone")) {
			out.add(b);
		}
		out.add(b + "_top");
	}

	static List<String> bottomCandidates(String raw) {
		List<String> out = new ArrayList<>();
		String p = strip(raw);
		if (p.equals("grass_block") || p.equals("podzol") || p.equals("mycelium") || p.equals("dirt_path") || p.equals("farmland")) {
			out.add("dirt");
		} else if (p.endsWith("_nylium")) {
			out.add("netherrack");
		} else {
			out.add(p + "_bottom");
		}
		return out;
	}

	static List<String> topCandidates(String raw) {
		List<String> out = new ArrayList<>();
		String p = strip(raw);
		out.add(p + "_top");
		if (p.endsWith("_wood")) {
			out.add(p.substring(0, p.length() - 5) + "_log");   // 원목 나무는 사면이 껍질
		}
		for (String sh : SHAPES) {
			if (p.endsWith(sh)) {
				String b = p.substring(0, p.length() - sh.length());
				out.add(b + "_top");
				out.add(b + "_block_top");
				break;
			}
		}
		return out;
	}

	private static int tintOf(String name) {
		if (name.contains("leaves") && !name.contains("cherry") && !name.contains("azalea") && !name.contains("pale_oak")) {
			if (name.contains("spruce")) {
				return 0x619961;
			}
			if (name.contains("birch")) {
				return 0x80A755;
			}
			return 0x59AE30;
		}
		if (name.startsWith("leaf_litter")) {
			return 0xA9804B;   // 49-260차: 낙엽 더미(마른 잎 색 - 바이옴 색 대신 대표값)
		}
		if (name.equals("grass_block_top") || name.equals("short_grass") || name.equals("grass") || name.equals("tall_grass_top")
				|| name.equals("tall_grass_bottom") || name.equals("fern") || name.startsWith("large_fern") || name.equals("vine")
				|| name.equals("lily_pad")) {
			return 0x7CBD6B;
		}
		return 0xFFFFFF;
	}

	// ==================== 파일 ====================

	private static Tex load(String ns, String name) {
		Identifier id = LunaCompat.identifier(ns, "textures/block/" + name + ".png");
		int[] wh = pngSize(id);
		if (wh == null || wh[0] <= 0 || wh[1] < wh[0]) {
			return null;
		}
		return new Tex(id, wh[0], wh[1], tintOf(name));
	}

	private static Method getResource;

	/** 리소스 PNG의 [가로, 세로]. 없으면 null. getResource가 버전마다 Optional이거나 예외를 던지고, 스트림 메서드 이름도 달라 반사로. */
	static int[] pngSize(Identifier id) {
		Object res = null;
		try {
			Minecraft client = Minecraft.getInstance();
			Object manager = client == null ? null : client.getResourceManager();
			if (manager == null) {
				return null;
			}
			if (getResource == null) {
				for (Method m : manager.getClass().getMethods()) {
					if (m.getParameterCount() == 1 && LunaCompat.nameMatches(manager.getClass(), "getResource", m.getName())
							&& m.getParameterTypes()[0].isInstance(id)) {
						m.setAccessible(true);
						getResource = m;
						break;
					}
				}
				if (getResource == null) {
					return null;
				}
			}
			res = getResource.invoke(manager, id);
			if (res instanceof java.util.Optional<?> opt) {
				res = opt.orElse(null);
			}
			if (res == null) {
				return null;
			}
			Method open = null;
			for (Method m : res.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && InputStream.class.isAssignableFrom(m.getReturnType())) {
					open = m;
					break;
				}
			}
			if (open == null) {
				return null;
			}
			open.setAccessible(true);
			try (InputStream in = (InputStream) open.invoke(res)) {
				byte[] head = in.readNBytes(24);
				if (head.length < 24 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
					return null;
				}
				return new int[]{beInt(head, 16), beInt(head, 20)};
			}
		} catch (Throwable t) {
			return null;   // 없는 그림(예외를 던지는 버전)
		} finally {
			if (res instanceof AutoCloseable ac) {
				try {
					ac.close();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// ==================== 49-264차: 삼각형 반쪽 그림 ====================
	// 사용자: "한 면이 날 쳐다보면서 비틀어져". 화면 위 그리기(GUI)는 평행사변형(아핀)만 그릴 수 있어서, 원근으로 사다리꼴이 된 면을
	// 평행사변형 하나로 맞추면 가까울수록 비틀어지고, 여러 조각으로 나누면 이음매가 깨져 보였다. 삼각형은 아핀으로 <b>정확히</b> 그려진다.
	// 그래서 면을 대각선(b-d)으로 나눈 두 삼각형으로 그린다 - 그림의 위왼쪽 반(A)과 아래오른쪽 반(B)만 남긴(나머지는 투명) 그림 두 장을
	// 미리 만들어(그 블록 그림의 그 부분만 잘라 S×S로 키움) 각 삼각형의 꼭짓점 셋에 맞춰 한 장씩 그린다. 두 반쪽은 텍셀 단위로 딱
	// 맞물리고, 두 삼각형은 대각선 위에서 같은 자리로 가므로 이음매가 생기지 않는다. 색 칸(다른 블록 등)은 흰 반쪽 그림에 색을 곱한다.
	// 만드는 데는 자바 ImageIO(스크린샷 기능도 쓰는 것)로 PNG를 풀고 다시 묶어 NativeImage.read로 올린다 - 버전마다 다른 픽셀 API를 안 쓴다.

	/** 만든 반쪽 그림 한 쌍과 크기. */
	public static final class Masked {
		public final Identifier a, b;
		public final int size;

		Masked(Identifier a, Identifier b, int size) {
			this.a = a;
			this.b = b;
			this.size = size;
		}
	}

	private static final Map<String, Object> MASKS = new HashMap<>();
	private static final Map<Identifier, Object> SOURCES = new HashMap<>();
	private static final Object FAILED = new Object();
	private static int maskSeq;
	private static long maskFrame;
	private static int maskMadeInFrame;
	private static boolean masksBroken;
	private static final int MAX_MASKS = 900;

	/**
	 * 그림 t(null = 흰색)의 (u, v)에서 rw × rh 텍셀 부분을 대각선으로 나눈 반쪽 그림 한 쌍. 아직 없으면 만든다(한 프레임에 8쌍까지 -
	 * 넘으면 이번엔 null이고 다음 프레임에). 만들 수 없으면 null(호출부가 예전 방식으로 그린다).
	 * alpha(0~255) = 그릴 때 곱할 진하기. 49-265차: 두 반쪽이 대각선 한 줄을 같이 갖고(틈이 점선처럼 보이던 것), 그 줄은 겹쳐도
	 * 진하기가 같아지게 덜 진하게 만든다 - 그래서 진하기마다 따로 만든다.
	 */
	public static Masked masked(Tex t, int u, int v, int rw, int rh, int alpha) {
		if (masksBroken) {
			return null;
		}
		int aq = Math.max(0, Math.min(255, alpha)) >> 3;
		String key = (t == null ? "#white" : t.id.toString()) + "|" + u + "|" + v + "|" + rw + "|" + rh + "|" + aq;
		Object hit = MASKS.get(key);
		if (hit instanceof Masked m) {
			return m;
		}
		if (hit == FAILED || MASKS.size() >= MAX_MASKS) {
			return null;
		}
		long now = System.nanoTime() / 16_000_000L;   // 대략 프레임 단위
		if (now != maskFrame) {
			maskFrame = now;
			maskMadeInFrame = 0;
		}
		if (maskMadeInFrame >= 8) {
			return null;
		}
		maskMadeInFrame++;
		try {
			Masked m = makeMasked(t, u, v, rw, rh, (aq << 3 | 7) / 255.0);
			MASKS.put(key, m == null ? FAILED : m);
			return m;
		} catch (Throwable e) {
			MASKS.put(key, FAILED);
			LunaCompat.warnOnce("blueprint:mask", e);
			if (e instanceof NoClassDefFoundError || e instanceof LinkageError) {
				masksBroken = true;   // ImageIO가 없는 실행 환경 - 예전 방식으로만
			}
			return null;
		}
	}

	private static java.awt.image.BufferedImage source(Identifier id) throws Exception {
		Object hit = SOURCES.get(id);
		if (hit instanceof java.awt.image.BufferedImage bi) {
			return bi;
		}
		if (hit == FAILED) {
			return null;
		}
		byte[] raw = readResource(id, 8 << 20);
		java.awt.image.BufferedImage bi = raw == null ? null : javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(raw));
		if (bi != null && id.getPath().contains("leaves")) {
			bi = fillHoles(bi);
		}
		SOURCES.put(id, bi == null ? FAILED : bi);
		return bi;
	}

	/**
	 * 49-265차(사용자: "홀로그램 깨지고"): 잎 그림의 구멍(투명)으로 뒤의 다른 면들이 비쳐 줄무늬처럼 어지러웠다. 바닐라 '빠른 그래픽'
	 * 잎처럼 구멍을 잎 평균색을 어둡게 한 색으로 메운다.
	 */
	private static java.awt.image.BufferedImage fillHoles(java.awt.image.BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = src.getRGB(x, y);
				if ((c >>> 24) >= 128) {
					r += (c >> 16) & 0xFF;
					g += (c >> 8) & 0xFF;
					b += c & 0xFF;
					n++;
				}
			}
		}
		if (n == 0) {
			return src;
		}
		int hole = 0xFF000000 | (int) (r / n * 2 / 5) << 16 | (int) (g / n * 2 / 5) << 8 | (int) (b / n * 2 / 5);
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = src.getRGB(x, y);
				out.setRGB(x, y, (c >>> 24) >= 128 ? c | 0xFF000000 : hole);
			}
		}
		return out;
	}

	private static Masked makeMasked(Tex t, int u, int v, int rw, int rh, double alpha) throws Exception {
		java.awt.image.BufferedImage src = t == null ? null : source(t.id);
		if (t != null && src == null) {
			return null;
		}
		int size = Math.max(32, Math.min(128, Integer.highestOneBit(Math.max(rw, rh) * 2 - 1) * 2));
		Minecraft client = Minecraft.getInstance();
		Identifier[] ids = new Identifier[2];
		int seq = ++maskSeq;
		// 대각선 줄은 두 반쪽이 다 그린다 - 겹친 곳 진하기가 alpha가 되도록 각자 k만큼: 1 - (1 - a k)^2 = a
		double k = alpha >= 0.995 ? 1 : (1 - Math.sqrt(1 - alpha)) / alpha;
		int diagA = (int) Math.round(255 * Math.max(0.3, Math.min(1, k)));
		for (int half = 0; half < 2; half++) {
			java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int sum = x + y;
					boolean diag = sum == size - 1;   // 대각선이 지나는 줄
					if (!diag && (sum < size - 1) != (half == 0)) {
						continue;   // 투명
					}
					int argb;
					if (src == null) {
						argb = 0xFFFFFFFF;
					} else {
						int sx = Math.min(src.getWidth() - 1, u + x * rw / size);
						int sy = Math.min(src.getHeight() - 1, v + y * rh / size);
						argb = src.getRGB(sx, sy);
					}
					if (diag) {
						argb = (argb & 0xFFFFFF) | (((argb >>> 24) * diagA / 255) << 24);
					}
					out.setRGB(x, y, argb);
				}
			}
			java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
			javax.imageio.ImageIO.write(out, "png", bo);
			com.mojang.blaze3d.platform.NativeImage img =
					com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bo.toByteArray()));
			ids[half] = LunaCompat.registerImageTexture(client, "bp_mask/m" + seq + (half == 0 ? "a" : "b"), img);
			if (ids[half] == null) {
				return null;
			}
		}
		return new Masked(ids[0], ids[1], size);
	}

	// ==================== 49-260차: 블록 회전(blockstates) ====================
	// 사용자: "낙엽 같은 건 방향이 반대". 그림은 늘 북쪽 기준으로 입혔는데, 바닐라는 블록 상태마다 모델을 y축으로 돌린다
	// (blockstates/<id>.json의 variants/multipart "y"). 그 값을 읽어 윗면, 아랫면 그림을 같이 돌린다.

	private static final Map<String, List<Object[]>> ROT_CACHE = new HashMap<>();

	/** 이 블록 상태(props = "facing=east,half=top")의 모델 y 회전(0, 90, 180, 270). 모르면 0. */
	public static int yRot(String blockId, String props) {
		try {
			if (blockId == null || blockId.isEmpty()) {
				return 0;
			}
			List<Object[]> rules = ROT_CACHE.get(blockId);
			if (rules == null) {
				rules = loadRot(blockId);
				ROT_CACHE.put(blockId, rules);
			}
			if (rules.isEmpty()) {
				return 0;
			}
			Map<String, String> have = Blueprint.parseProps(props);
			for (Object[] r : rules) {
				@SuppressWarnings("unchecked")
				Map<String, String> cond = (Map<String, String>) r[0];
				boolean ok = true;
				for (Map.Entry<String, String> c : cond.entrySet()) {
					String v = have.get(c.getKey());
					if (v == null || !java.util.Arrays.asList(c.getValue().split("\\|")).contains(v)) {
						ok = false;
						break;
					}
				}
				if (ok) {
					return (Integer) r[1];
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:rot", t);
		}
		return 0;
	}

	private static List<Object[]> loadRot(String blockId) {
		List<Object[]> out = new ArrayList<>();
		int colon = blockId.indexOf(':');
		String ns = colon < 0 ? "minecraft" : blockId.substring(0, colon);
		String path = colon < 0 ? blockId : blockId.substring(colon + 1);
		byte[] raw = readResource(LunaCompat.identifier(ns, "blockstates/" + path + ".json"), 1 << 20);
		if (raw == null) {
			return out;
		}
		com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
		if (!root.isJsonObject()) {
			return out;
		}
		com.google.gson.JsonObject o = root.getAsJsonObject();
		if (o.has("variants") && o.get("variants").isJsonObject()) {
			for (Map.Entry<String, com.google.gson.JsonElement> v : o.getAsJsonObject("variants").entrySet()) {
				Map<String, String> cond = new HashMap<>();
				for (String kv : v.getKey().split(",")) {
					int eq = kv.indexOf('=');
					if (eq > 0) {
						cond.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
					}
				}
				out.add(new Object[]{cond, yOf(v.getValue())});
			}
		} else if (o.has("multipart") && o.get("multipart").isJsonArray()) {
			for (com.google.gson.JsonElement part : o.getAsJsonArray("multipart")) {
				if (!part.isJsonObject()) {
					continue;
				}
				com.google.gson.JsonObject po = part.getAsJsonObject();
				Map<String, String> cond = new HashMap<>();
				if (po.has("when") && po.get("when").isJsonObject()) {
					com.google.gson.JsonObject w = po.getAsJsonObject("when");
					if (w.has("OR") || w.has("AND")) {
						continue;
					}
					for (Map.Entry<String, com.google.gson.JsonElement> c : w.entrySet()) {
						cond.put(c.getKey(), c.getValue().getAsString());
					}
				}
				int y = yOf(po.get("apply"));
				if (y != 0 || !cond.isEmpty()) {
					out.add(new Object[]{cond, y});
				}
			}
		}
		return out;
	}

	private static int yOf(com.google.gson.JsonElement apply) {
		if (apply == null) {
			return 0;
		}
		if (apply.isJsonArray() && apply.getAsJsonArray().size() > 0) {
			apply = apply.getAsJsonArray().get(0);
		}
		if (apply.isJsonObject() && apply.getAsJsonObject().has("y")) {
			return ((apply.getAsJsonObject().get("y").getAsInt() % 360) + 360) % 360;
		}
		return 0;
	}

	/** 리소스 파일 내용(최대 max바이트). 없으면 null. */
	static byte[] readResource(Identifier id, int max) {
		Object res = null;
		try {
			Object r = openResource(id);
			res = r;
			if (r == null) {
				return null;
			}
			Method open = null;
			for (Method m : r.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && InputStream.class.isAssignableFrom(m.getReturnType())) {
					open = m;
					break;
				}
			}
			if (open == null) {
				return null;
			}
			open.setAccessible(true);
			try (InputStream in = (InputStream) open.invoke(r)) {
				return in.readNBytes(max);
			}
		} catch (Throwable t) {
			return null;
		} finally {
			if (res instanceof AutoCloseable ac) {
				try {
					ac.close();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	private static Object openResource(Identifier id) throws Exception {
		Minecraft client = Minecraft.getInstance();
		Object manager = client == null ? null : client.getResourceManager();
		if (manager == null) {
			return null;
		}
		if (getResource == null) {
			for (Method m : manager.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && LunaCompat.nameMatches(manager.getClass(), "getResource", m.getName())
						&& m.getParameterTypes()[0].isInstance(id)) {
					m.setAccessible(true);
					getResource = m;
					break;
				}
			}
			if (getResource == null) {
				return null;
			}
		}
		Object res = getResource.invoke(manager, id);
		if (res instanceof java.util.Optional<?> opt) {
			res = opt.orElse(null);
		}
		return res;
	}

	private static int beInt(byte[] b, int o) {
		return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
	}
}
