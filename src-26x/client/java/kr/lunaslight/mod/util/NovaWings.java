package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

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

	public static final String[] KEYS = {"butterfly", "angel"};   // 10-26: 천사 날개(런처 세션 - angel_wing.bbmodel, 3초마다 30도 펄럭)

	// ==================== 소유(칸마다) ====================
	// 49-351차(사용자: "토끼가방으로 치장에 추가해줘"): 날개와 같은 구조의 두 번째 칸 [가방]. 칸마다 서명 종류(kind), key 목록, Supabase 칸,
	// 내 것(서명)과 남의 것(캐시)을 따로 갖는다. 그리기는 둘 다 같은 자산 형식(NovaWingsRender).

	public static final class Slot {
		public final String kind;        // 런처 서명 종류이자 .luna-launch.json cosmetics.<kind>, 값 접두("wing-")
		public final String column;      // nova_player_tiers 칸
		public final String[] keys;
		private final Map<String, String> names;
		private final java.util.function.BooleanSupplier mineOn;

		/** 내 것(런처 서명) 하나. 49-355차: 모자는 2개까지라 목록으로. */
		private static final class Signed {
			final String key, sig;
			final long ts;
			String okFor;
			boolean ok;

			Signed(String key, long ts, String sig) {
				this.key = key;
				this.ts = ts;
				this.sig = sig;
			}
		}

		private volatile List<Signed> mine = List.of();
		private final Map<String, String> others = new ConcurrentHashMap<>();
		private final Map<String, Long> fetched = new ConcurrentHashMap<>();
		private final Set<String> want = ConcurrentHashMap.newKeySet();
		private volatile boolean inFlight;
		private volatile long lastFetch;

		/** 49-353차: 머리에 붙나(모자 - 머리 방향/숙임을 따른다). 아니면 몸(날개, 가방). */
		public final boolean head;
		private final String assetPrefix;

		Slot(String kind, String column, String[] keys, Map<String, String> names, java.util.function.BooleanSupplier mineOn) {
			this(kind, column, keys, names, mineOn, false, "");
		}

		Slot(String kind, String column, String[] keys, Map<String, String> names, java.util.function.BooleanSupplier mineOn,
				boolean head, String assetPrefix) {
			this.head = head;
			this.assetPrefix = assetPrefix;
			this.kind = kind;
			this.column = column;
			this.keys = keys;
			this.names = names;
			this.mineOn = mineOn;
		}

		/** LunaSocial이 .luna-launch.json의 cosmetics.<kind>를 읽어 넘긴다(끼지 않았으면 key null). */
		public void setMineSigned(String key, long ts, String sig) {
			String k = valid(key);
			mine = k == null ? List.of() : List.of(new Signed(k, ts, sig));
		}

		/** 49-355차: 여러 개(cosmetics.hats = [{key, ts, sig}, …]). 세 배열은 같은 길이. */
		public void setMineSignedAll(String[] keysIn, long[] ts, String[] sigs) {
			List<Signed> list = new ArrayList<>();
			for (int i = 0; i < keysIn.length; i++) {
				String k = valid(keysIn[i]);
				if (k != null) {
					list.add(new Signed(k, ts[i], sigs[i]));
				}
			}
			mine = List.copyOf(list);
		}

		public String valid(String key) {
			if (key == null) {
				return null;
			}
			String k = key.trim().toLowerCase(Locale.ROOT);
			if (k.startsWith(kind + "-")) {
				k = k.substring(kind.length() + 1);
			}
			if (k.endsWith("_" + kind + "s")) {
				k = k.substring(0, k.length() - kind.length() - 2);
			}
			for (String x : keys) {
				if (x.equals(k)) {
					return x;
				}
			}
			return null;
		}

		/** 내 것(서명 확인된 것만, 낀 순서대로). */
		private List<String> mine(String selfUuid) {
			List<Signed> list = mine;
			if (list.isEmpty() || selfUuid.isEmpty()) {
				return List.of();
			}
			List<String> out = new ArrayList<>(list.size());
			for (Signed e : list) {
				String tag = e.key + "|" + selfUuid + "|" + e.ts;
				if (!tag.equals(e.okFor)) {
					e.ok = kr.lunaslight.mod.LunaClientMod.verifySig2(kind, e.ts, e.key + "|" + selfUuid, e.sig, false);
					e.okFor = tag;
					if (!e.ok) {
						LunaCompat.warnOnce(kind + "s:sig", new IllegalStateException(kind + " 서명이 맞지 않아 그리지 않음"));
					}
				}
				if (e.ok && !out.contains(e.key)) {
					out.add(e.key);
				}
			}
			return out;
		}

		/** 쉼표로 이은 값("bunny,xxx") → 아는 key들. */
		private List<String> parseList(String raw) {
			if (raw == null || raw.isEmpty()) {
				return List.of();
			}
			List<String> out = new ArrayList<>(2);
			for (String part : raw.split(",")) {
				String k = valid(part);
				if (k != null && !out.contains(k)) {
					out.add(k);
				}
			}
			return out;
		}

		/** 49-353차: 묶음 파일 이름 = 칸 접두 + key(가방 bunny와 모자 bunny가 안 겹치게 모자는 "hat-bunny"). */
		public Asset asset(String key) {
			return key == null ? null : NovaWings.asset(assetPrefix + key);
		}

		/** 내가 낀 것(서명 확인된 것 중 첫째, 없으면 null). */
		public String mineKey() {
			List<String> m = mine(selfId());
			return m.isEmpty() ? null : m.get(0);
		}

		/** 49-355차: 내가 낀 것들의 이름(", "로 이음, 없으면 null) - [코스메틱] 페이지 표시용. */
		public String mineNames() {
			List<String> m = mine(selfId());
			if (m.isEmpty()) {
				return null;
			}
			StringBuilder sb = new StringBuilder();
			for (String k : m) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(nameOf(k));
			}
			return sb.toString();
		}

		/** key → 보이는 이름. */
		public String nameOf(String key) {
			if (key == null) {
				return null;
			}
			String n = names.get(key);
			return n == null ? key : n;
		}

		/** 이 uuid(대시 있든 없든)가 낀 것의 key(첫째, 없으면 null). */
		public String keyFor(String uuid, boolean withOthers) {
			List<String> ks = keysFor(uuid, withOthers);
			return ks.isEmpty() ? null : ks.get(0);
		}

		/**
		 * 49-355차: 이 uuid가 낀 것들(모자는 2개까지 - 런처가 내 것은 cosmetics.hats 목록, 남의 것은 cosmetic_hat "bunny,xxx"로 준다).
		 * 남의 것은 모르면 조회를 예약한다.
		 */
		public List<String> keysFor(String uuid, boolean withOthers) {
			String uid = norm(uuid);
			if (uid.isEmpty()) {
				return List.of();
			}
			LunaSocial.load();
			if (uid.equals(selfId())) {
				return mineOn.getAsBoolean() ? mine(uid) : List.of();   // 49-282차: [코스메틱] 쓰기 스위치
			}
			if (!withOthers) {
				return List.of();
			}
			String k = others.get(uid);
			Long at = fetched.get(uid);
			if (at == null || System.currentTimeMillis() - at > TTL) {
				want.add(uid);
				maybeFetch();
			}
			return parseList(k);
		}

		private void maybeFetch() {
			long now = System.currentTimeMillis();
			if (inFlight || want.isEmpty() || now - lastFetch < 5000L || !LunaSocial.available()) {
				return;
			}
			inFlight = true;
			lastFetch = now;
			List<String> batch = new ArrayList<>();
			for (String u : want) {
				batch.add(u);
				if (batch.size() >= 60) {
					break;
				}
			}
			want.removeAll(batch);
			try {
				LunaSocial.fetchCosmeticColumn(batch, column).whenComplete((map, err) -> {
					long t = System.currentTimeMillis();
					for (String u : batch) {
						List<String> ks = map == null ? List.of() : parseList(map.get(u));
						others.put(u, String.join(",", ks));
						fetched.put(u, err == null ? t : t - TTL + 60_000L);
					}
					inFlight = false;
				});
			} catch (Throwable t) {
				inFlight = false;
			}
		}
	}

	/** 날개 칸. */
	public static final Slot WING = new Slot("wing", "cosmetic_wing", KEYS, Map.of("butterfly", "나비 날개", "angel", "천사 날개"),
			() -> kr.lunaslight.mod.module.impl.misc.CosmeticsModule.wingsOn());
	/** 49-351차: 가방 칸(첫 번째 = 토끼 가방). 서명 글자 "NovaSig|v1|bag|ts|key|내 uuid", Supabase nova_player_tiers.cosmetic_bag. */
	public static final String[] BAG_KEYS = {"bunny"};
	public static final Slot BAG = new Slot("bag", "cosmetic_bag", BAG_KEYS, Map.of("bunny", "토끼 가방"),
			() -> kr.lunaslight.mod.module.impl.misc.CosmeticsModule.bagOn());
	/** 49-353차(사용자: "토끼모자 추가좀 해줘"): 모자 칸(첫 번째 = 토끼 모자). 서명 "NovaSig|v1|hat|ts|key|내 uuid", Supabase nova_player_tiers.cosmetic_hat. */
	// 49-363차: 원숭이 모자(원숭이 귀 + 머리 위 바나나, 사용자 monkey.bbmodel) - 묶음 "hat-monkey" → cos/d973a4f55e1c8134.bin
	public static final String[] HAT_KEYS = {"bunny", "monkey"};
	public static final Slot HAT = new Slot("hat", "cosmetic_hat", HAT_KEYS, Map.of("bunny", "토끼 모자", "monkey", "원숭이 모자"),
			() -> kr.lunaslight.mod.module.impl.misc.CosmeticsModule.hatOn(), true, "hat-");
	/** 49-364차: 발자취 칸(별가루 / 벚꽃, 모양 묶음 없음 - NovaTrails가 입자로 그린다). 서명 "NovaSig|v1|trail|ts|key|내 uuid", Supabase nova_player_tiers.cosmetic_trail. */
	public static final String[] TRAIL_KEYS = {"stardust", "petal"};
	public static final Slot TRAIL = new Slot("trail", "cosmetic_trail", TRAIL_KEYS, Map.of("stardust", "별가루 발자취", "petal", "벚꽃 발자취"),
			() -> kr.lunaslight.mod.module.impl.misc.CosmeticsModule.trailOn());

	/** LunaSocial이 .luna-launch.json의 cosmetics.wing을 읽어 넘긴다(끼지 않았으면 key null). */
	public static void setMineSigned(String key, long ts, String sig) {
		WING.setMineSigned(key, ts, sig);
	}

	private static String norm(String id) {
		return id == null ? "" : id.replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** 49-271차: 내가 낀 날개(서명 확인된 것, 없으면 null) - [코스메틱] 페이지 표시용. */
	public static String mineKey() {
		return WING.mineKey();
	}

	/** key → 보이는 이름(날개). */
	public static String nameOf(String key) {
		return WING.nameOf(key);
	}

	private static final long TTL = 10 * 60_000L;   // 10-08(Supabase 요청 수): 1분 → 10분, 없는 사람도 같이 기억
	private static String selfId = "";
	private static long selfAt;

	public static String selfId() {
		long now = System.currentTimeMillis();
		if (now - selfAt > 5000L) {
			selfAt = now;
			try {
				selfId = norm(LunaSocial.currentUuid(Minecraft.getInstance()));
			} catch (Throwable ignored) {
			}
		}
		return selfId;
	}

	/** 이 uuid(대시 있든 없든)가 낀 날개 key(없으면 null). 남의 것은 모르면 조회를 예약한다. */
	public static String keyFor(String uuid, boolean others) {
		return WING.keyFor(uuid, others);
	}

	// ==================== 자산(암호화된 묶음) ====================

	private static final String[] K = {"zAMSYGdn+zy0Z/O", "7+ARsOsy+zpQf+b", "1BkVSuKJuiNTg="};

	/** 뼈 하나: 피벗(Blockbench 좌표 - Bedrock의 x를 뒤집은 것, 픽셀), 부모, 상자들, 회전 열쇠틀. */
	static final class Bone {
		String name;
		int parent = -1;
		float[] pivot = new float[3];
		final List<float[]> cubes = new ArrayList<>();   // x0 y0 z0 x1 y1 z1 u v sx sy sz(Bedrock 크기) [+ 49-351차: 면마다 uv 24개 = 북 남 동 서 위 아래 × (x1 y1 x2 y2)]
		float[] rot;   // 49-351차: Blockbench 요소 자체 회전(도, 없으면 null) - 열쇠틀 없이 고정
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
		return com.google.gson.JsonParser.parseString(s).getAsJsonObject();
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
		if (root.has("elements") && !root.has("minecraft:geometry")) {
			parseBlockbench(a, root);   // 49-351차
			return;
		}
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

	/**
	 * 49-351차(토끼 가방): Blockbench 저장 파일(.bbmodel, elements + outliner)을 그대로 읽는다 - Bedrock 내보내기는 상자 UV만 되는데
	 * 가방은 면마다 UV를 따로 찍어 뒀다. 좌표는 Blockbench 그대로(앞 = -Z, 픽셀), 요소마다 from/to/faces(uv [x1 y1 x2 y2], Blockbench 면 이름
	 * north/south/east/west/up/down = -Z/+Z/+X/-X/+Y/-Y)와 자체 회전(origin + rotation). 그룹(outliner)은 뼈: origin이 피벗, rotation은 고정 회전.
	 * 애니메이션은 안 붙인다(bbmodel 애니메이션은 안 읽음 - 가방은 가만히 있는다).
	 */
	private static void parseBlockbench(Asset a, JsonObject root) {
		JsonObject res = root.getAsJsonObject("resolution");
		if (res != null) {
			a.texW = res.get("width").getAsFloat();
			a.texH = res.get("height").getAsFloat();
		}
		Map<String, JsonObject> elements = new HashMap<>();
		for (JsonElement e : root.getAsJsonArray("elements")) {
			JsonObject el = e.getAsJsonObject();
			if (el.has("uuid") && (!el.has("type") || "cube".equals(el.get("type").getAsString()))) {
				elements.put(el.get("uuid").getAsString(), el);
			}
		}
		Set<String> placed = new java.util.HashSet<>();
		if (root.has("outliner")) {
			for (JsonElement e : root.getAsJsonArray("outliner")) {
				bbNode(a, e, -1, elements, placed);
			}
		}
		for (Map.Entry<String, JsonObject> e : elements.entrySet()) {
			if (!placed.contains(e.getKey())) {
				bbCube(a, e.getValue(), -1);
			}
		}
	}

	private static void bbNode(Asset a, JsonElement node, int parent, Map<String, JsonObject> elements, Set<String> placed) {
		if (node.isJsonPrimitive()) {
			JsonObject el = elements.get(node.getAsString());
			if (el != null) {
				placed.add(node.getAsString());
				bbCube(a, el, parent);
			}
			return;
		}
		if (!node.isJsonObject()) {
			return;
		}
		JsonObject g = node.getAsJsonObject();
		Bone b = new Bone();
		b.name = g.has("name") ? g.get("name").getAsString() : "group";
		b.pivot = vec(g.get("origin"));
		float[] r = vec(g.get("rotation"));
		if (r[0] != 0 || r[1] != 0 || r[2] != 0) {
			b.rot = r;
		}
		b.parent = parent;
		int idx = a.bones.size();
		a.bones.add(b);
		if (g.has("children")) {
			for (JsonElement c : g.getAsJsonArray("children")) {
				bbNode(a, c, idx, elements, placed);
			}
		}
	}

	private static final String[] BB_FACES = {"north", "south", "east", "west", "up", "down"};

	/** 요소 하나 = 뼈 하나(자체 회전이 있을 수 있어서) + 상자 하나. */
	private static void bbCube(Asset a, JsonObject el, int parent) {
		float[] from = vec(el.get("from"));
		float[] to = vec(el.get("to"));
		Bone b = new Bone();
		b.name = el.has("name") ? el.get("name").getAsString() : "cube";
		b.parent = parent;
		b.pivot = el.has("origin") ? vec(el.get("origin")) : from.clone();
		float[] r = vec(el.get("rotation"));
		if (r[0] != 0 || r[1] != 0 || r[2] != 0) {
			b.rot = r;
		}
		float[] c = new float[11 + 24];
		c[0] = Math.min(from[0], to[0]);
		c[1] = Math.min(from[1], to[1]);
		c[2] = Math.min(from[2], to[2]);
		c[3] = Math.max(from[0], to[0]);
		c[4] = Math.max(from[1], to[1]);
		c[5] = Math.max(from[2], to[2]);
		JsonObject faces = el.getAsJsonObject("faces");
		for (int f = 0; f < 6; f++) {
			float[] uv = {0, 0, 0, 0};
			boolean has = false;
			if (faces != null && faces.has(BB_FACES[f]) && faces.get(BB_FACES[f]).isJsonObject()) {
				JsonObject fo = faces.getAsJsonObject(BB_FACES[f]);
				if (fo.has("uv") && fo.get("uv").isJsonArray()) {
					JsonArray ua = fo.getAsJsonArray("uv");
					for (int i = 0; i < 4 && i < ua.size(); i++) {
						uv[i] = ua.get(i).getAsFloat();
					}
					has = !(fo.has("texture") && fo.get("texture").isJsonNull());
				}
			}
			if (!has) {
				uv[0] = Float.NaN;   // 그림 없는 면 = 안 그림(49-353차: 폭 0은 이제 한 줄로 그린다)
			}
			System.arraycopy(uv, 0, c, 11 + f * 4, 4);
		}
		b.cubes.add(c);
		a.quadCount += 6;
		a.bones.add(b);
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
		return build(a, timeSec, bodyYaw, sneak, ox, oy, oz, 0f, out);
	}

	/** 49-352차: liftPx = 모델 전체를 위로 올리는 픽셀(가방 [높이 보정] 설정, 음수면 내림) - 몸 기울기(웅크림) 안에서 옮긴다. */
	public static int build(Asset a, double timeSec, float bodyYaw, boolean sneak, double ox, double oy, double oz, float liftPx, float[] out) {
		return build(a, timeSec, bodyYaw, sneak, ox, oy, oz, liftPx, false, 0f, out);
	}

	/** 바닐라 플레이어 렌더러가 모델 전체에 거는 배율(PlayerRenderer/AvatarRenderer.scale, 1.14.4~26.3 같음). */
	private static final double PLAYER_SCALE = 0.9375;

	/**
	 * 49-353차: onHead = 머리에 붙인다(모자) - yaw는 머리 방향, pitch = 머리 숙임(도, 아래가 +). 몸에 붙는 것은 yaw = 몸 방향, pitch 무시.
	 */
	public static int build(Asset a, double timeSec, float yaw, boolean sneak, double ox, double oy, double oz, float liftPx,
			boolean onHead, float pitch, float[] out) {
		int nb = a.bones.size();
		double[][] world = new double[nb][];
		float t = (float) (timeSec % a.length);
		double[] part;
		if (onHead) {
			// 머리: 목(24px)을 중심으로 숙인다. 웅크리면 바닐라처럼 머리가 4.2px 내려간다(head.y = 4.2, 몸처럼 기울지는 않음).
			double[] tilt = mul(rotX(Math.toRadians(-pitch)), translate(0, -24, 0));
			part = mul(translate(0, sneak ? 24 - 4.2 : 24, 0), tilt);
		} else {
			// 몸: 웅크리면 바닐라처럼 목(서 있을 때 위 24px)을 중심으로 앞으로 0.5 라디안 숙이고 몸통이 3.2px 내려간다(body.y = 3.2).
			// 49-272차: 예전엔 20.8px을 중심으로 돌려 날개가 몸보다 3.2px 높이 떠 있었다.
			part = sneak ? mul(translate(0, 24 - 3.2, 0), mul(rotX(-0.5), translate(0, -24, 0))) : translate(0, 0, 0);
		}
		// 모델(픽셀, 앞 = -Z) → 월드(블록): 1/16, 방향으로 돌림(바닐라와 같은 180 - yaw).
		// 49-353차(사용자: "가방 높이가 안 맞아"): 바닐라는 플레이어 모델을 발을 중심으로 0.9375배로 줄여 그린다(몸 위 = 22.5px, 머리 위 = 30px) -
		// 예전엔 이 배율 없이 그려 가방, 날개가 몸보다 크고 1.5px 높았다(모자는 3px). 웅크림의 렌더 위치 -2px(-0.125블록)는 배율 밖이다.
		double s = 1 / 16.0;
		double[] place = mul(translate(ox, oy, oz), mul(rotY(Math.toRadians(180 - yaw)), new double[]{s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0}));
		double k = PLAYER_SCALE;
		double[] scaleM = {k, 0, 0, 0, 0, k, 0, 0, 0, 0, k, 0};
		double[] base = mul(mul(place, mul(translate(0, sneak ? -2 : 0, 0), scaleM)), part);
		if (liftPx != 0f) {
			base = mul(base, translate(0, liftPx, 0));
		}
		for (int i = 0; i < nb; i++) {
			Bone b = a.bones.get(i);
			double[] local = translate(0, 0, 0);
			float[] r = rotAt(b, t);
			if (r == null) {
				r = b.rot;   // 49-351차: 고정 회전(Blockbench 요소/그룹)
			}
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
		if (c.length >= 35) {
			// 49-351차: 면마다 UV(Blockbench). 바깥에서 봤을 때 왼쪽 위 꼭짓점이 uv (x1, y1) - 자바 블록 모델과 같은 방향
			// (위: 북쪽이 위, 서쪽이 왼쪽 / 아래: 남쪽이 위, 서쪽이 왼쪽).
			n = quadUv(m, out, n, tw, th, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, c, 11);
			n = quadUv(m, out, n, tw, th, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, c, 15);
			n = quadUv(m, out, n, tw, th, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, c, 19);
			n = quadUv(m, out, n, tw, th, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, c, 23);
			n = quadUv(m, out, n, tw, th, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c, 27);
			n = quadUv(m, out, n, tw, th, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, c, 31);
			return n;
		}
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

	/**
	 * 49-351차: 면 UV [x1 y1 x2 y2](픽셀, 뒤집힌 값도 그대로 - 거울 면). 그림 없는 면(NaN)은 안 그린다.
	 * 49-353차: 폭이나 높이가 0인 UV(얇은 판의 옆면, 상자 UV가 내림한 0.3px 두께)는 Blockbench처럼 그 줄 한 칸을 늘여 그린다.
	 */
	private static int quadUv(double[] m, float[] out, int n, float tw, float th, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
			float[] c, int o) {
		float u0 = c[o], v0 = c[o + 1], u1 = c[o + 2], v1 = c[o + 3];
		if (Float.isNaN(u0)) {
			return n;
		}
		if (u0 == u1) {
			u1 = u0 + 0.001f;
		}
		if (v0 == v1) {
			v1 = v0 + 0.001f;
		}
		return quad(m, out, n, tw, th, nx, ny, nz, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, u0, v0, u1 - u0, v1 - v0);
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
			com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(a.png));
			String name = "c/" + Long.toHexString(Double.doubleToLongBits(Math.random()) ^ System.nanoTime());
			Identifier id = LunaCompat.registerImageTexture(Minecraft.getInstance(), name, img);
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
