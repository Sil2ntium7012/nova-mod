package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-48차: 작물 계산기의 <b>기록</b> 보관소.
 *
 * 캐기 시작하면 "한 판(Session)"이 열리고, 아이템이 늘어날 때마다 종류별로 개수를 쌓는다.
 * 일정 시간(설정) 동안 아무것도 안 캐면 그 판을 닫아 기록으로 남긴다. 너무 짧은 판(설정)은 버린다.
 * 파일은 {@code config/lunaslight/harvest.json} 한 장 - 월드가 달라도 한 곳에 모인다
 * (서버를 오가며 캐도 "오늘 얼마 벌었나"가 이어지는 게 이 기능의 목적이라서).
 */
public final class HarvestLog {
	private HarvestLog() {
	}

	/** 기록 한 판. items = 아이템id → 개수. */
	public static final class Session {
		public long startedAt;
		public long endedAt;
		/** 실제로 캔 시간(마지막으로 캔 시각 − 시작 시각). 멍하니 서 있던 시간은 빼고 센다. */
		public long activeMs;
		public double earned;
		public final Map<String, Integer> items = new LinkedHashMap<>();
		/**
		 * 49-108차(사용자: "캔 거는 따로 계산 - 하나 캤는데 4개 들어온 경우"): <b>수확 횟수</b>(=캔 횟수).
		 * items의 개수는 "얻은 아이템 수"(1번 캐서 4개면 4)이고, 이건 "몇 번 캤나"(1)이다. 둘을 나눠 센다.
		 */
		public int harvests;

		public int totalCount() {
			int n = 0;
			for (int v : items.values()) {
				n += v;
			}
			return n;
		}
	}

	private static final List<Session> HISTORY = new ArrayList<>();
	private static boolean loaded;
	private static boolean dirty;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("harvest.json");
	}

	public static List<Session> history() {
		ensureLoaded();
		return HISTORY;
	}

	public static void add(Session s) {
		ensureLoaded();
		HISTORY.add(0, s);      // 최신이 위
		while (HISTORY.size() > 200) {
			HISTORY.remove(HISTORY.size() - 1);
		}
		dirty = true;
		save();
	}

	public static void remove(int index) {
		ensureLoaded();
		if (index >= 0 && index < HISTORY.size()) {
			HISTORY.remove(index);
			dirty = true;
			save();
		}
	}

	public static void clear() {
		ensureLoaded();
		HISTORY.clear();
		dirty = true;
		save();
	}

	/** 아이템별 총합(개수, 시간). 기록 전체를 합친 값 - 화면의 "총합" 탭에서 쓴다. */
	public static Map<String, int[]> totals() {
		ensureLoaded();
		Map<String, int[]> out = new LinkedHashMap<>();
		for (Session s : HISTORY) {
			int total = Math.max(1, s.totalCount());
			for (Map.Entry<String, Integer> e : s.items.entrySet()) {
				int[] cur = out.computeIfAbsent(e.getKey(), k -> new int[]{0, 0});
				cur[0] += e.getValue();
				// 시간은 그 판에서 그 아이템이 차지한 비율만큼 나눠 준다(같이 캐는 경우가 많아서).
				cur[1] += Math.round(s.activeMs * (e.getValue() / (float) total) / 1000f);
			}
		}
		return out;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(r).getAsJsonObject();
			if (!root.has("sessions") || !root.get("sessions").isJsonArray()) {
				return;
			}
			JsonArray arr = root.get("sessions").getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				Session s = new Session();
				s.startedAt = o.get("startedAt").getAsLong();
				s.endedAt = o.get("endedAt").getAsLong();
				s.activeMs = o.get("activeMs").getAsLong();
				s.earned = o.has("earned") ? o.get("earned").getAsDouble() : 0;
				s.harvests = o.has("harvests") ? o.get("harvests").getAsInt() : 0;
				JsonObject items = o.has("items") && o.get("items").isJsonObject() ? o.get("items").getAsJsonObject() : null;
				if (items != null) {
					for (Map.Entry<String, com.google.gson.JsonElement> e : items.entrySet()) {
						s.items.put(e.getKey(), e.getValue().getAsInt());
					}
				}
				HISTORY.add(s);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("harvest:load", t);
		}
	}

	public static void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			JsonObject root = new JsonObject();
			JsonArray arr = new JsonArray();
			for (Session s : HISTORY) {
				JsonObject o = new JsonObject();
				o.addProperty("startedAt", s.startedAt);
				o.addProperty("endedAt", s.endedAt);
				o.addProperty("activeMs", s.activeMs);
				o.addProperty("earned", s.earned);
				o.addProperty("harvests", s.harvests);
				JsonObject items = new JsonObject();
				for (Map.Entry<String, Integer> e : s.items.entrySet()) {
					items.addProperty(e.getKey(), e.getValue());
				}
				o.add("items", items);
				arr.add(o);
			}
			root.add("sessions", arr);
			try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				w.write(root.toString());
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("harvest:save", t);
		}
	}

	// ==================== 가격표 ====================
	// 아이템id → 개당 가격. 설정 문자열 하나로 저장한다("minecraft:iron_ingot=50, minecraft:wheat=5").
	// 사람이 직접 치기보다 화면에서 아이템을 고르고 숫자만 넣는 게 편하므로 LunaHarvestScreen이 이걸 만든다.

	public static Map<String, Double> parsePrices(String raw) {
		Map<String, Double> out = new LinkedHashMap<>();
		if (raw == null || raw.isBlank()) {
			return out;
		}
		for (String part : raw.split(",")) {
			int eq = part.lastIndexOf('=');
			if (eq <= 0) {
				continue;
			}
			String id = part.substring(0, eq).trim();
			String value = part.substring(eq + 1).trim();
			if (id.isEmpty()) {
				continue;
			}
			try {
				out.put(id, Double.parseDouble(value));
			} catch (NumberFormatException ignored) {
				// 숫자가 아니면 그 줄만 버린다
			}
		}
		return out;
	}

	// ==================== 49-76차(6-12-3): 가격표 여러 벌 ====================
	// 사용자: "작물/광물 별로 가격표 따로 등록 가능하게". 표마다 이름이 있고("작물", "광물", …) 그중 하나가
	// 활성이다. 저장 형식은 한 줄: `이름|id=가격,id=가격;이름2|…` - 표 이름에 `|`·`;`는 못 쓴다(넣을 때 걸러진다).
	public static final String[] DEFAULT_TABLES = {"작물", "광물"};

	public static java.util.LinkedHashMap<String, Map<String, Double>> parseTables(String raw) {
		java.util.LinkedHashMap<String, Map<String, Double>> out = new java.util.LinkedHashMap<>();
		if (raw != null && !raw.isBlank()) {
			for (String block : raw.split(";")) {
				int bar = block.indexOf('|');
				if (bar <= 0) {
					continue;
				}
				String name = block.substring(0, bar).trim();
				if (name.isEmpty()) {
					continue;
				}
				out.put(name, parsePrices(block.substring(bar + 1)));
			}
		}
		if (out.isEmpty()) {
			for (String n : DEFAULT_TABLES) {
				out.put(n, new LinkedHashMap<>());
			}
		}
		return out;
	}

	public static String writeTables(Map<String, Map<String, Double>> tables) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Map<String, Double>> e : tables.entrySet()) {
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(e.getKey().replace("|", "").replace(";", "")).append('|').append(writePrices(e.getValue()));
		}
		return sb.toString();
	}

	/** 표 이름으로 쓸 수 있게 다듬는다(구분자 제거, 앞뒤 공백 제거, 12자). */
	public static String cleanTableName(String s) {
		if (s == null) {
			return "";
		}
		String t = s.replace("|", "").replace(";", "").replace(",", "").trim();
		return t.length() > 12 ? t.substring(0, 12) : t;
	}

	public static String writePrices(Map<String, Double> prices) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Double> e : prices.entrySet()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(e.getKey()).append('=').append(trim(e.getValue()));
		}
		return sb.toString();
	}

	/** 12.0 → "12", 12.5 → "12.5" (가격표 문자열이 지저분해지지 않게). */
	public static String trim(double v) {
		if (Math.abs(v - Math.rint(v)) < 0.0001) {
			return String.valueOf((long) Math.rint(v));
		}
		return String.valueOf(Math.round(v * 100) / 100.0);
	}

	/** 1234567 → "1,234,567" */
	public static String money(double v) {
		long whole = (long) Math.floor(Math.abs(v));
		String s = String.format("%,d", whole);
		if (Math.abs(v - Math.rint(v)) >= 0.01) {
			s += "." + String.format("%02d", Math.round((Math.abs(v) - whole) * 100));
		}
		return (v < 0 ? "-" : "") + s;
	}

	/** 초 → "1시간 23분" / "12분 5초" / "42초" */
	public static String duration(long ms) {
		long sec = Math.max(0, ms / 1000);
		if (sec >= 3600) {
			return (sec / 3600) + "시간 " + ((sec % 3600) / 60) + "분";
		}
		if (sec >= 60) {
			return (sec / 60) + "분 " + (sec % 60) + "초";
		}
		return sec + "초";
	}
}
