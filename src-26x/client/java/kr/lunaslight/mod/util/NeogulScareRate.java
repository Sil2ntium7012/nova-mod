package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-572차(사용자: "허수아비 확률 계산기 넣어서 농사한 개수 비례 현재 본인 허수아비 등장 확률 계속 측정해 줘, 나온 것만으로도 해 줘야 해"):
 * <b>허수아비 확률 재기</b>.
 *
 * <p>너굴마을에서 다 자란 작물을 캘 때마다 1번(TodoGameModeMixin.destroyBlock), "허수아비가 나타났습니다" 채팅이 오면 허수아비 1번을 센다.
 * 확률 = 나온 허수아비 ÷ 캔 작물 - 내가 실제로 본 것만으로 잰다(스킬 "이리 오너라"로 오른 확률도 그대로 들어간다).
 * 저장: config/lunaslight/neogul-scarecrow.json. 다 자란 작물만 센다(위키: 밀, 벼(beetroots), 감자, 당근, 고추(nether_wart), 호박, 수박 - 벼와 고추는 age 3, 밀/감자/당근은 7, 호박/수박은 그대로).
 */
public final class NeogulScareRate {
	private NeogulScareRate() {
	}

	public static long harvests, scares, sinceLast;
	/** 이번 접속에서. */
	public static long sessHarvests, sessScares;

	// 49-706차(사용자: "캐고 있는 작물마다 허수아비 확률 다르게 하고 내가 캐고 있는 작물만 보이게"): 작물마다 [캔 수, 허수아비, 마지막 뒤].
	// 허수아비는 나타나기 바로 전에 캔 작물(10초 안)의 것으로 센다. 예전 합계(harvests, scares)는 그대로 둔다(작물을 몰라 나눌 수 없음).
	public static final java.util.Map<String, long[]> CROP = new java.util.LinkedHashMap<>();
	/** 지금 캐고 있는 작물(마지막으로 캔 것)과 그 시각. */
	public static volatile String currentKey;
	public static volatile long currentAt;
	private static final long ATTRIBUTE_MS = 10_000L;

	/** 작물 key → 보이는 이름. */
	public static String label(String key) {
		if (key == null) {
			return "";
		}
		return switch (key) {
			case "wheat" -> "밀";
			case "beetroots" -> "벼";
			case "potatoes" -> "감자";
			case "carrots" -> "당근";
			case "nether_wart" -> "고추";
			case "pumpkin" -> "호박";
			case "melon" -> "수박";
			case "leaves" -> "나뭇잎";
			default -> key;
		};
	}

	/** 블록 상태 → 작물 key(다 자란 작물, 농사 나뭇잎만). 아니면 null. */
	static String cropKey(BlockState st) {
		if (farmLeaf(st)) {
			return "leaves";
		}
		if (!matureCrop(st)) {
			return null;
		}
		String s = String.valueOf(st);
		int a = s.indexOf('{'), b = s.indexOf('}');
		String id = a >= 0 && b > a ? s.substring(a + 1, b) : s;
		return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
	}

	/** 작물 하나의 [캔 수, 허수아비, 마지막 허수아비 뒤]. */
	public static synchronized long[] crop(String key) {
		load();
		return key == null ? null : CROP.get(key);
	}
	private static boolean loaded, registered, dirty;
	private static long lastSave;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			ChatState.addPlainListener(NeogulScareRate::onChat);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> save());
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:init", t);
		}
	}

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-scarecrow.json");
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
				harvests = o.has("harvests") ? o.get("harvests").getAsLong() : 0;
				scares = o.has("scares") ? o.get("scares").getAsLong() : 0;
				sinceLast = o.has("sinceLast") ? o.get("sinceLast").getAsLong() : 0;
				if (o.has("crops")) {
					for (var e : o.getAsJsonObject("crops").entrySet()) {
						JsonObject c = e.getValue().getAsJsonObject();
						CROP.put(e.getKey(), new long[] {c.get("h").getAsLong(), c.get("s").getAsLong(), c.has("n") ? c.get("n").getAsLong() : 0});
					}
				}
				if (o.has("current")) {
					currentKey = o.get("current").getAsString();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:load", t);
		}
	}

	public static synchronized void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("harvests", harvests);
			o.addProperty("scares", scares);
			o.addProperty("sinceLast", sinceLast);
			JsonObject crops = new JsonObject();
			for (var e : CROP.entrySet()) {
				JsonObject c = new JsonObject();
				c.addProperty("h", e.getValue()[0]);
				c.addProperty("s", e.getValue()[1]);
				c.addProperty("n", e.getValue()[2]);
				crops.add(e.getKey(), c);
			}
			o.add("crops", crops);
			if (currentKey != null) {
				o.addProperty("current", currentKey);
			}
			Path p = file();
			Files.createDirectories(p.getParent());
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:save", t);
		}
	}

	public static synchronized void reset() {
		load();
		harvests = scares = sinceLast = sessHarvests = sessScares = 0;
		CROP.clear();
		currentKey = null;
		dirty = true;
		save();
	}

	/** TodoGameModeMixin: 블록을 캤다(캐기 전 상태). */
	public static void onBreak(BlockState st) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (st == null || !NeogulData.onNeogul(mc)) {
				return;
			}
			String key = cropKey(st);
			if (key == null) {
				return;
			}
			synchronized (NeogulScareRate.class) {
				load();
				harvests++;
				sinceLast++;
				sessHarvests++;
				long[] c = CROP.computeIfAbsent(key, k -> new long[3]);
				c[0]++;
				c[2]++;
				currentKey = key;
				currentAt = System.currentTimeMillis();
				dirty = true;
				long now = System.currentTimeMillis();
				if (now - lastSave > 30_000L) {
					lastSave = now;
					save();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("scareRate:break", t);
		}
	}

	private static void onChat(String line) {
		if (line == null || !line.contains("허수아비") || !line.replace(" ", "").contains("나타났습니다")) {
			return;
		}
		if (line.contains("[전체]") || line.contains("[질문]") || line.contains("명)")) {
			return;   // 남이 채팅에 쓴 글
		}
		try {
			if (!NeogulData.onNeogul(Minecraft.getInstance())) {
				return;
			}
		} catch (Throwable ignored) {
			return;
		}
		synchronized (NeogulScareRate.class) {
			load();
			scares++;
			sessScares++;
			sinceLast = 0;
			// 바로 전에 캔 작물의 허수아비
			if (currentKey != null && System.currentTimeMillis() - currentAt <= ATTRIBUTE_MS) {
				long[] c = CROP.computeIfAbsent(currentKey, k -> new long[3]);
				c[1]++;
				c[2] = 0;
			}
			dirty = true;
			save();
		}
	}

	/**
	 * 49-676차(사용자: "나뭇잎도 이제 허수아비 확률 뜨게/포함하게"): 참나무 잎, 짙은 참나무 잎도 농사 경험치가 나오는
	 * 수확물(11/15 패치 1.3)이라 허수아비 확률에 같이 센다.
	 */
	static boolean farmLeaf(BlockState st) {
		String s = String.valueOf(st);
		int a = s.indexOf('{'), b = s.indexOf('}');
		String id = a >= 0 && b > a ? s.substring(a + 1, b) : s;
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		return path.equals("oak_leaves") || path.equals("dark_oak_leaves");
	}

	/** 다 자란 작물인지(블록 상태 글자로 - 버전 무관). */
	static boolean matureCrop(BlockState st) {
		String s = String.valueOf(st);
		int a = s.indexOf('{'), b = s.indexOf('}');
		String id = a >= 0 && b > a ? s.substring(a + 1, b) : s;
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.equals("pumpkin") || path.equals("melon")) {
			return true;
		}
		int max;
		switch (path) {
			case "beetroots", "nether_wart" -> max = 3;   // 위키(허수아비): 밀, 벼, 감자, 당근, 고추, 호박, 수박
			case "wheat", "carrots", "potatoes" -> max = 7;
			default -> {
				return false;
			}
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("age=(\\d+)").matcher(s);
		return m.find() && Integer.parseInt(m.group(1)) >= max;
	}

	/** 지금까지 잰 확률(0~1). 허수아비가 아직 없으면 -1. */
	public static double rate() {
		load();
		return harvests > 0 && scares > 0 ? scares / (double) harvests : -1;
	}
}
