package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-270차: <b>노바 날개</b>(런처 상점 치장품, 첫 번째 = 나비 날개). 사용자: "애니메이션 넣어서 하려고 하는데 사람들이 꺼내 가서 악용 못 하게
 * 방지해 주고 구매한 사람들만 쓸 수 있게".
 *
 * <p><b>꺼내 가기 방지</b>: 그림(png), 모델(Blockbench Bedrock geo.json), 애니메이션(animation.json)을 tools/pack-cosmetic.py가
 * AES-GCM으로 묶어 jar의 assets/lunaslight/cos/&lt;이름 해시&gt;.bin 한 파일로 넣는다. jar를 풀어도 png, json이 안 나오고, 리소스
 * 관리자(리소스팩)를 거치지 않고 클래스 로더로 직접 읽으므로 리소스팩으로 꺼내거나 바꿔치기할 수도 없다. 푼 그림은 메모리의 동적 텍스처로만
 * 올린다. (게임이 그리려면 결국 메모리에서 풀어야 해서 메모리를 뜯는 것까지 막을 수는 없다 - 그래서 키는 마스크를 씌워 나눠 둔다.)
 *
 * <p><b>산 사람만</b>: 내 날개는 런처가 Ed25519로 서명한 값(.luna-launch.json cosmetics.wing = {key, ts, sig}, 서명 글자
 * "NovaSig|v1|wing|ts|key|내 uuid(소문자, 대시 없음)")이 맞을 때만 그린다 - 파일을 고쳐 넣으면 서명이 틀린다. 남의 날개는 망토처럼
 * Supabase nova_player_tiers.cosmetic_wing(묶어서 60초 캐시).
 *
 * <p>그리기는 버전마다 다르고(NovaWingsRender, 26.x 판, 1.14.4 GL 판), 여기서는 모양만 만든다: 뼈대(피벗, 부모) + 애니메이션(선형 보간,
 * 반복) → 카메라 기준 월드 좌표의 사각형(꼭짓점마다 x y z u v nx ny nz).
 */
public final class NovaWings {
	private NovaWings() {
	}

	public static final String[] KEYS = {"butterfly"};

	// ==================== 소유 ====================

	private static volatile String mineKey, mineSig;
	private static volatile long mineTs;
	private static volatile String mineOkFor;
	private static volatile boolean mineOk;

	/** LunaSocial이 .luna-launch.json의 cosmetics.wing을 읽어 넘긴다(끼지 않았으면 key null). */
	public static void setMineSigned(String key, long ts, String sig) {
		mineKey = valid(key);
		mineTs = ts;
		mineSig = sig;
		mineOkFor = null;
	}

	private static String valid(String key) {
		if (key == null) {
			return null;
		}
		String k = key.trim().toLowerCase(Locale.ROOT);
		if (k.startsWith("wing-")) {
			k = k.substring(5);
		}
		if (k.endsWith("_wings")) {
			k = k.substring(0, k.length() - 6);
		}
		for (String x : KEYS) {
			if (x.equals(k)) {
				return x;
			}
		}
		return null;
	}

	private static String norm(String id) {
		return id == null ? "" : id.replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** 내 날개(서명 확인된 것만). */
	private static String mine(String selfUuid) {
		String k = mineKey;
		if (k == null || selfUuid.isEmpty()) {
			return null;
		}
		String tag = k + "|" + selfUuid + "|" + mineTs;
		if (!tag.equals(mineOkFor)) {
			mineOk = kr.lunaslight.mod.LunaClientMod.verifySig2("wing", mineTs, k + "|" + selfUuid, mineSig, false);
			mineOkFor = tag;
			if (!mineOk) {
				LunaCompat.warnOnce("wings:sig", new IllegalStateException("날개 서명이 맞지 않아 그리지 않음"));
			}
		}
		return mineOk ? k : null;
	}

	/** 49-271차: 내가 낀 날개(서명 확인된 것, 없으면 null) - [코스메틱] 페이지 표시용. */
	public static String mineKey() {
		return mine(selfId());
	}

	/** key → 보이는 이름. */
	public static String nameOf(String key) {
		if (key == null) {
			return null;
		}
		return "butterfly".equals(key) ? "나비 날개" : key;
	}

	private static final Map<String, String> OTHERS = new ConcurrentHashMap<>();
	private static final Map<String, Long> FETCHED = new ConcurrentHashMap<>();
	private static final Set<String> WANT = ConcurrentHashMap.newKeySet();
	private static volatile boolean inFlight;
	private static volatile long lastFetch;
	private static final long TTL = 60_000L;
	private static String selfId = "";
	private static long selfAt;

	public static String selfId() {
		long now = System.currentTimeMillis();
		if (now - selfAt > 5000L) {
			selfAt = now;
			try {
				selfId = norm(LunaSocial.currentUuid(MinecraftClient.getInstance()));
			} catch (Throwable ignored) {
			}
		}
		return selfId;
	}

	/** 이 uuid(대시 있든 없든)가 낀 날개 key(없으면 null). 남의 것은 모르면 조회를 예약한다. */
	public static String keyFor(String uuid, boolean others) {
		String uid = norm(uuid);
		if (uid.isEmpty()) {
			return null;
		}
		LunaSocial.load();
		if (uid.equals(selfId())) {
			return mine(uid);
		}
		if (!others) {
			return null;
		}
		String k = OTHERS.get(uid);
		Long at = FETCHED.get(uid);
		if (at == null || System.currentTimeMillis() - at > TTL) {
			WANT.add(uid);
			maybeFetch();
		}
		return k == null || k.isEmpty() ? null : k;
	}

	private static void maybeFetch() {
		long now = System.currentTimeMillis();
		if (inFlight || WANT.isEmpty() || now - lastFetch < 2000L || !LunaSocial.available()) {
			return;
		}
		inFlight = true;
		lastFetch = now;
		List<String> batch = new ArrayList<>();
		for (String u : WANT) {
			batch.add(u);
			if (batch.size() >= 60) {
				break;
			}
		}
		WANT.removeAll(batch);
		try {
			LunaSocial.fetchWings(batch).whenComplete((map, err) -> {
				long t = System.currentTimeMillis();
				for (String u : batch) {
					String k = map == null ? null : valid(map.get(u));
					OTHERS.put(u, k == null ? "" : k);
					FETCHED.put(u, err == null ? t : t - TTL + 15_000L);
				}
				inFlight = false;
			});
		} catch (Throwable t) {
			inFlight = false;
		}
	}

	// ==================== 자산(암호화된 묶음) ====================

	private static final String[] K = {"zAMSYGdn+zy0Z/O", "7+ARsOsy+zpQf+b", "1BkVSuKJuiNTg="};

	/** 뼈 하나: 피벗(Blockbench 좌표 - Bedrock의 x를 뒤집은 것, 픽셀), 부모, 상자들, 회전 열쇠틀. */
	static final class Bone {
		String name;
		int parent = -1;
		float[] pivot = new float[3];
		final List<float[]> cubes = new ArrayList<>();   // x0 y0 z0 x1 y1 z1 u v sx sy sz(Bedrock 크기)
		float[] keyT;
		float[][] keyRot;   // Blockbench 좌표의 각도(도)
	}

	public static final class Asset {
		byte[] png;
		float texW = 16, texH = 16, length = 1;
		final List<Bone> bones = new ArrayList<>();
		Object texture;   // 플랫폼이 올린 텍스처 id
		boolean textureFailed;
		int quadCount;

		public Object texture() {
			return texture;
		}

		public void setTexture(Object t) {
			texture = t;
		}

		public boolean textureFailed() {
			return textureFailed;
		}

		public void markTextureFailed() {
			textureFailed = true;
		}

		public byte[] png() {
			return png;
		}

		/** 꼭짓점 수의 상한(사각형 수 × 4). */
		public int maxVertices() {
			return quadCount * 4;
		}
	}

	private static final Map<String, Object> ASSETS = new HashMap<>();
	private static final Object FAILED = new Object();

	public static synchronized Asset asset(String key) {
		if (key == null) {
			return null;
		}
		Object hit = ASSETS.get(key);
		if (hit instanceof Asset a) {
			return a;
		}
		if (hit == FAILED) {
			return null;
		}
		try {
			Asset a = load(key);
			ASSETS.put(key, a == null ? FAILED : a);
			return a;
		} catch (Throwable t) {
			ASSETS.put(key, FAILED);
			LunaCompat.warnOnce("wings:asset", t);
			return null;
		}
	}

	private static byte[] aesKey() throws Exception {
		byte[] masked = java.util.Base64.getDecoder().decode(K[0] + K[1] + K[2]);
		byte[] mask = java.security.MessageDigest.getInstance("SHA-256").digest("NovaClient|cos|mask|49-270".getBytes(StandardCharsets.UTF_8));
		byte[] k = new byte[masked.length];
		for (int i = 0; i < k.length; i++) {
			k[i] = (byte) (masked[i] ^ mask[i]);
		}
		return k;
	}

	private static String hex16(String s) throws Exception {
		byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			sb.append(String.format("%02x", d[i]));
		}
		return sb.toString();
	}

	private static Asset load(String key) throws Exception {
		String path = "/assets/lunaslight/cos/" + hex16("nova-cos|" + key) + ".bin";
		byte[] raw;
		try (InputStream in = NovaWings.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				bo.write(buf, 0, n);
			}
			raw = bo.toByteArray();
		}
		javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
		c.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(aesKey(), "AES"),
				new javax.crypto.spec.GCMParameterSpec(128, raw, 0, 12));
		c.updateAAD(("nova-cos|" + key).getBytes(StandardCharsets.UTF_8));
		byte[] body = c.doFinal(raw, 12, raw.length - 12);
		ByteBuffer bb = ByteBuffer.wrap(body);
		if (bb.getInt() != 0x4E574331) {   // "NWC1"
			return null;
		}
		byte[][] parts = new byte[3][];
		for (int i = 0; i < 3; i++) {
			parts[i] = new byte[bb.getInt()];
			bb.get(parts[i]);
		}
		Asset a = new Asset();
		a.png = parts[0];
		parseGeo(a, new String(parts[1], StandardCharsets.UTF_8));
		parseAnim(a, new String(parts[2], StandardCharsets.UTF_8));
		return a;
	}

	@SuppressWarnings("deprecation")
	private static JsonObject json(String s) {
		return new com.google.gson.JsonParser().parse(s).getAsJsonObject();
	}

	private static float[] vec(JsonElement e) {
		if (e == null) {
			return new float[3];
		}
		if (e.isJsonObject()) {
			JsonObject o = e.getAsJsonObject();
			e = o.has("post") ? o.get("post") : o.has("vector") ? o.get("vector") : o.get("pre");
			if (e != null && e.isJsonObject()) {
				return vec(e);
			}
		}
		float[] v = new float[3];
		if (e != null && e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			for (int i = 0; i < 3 && i < a.size(); i++) {
				try {
					v[i] = a.get(i).getAsFloat();
				} catch (Throwable ignored) {
					// 식(몰랑 식)은 지원 안 함 - 0
				}
			}
		} else if (e != null && e.isJsonPrimitive()) {
			float f = e.getAsFloat();
			v[0] = v[1] = v[2] = f;
		}
		return v;
	}

	private static void parseGeo(Asset a, String text) {
		JsonObject root = json(text);
		JsonArray geos = root.getAsJsonArray("minecraft:geometry");
		JsonObject g = geos.get(0).getAsJsonObject();
		JsonObject d = g.getAsJsonObject("description");
		if (d != null) {
			if (d.has("texture_width")) {
				a.texW = d.get("texture_width").getAsFloat();
			}
			if (d.has("texture_height")) {
				a.texH = d.get("texture_height").getAsFloat();
			}
		}
		Map<String, Integer> index = new HashMap<>();
		List<String> parents = new ArrayList<>();
		for (JsonElement be : g.getAsJsonArray("bones")) {
			JsonObject bo = be.getAsJsonObject();
			Bone b = new Bone();
			b.name = bo.get("name").getAsString();
			float[] p = vec(bo.get("pivot"));
			b.pivot = new float[]{-p[0], p[1], p[2]};   // Bedrock x → Blockbench x
			if (bo.has("cubes")) {
				for (JsonElement ce : bo.getAsJsonArray("cubes")) {
					JsonObject co = ce.getAsJsonObject();
					float[] o = vec(co.get("origin"));
					float[] s = vec(co.get("size"));
					float u = 0, v = 0;
					if (co.has("uv") && co.get("uv").isJsonArray()) {
						u = co.getAsJsonArray("uv").get(0).getAsFloat();
						v = co.getAsJsonArray("uv").get(1).getAsFloat();
					}
					b.cubes.add(new float[]{-(o[0] + s[0]), o[1], o[2], -o[0], o[1] + s[1], o[2] + s[2], u, v, s[0], s[1], s[2]});
					a.quadCount += 6;
				}
			}
			index.put(b.name, a.bones.size());
			parents.add(bo.has("parent") ? bo.get("parent").getAsString() : null);
			a.bones.add(b);
		}
		for (int i = 0; i < a.bones.size(); i++) {
			Integer pi = parents.get(i) == null ? null : index.get(parents.get(i));
			a.bones.get(i).parent = pi == null ? -1 : pi;
		}
	}

	private static void parseAnim(Asset a, String text) {
		JsonObject root = json(text);
		JsonObject anims = root.getAsJsonObject("animations");
		if (anims == null || anims.size() == 0) {
			return;
		}
		JsonObject an = null;
		for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
			if (an == null || e.getKey().endsWith(".idle")) {
				an = e.getValue().getAsJsonObject();
			}
		}
		if (an.has("animation_length")) {
			a.length = Math.max(0.05f, an.get("animation_length").getAsFloat());
		}
		JsonObject bones = an.getAsJsonObject("bones");
		if (bones == null) {
			return;
		}
		for (Bone b : a.bones) {
			if (!bones.has(b.name)) {
				continue;
			}
			JsonObject bo = bones.getAsJsonObject(b.name);
			JsonElement rot = bo.get("rotation");
			if (rot == null) {
				continue;
			}
			List<float[]> keys = new ArrayList<>();
			if (rot.isJsonObject() && !rot.getAsJsonObject().has("vector") && !rot.getAsJsonObject().has("post")) {
				for (Map.Entry<String, JsonElement> k : rot.getAsJsonObject().entrySet()) {
					float t;
					try {
						t = Float.parseFloat(k.getKey());
					} catch (NumberFormatException ex) {
						continue;
					}
					float[] v = vec(k.getValue());
					keys.add(new float[]{t, -v[0], -v[1], v[2]});   // Bedrock → Blockbench(x, y 뒤집힘)
				}
			} else {
				float[] v = vec(rot);
				keys.add(new float[]{0, -v[0], -v[1], v[2]});
			}
			keys.sort((x, y) -> Float.compare(x[0], y[0]));
			b.keyT = new float[keys.size()];
			b.keyRot = new float[keys.size()][];
			for (int i = 0; i < keys.size(); i++) {
				b.keyT[i] = keys.get(i)[0];
				b.keyRot[i] = new float[]{keys.get(i)[1], keys.get(i)[2], keys.get(i)[3]};
			}
		}
	}

	// ==================== 모양 ====================

	private static float[] rotAt(Bone b, float t) {
		if (b.keyT == null || b.keyT.length == 0) {
			return null;
		}
		if (t <= b.keyT[0]) {
			return b.keyRot[0];
		}
		int n = b.keyT.length;
		if (t >= b.keyT[n - 1]) {
			return b.keyRot[n - 1];
		}
		for (int i = 0; i < n - 1; i++) {
			if (t <= b.keyT[i + 1]) {
				float f = (t - b.keyT[i]) / Math.max(1e-6f, b.keyT[i + 1] - b.keyT[i]);
				float[] p = b.keyRot[i], q = b.keyRot[i + 1];
				return new float[]{p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f, p[2] + (q[2] - p[2]) * f};
			}
		}
		return b.keyRot[n - 1];
	}

	/** 3x4 아핀(행 우선 m[0..11]): x' = m0 x + m1 y + m2 z + m3 ... */
	private static double[] mul(double[] a, double[] b) {
		double[] r = new double[12];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 4; j++) {
				double s = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j];
				if (j == 3) {
					s += a[i * 4 + 3];
				}
				r[i * 4 + j] = s;
			}
		}
		return r;
	}

	private static double[] translate(double x, double y, double z) {
		return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z};
	}

	private static double[] rotX(double rad) {
		double c = Math.cos(rad), s = Math.sin(rad);
		return new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0};
	}

	private static double[] rotY(double rad) {
		double c = Math.cos(rad), s = Math.sin(rad);
		return new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0};
	}

	private static double[] rotZ(double rad) {
		double c = Math.cos(rad), s = Math.sin(rad);
		return new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0};
	}

	/**
	 * 날개 꼭짓점을 out에 채운다(꼭짓점마다 8개: x y z u v nx ny nz, 카메라 기준 월드 좌표, uv는 0~1). 돌려주는 값 = 꼭짓점 수(4의 배수).
	 * (ox, oy, oz) = 플레이어 발 위치 - 카메라, bodyYaw = 몸 방향(도), sneak = 웅크림.
	 */
	public static int build(Asset a, double timeSec, float bodyYaw, boolean sneak, double ox, double oy, double oz, float[] out) {
		int nb = a.bones.size();
		double[][] world = new double[nb][];
		float t = (float) (timeSec % a.length);
		// 몸: 웅크리면 바닐라처럼 목(서 있을 때 위 24px)을 중심으로 앞으로 0.5 라디안 숙이고, 몸통이 3.2px(1.16+ body pivotY 3.2,
		// 1.15 이하 모델 0.2블록 내림) + 플레이어 전체가 2px(렌더 위치 -0.125) 내려간다.
		// 49-272차: 예전엔 20.8px을 중심으로 돌려 날개가 몸보다 3.2px 높이 떠 있었다.
		double[] body = sneak ? mul(translate(0, 24 - 3.2 - 2, 0), mul(rotX(-0.5), translate(0, -24, 0))) : translate(0, 0, 0);
		// 모델(픽셀, 앞 = -Z) → 월드(블록): 1/16, 몸 방향으로 돌림(바닐라와 같은 180 - bodyYaw)
		double s = 1 / 16.0;
		double[] place = mul(translate(ox, oy, oz), mul(rotY(Math.toRadians(180 - bodyYaw)), new double[]{s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0}));
		double[] base = mul(place, body);
		for (int i = 0; i < nb; i++) {
			Bone b = a.bones.get(i);
			double[] local = translate(0, 0, 0);
			float[] r = rotAt(b, t);
			if (r != null) {
				double[] rot = mul(rotZ(Math.toRadians(r[2])), mul(rotY(Math.toRadians(r[1])), rotX(Math.toRadians(r[0]))));
				local = mul(translate(b.pivot[0], b.pivot[1], b.pivot[2]), mul(rot, translate(-b.pivot[0], -b.pivot[1], -b.pivot[2])));
			}
			double[] parent = b.parent >= 0 && b.parent < i && world[b.parent] != null ? world[b.parent] : base;
			world[i] = mul(parent, local);
		}
		int n = 0;
		for (int i = 0; i < nb; i++) {
			double[] m = world[i];
			for (float[] c : a.bones.get(i).cubes) {
				n = cube(m, c, a.texW, a.texH, out, n);
			}
		}
		return n;
	}

	private static int cube(double[] m, float[] c, float tw, float th, float[] out, int n) {
		float x0 = c[0], y0 = c[1], z0 = c[2], x1 = c[3], y1 = c[4], z1 = c[5];
		float u = c[6], v = c[7], w = c[8], h = c[9], d = c[10];
		// Bedrock 상자 UV: 위(u+d, v) 아래(u+d+w, v) 옆(u, v+d) 앞=북(u+d, v+d) 옆(u+d+w, v+d) 뒤=남(u+d+w+d, v+d)
		// 북(z0): 밖에서 보면 왼쪽 = +x / 남(z1): 왼쪽 = -x
		n = quad(m, out, n, tw, th, 0, 0, -1,
				x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, u + d, v + d, w, h);
		n = quad(m, out, n, tw, th, 0, 0, 1,
				x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, u + d + w + d, v + d, w, h);
		n = quad(m, out, n, tw, th, 1, 0, 0,
				x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, u, v + d, d, h);
		n = quad(m, out, n, tw, th, -1, 0, 0,
				x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, u + d + w, v + d, d, h);
		n = quad(m, out, n, tw, th, 0, 1, 0,
				x1, y1, z1, x0, y1, z1, x0, y1, z0, x1, y1, z0, u + d, v, w, d);
		n = quad(m, out, n, tw, th, 0, -1, 0,
				x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, u + d + w, v, w, d);
		return n;
	}

	/** 네 꼭짓점(왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래)과 그림 영역(픽셀). */
	private static int quad(double[] m, float[] out, int n, float tw, float th, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			float u, float v, float rw, float rh) {
		if (out.length < (n + 4) * 8) {
			return n;
		}
		float u0 = u / tw, u1 = (u + rw) / tw, v0 = v / th, v1 = (v + rh) / th;
		double rnx = m[0] * nx + m[1] * ny + m[2] * nz, rny = m[4] * nx + m[5] * ny + m[6] * nz, rnz = m[8] * nx + m[9] * ny + m[10] * nz;
		double len = Math.sqrt(rnx * rnx + rny * rny + rnz * rnz);
		if (len > 1e-9) {
			rnx /= len;
			rny /= len;
			rnz /= len;
		}
		float[][] p = {{ax, ay, az, u0, v0}, {bx, by, bz, u1, v0}, {cx, cy, cz, u1, v1}, {dx, dy, dz, u0, v1}};
		for (float[] q : p) {
			int o = n * 8;
			out[o] = (float) (m[0] * q[0] + m[1] * q[1] + m[2] * q[2] + m[3]);
			out[o + 1] = (float) (m[4] * q[0] + m[5] * q[1] + m[6] * q[2] + m[7]);
			out[o + 2] = (float) (m[8] * q[0] + m[9] * q[1] + m[10] * q[2] + m[11]);
			out[o + 3] = q[3];
			out[o + 4] = q[4];
			out[o + 5] = (float) rnx;
			out[o + 6] = (float) rny;
			out[o + 7] = (float) rnz;
			n++;
		}
		return n;
	}

	// ==================== 텍스처 ====================

	/** 푼 그림을 동적 텍스처로 올린다(리소스 경로에 안 올림 - 이름은 매번 무작위). 못 하면 null. */
	public static Identifier texture(Asset a) {
		if (a == null || a.textureFailed) {
			return null;
		}
		if (a.texture instanceof Identifier id) {
			return id;
		}
		try {
			net.minecraft.client.texture.NativeImage img = net.minecraft.client.texture.NativeImage.read(new ByteArrayInputStream(a.png));
			String name = "c/" + Long.toHexString(Double.doubleToLongBits(Math.random()) ^ System.nanoTime());
			Identifier id = LunaCompat.registerImageTexture(MinecraftClient.getInstance(), name, img);
			if (id == null) {
				a.textureFailed = true;
				return null;
			}
			a.texture = id;
			return id;
		} catch (Throwable t) {
			a.textureFailed = true;
			LunaCompat.warnOnce("wings:texture", t);
			return null;
		}
	}
}
