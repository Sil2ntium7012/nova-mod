package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-133차(사용자: "너굴 아이템 - 너굴마을에 존재하는 모든 아이템 및 도구 등의 출처, 아이템의 경로를 모두 확인할 수 있음,
 * 모든 정보는 직접 기입 + 위키를 토대로"): 너굴마을 아이템 자료.
 *
 * <p>자료는 모드 안의 {@code assets/lunaslight/neogul/items.json}(위키 기준)을 읽고, 설정 폴더의
 * {@code lunaslight/neogul_items.json}이 있으면 그 위에 덮어쓴다(같은 key는 교체, 제작법은 추가, "replace": true면 통째로 교체).
 *
 * <p>서버 아이템은 리소스팩 모양이라 바닐라 아이콘은 대체품일 뿐이다. 그래서 인벤토리에 <b>이름이 같은 아이템</b>이 들어오면
 * 그 아이템을 그대로 아이콘으로 기억해 쓴다({@link #learn}) - 한 번 들어 본 것은 실제 서버 모양으로 보인다.
 */
public final class NeogulData {

	public record Station(String id, String name, String note, List<String> icons) {
	}

	public record Category(String id, String name, List<String> icons) {
	}

	/** 기본 출처 하나: 어디서(station id) + 덧붙임(몬스터, 확률 등). */
	public record Source(String at, String note) {
	}

	public record Entry(String key, String name, String cat, List<String> icons, List<Source> from) {
	}

	public record Ing(String key, int count) {
	}

	public record Recipe(String at, String out, int count, List<Ing> in, String note) {
	}

	private static final String BUNDLED = "/assets/lunaslight/neogul/items.json";

	private static boolean loaded;
	private static final Map<String, Station> STATIONS = new LinkedHashMap<>();
	private static final List<Category> CATEGORIES = new ArrayList<>();
	private static final Map<String, Entry> ITEMS = new LinkedHashMap<>();
	private static final List<Recipe> RECIPES = new ArrayList<>();
	private static final Map<String, List<Recipe>> BY_OUT = new HashMap<>();
	private static final Map<String, List<Recipe>> BY_IN = new HashMap<>();
	private static final Map<String, String> KEY_BY_NAME = new HashMap<>();

	/** 아이콘 캐시(바닐라 대체품) + 게임에서 배운 실제 아이템. */
	private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();
	private static final Map<String, ItemStack> LEARNED = new HashMap<>();

	private NeogulData() {
	}

	// ==================== 읽기 ====================

	private static synchronized void ensure() {
		if (loaded) {
			return;
		}
		loaded = true;
		STATIONS.clear();
		CATEGORIES.clear();
		ITEMS.clear();
		RECIPES.clear();
		try (InputStream in = NeogulData.class.getResourceAsStream(BUNDLED)) {
			if (in != null) {
				merge(read(new InputStreamReader(in, StandardCharsets.UTF_8)));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogul:bundled", t);
		}
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul_items.json");
			if (Files.isRegularFile(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					JsonObject o = read(r);
					if (o != null && o.has("replace") && o.get("replace").getAsBoolean()) {
						STATIONS.clear();
						CATEGORIES.clear();
						ITEMS.clear();
						RECIPES.clear();
					}
					merge(o);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogul:override", t);
		}
		index();
	}

	/** 자료를 다시 읽는다(설정 폴더 파일을 고친 뒤). */
	public static synchronized void reload() {
		loaded = false;
		ICON_CACHE.clear();
		ensure();
	}

	private static JsonObject read(Reader r) {
		return new Gson().fromJson(r, JsonObject.class);
	}

	private static List<String> strings(JsonElement e) {
		List<String> out = new ArrayList<>();
		if (e == null || e.isJsonNull()) {
			return out;
		}
		if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				out.add(x.getAsString());
			}
		} else {
			out.add(e.getAsString());
		}
		return out;
	}

	/** "from": ["farm", {"at": "dg_farm", "note": "닭 Lv.1~3 100%"}] 둘 다 받는다. */
	private static List<Source> sources(JsonElement e) {
		List<Source> out = new ArrayList<>();
		if (e == null || e.isJsonNull()) {
			return out;
		}
		JsonArray arr;
		if (e.isJsonArray()) {
			arr = e.getAsJsonArray();
		} else {
			arr = new JsonArray();
			arr.add(e);
		}
		for (JsonElement x : arr) {
			if (x.isJsonObject()) {
				JsonObject o = x.getAsJsonObject();
				out.add(new Source(str(o, "at", ""), str(o, "note", "")));
			} else if (!x.isJsonNull()) {
				out.add(new Source(x.getAsString(), ""));
			}
		}
		return out;
	}

	private static String str(JsonObject o, String k, String def) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? def : e.getAsString();
	}

	private static void merge(JsonObject root) {
		if (root == null) {
			return;
		}
		if (root.has("stations")) {
			for (Map.Entry<String, JsonElement> m : root.getAsJsonObject("stations").entrySet()) {
				JsonObject o = m.getValue().getAsJsonObject();
				STATIONS.put(m.getKey(), new Station(m.getKey(), str(o, "name", m.getKey()), str(o, "note", ""), strings(o.get("icon"))));
			}
		}
		if (root.has("categories")) {
			for (JsonElement e : root.getAsJsonArray("categories")) {
				JsonObject o = e.getAsJsonObject();
				String id = str(o, "id", "");
				CATEGORIES.removeIf(c -> c.id().equals(id));
				CATEGORIES.add(new Category(id, str(o, "name", id), strings(o.get("icon"))));
			}
		}
		if (root.has("items")) {
			for (JsonElement e : root.getAsJsonArray("items")) {
				JsonObject o = e.getAsJsonObject();
				String key = str(o, "key", null);
				if (key == null) {
					continue;
				}
				ITEMS.put(key, new Entry(key, str(o, "name", key), str(o, "cat", ""), strings(o.get("icon")), sources(o.get("from"))));
			}
		}
		if (root.has("recipes")) {
			for (JsonElement e : root.getAsJsonArray("recipes")) {
				JsonObject o = e.getAsJsonObject();
				List<Ing> ins = new ArrayList<>();
				JsonArray arr = o.has("in") ? o.getAsJsonArray("in") : new JsonArray();
				for (JsonElement x : arr) {
					JsonArray pair = x.getAsJsonArray();
					ins.add(new Ing(pair.get(0).getAsString(), pair.size() > 1 ? pair.get(1).getAsInt() : 1));
				}
				String out = str(o, "out", null);
				if (out != null) {
					RECIPES.add(new Recipe(str(o, "at", "craft"), out, o.has("n") ? o.get("n").getAsInt() : 1, ins, str(o, "note", "")));
				}
			}
		}
	}

	private static void index() {
		BY_OUT.clear();
		BY_IN.clear();
		KEY_BY_NAME.clear();
		for (Recipe r : RECIPES) {
			BY_OUT.computeIfAbsent(r.out(), k -> new ArrayList<>()).add(r);
			for (Ing i : r.in()) {
				List<Recipe> l = BY_IN.computeIfAbsent(i.key(), k -> new ArrayList<>());
				if (!l.contains(r)) {
					l.add(r);
				}
			}
		}
		for (Entry e : ITEMS.values()) {
			KEY_BY_NAME.put(normalize(e.name()), e.key());
		}
	}

	// ==================== 조회 ====================

	public static List<Entry> items() {
		ensure();
		return new ArrayList<>(ITEMS.values());
	}

	public static Entry get(String key) {
		ensure();
		return key == null ? null : ITEMS.get(key);
	}

	public static List<Category> categories() {
		ensure();
		return Collections.unmodifiableList(CATEGORIES);
	}

	public static Station station(String id) {
		ensure();
		return id == null ? null : STATIONS.get(id);
	}

	/** 이걸 만드는 방법들. */
	public static List<Recipe> recipesFor(String key) {
		ensure();
		return BY_OUT.getOrDefault(key, Collections.emptyList());
	}

	/** 이걸 재료로 쓰는 것들. */
	public static List<Recipe> usesOf(String key) {
		ensure();
		return BY_IN.getOrDefault(key, Collections.emptyList());
	}

	/** 너굴마을(mcng.kr)에 접속해 있는지 - 서버 주소로 판단. */
	public static boolean onNeogul(MinecraftClient client) {
		try {
			String a = LunaCompat.currentServerAddress(client);
			return a != null && a.toLowerCase(java.util.Locale.ROOT).contains("mcng");
		} catch (Throwable t) {
			return false;
		}
	}

	// ==================== 아이콘 ====================

	/** 아이템 아이콘(배운 실제 아이템 우선, 없으면 바닐라 대체품). count가 1보다 크면 그 개수로. */
	public static ItemStack icon(Entry e, int count) {
		if (e == null) {
			return ItemStack.EMPTY;
		}
		ItemStack base = LEARNED.get(e.key());
		if (base == null) {
			base = ICON_CACHE.computeIfAbsent("i:" + e.key(), k -> vanilla(e.icons()));
		}
		return withCount(base, count);
	}

	public static ItemStack icon(Station s) {
		if (s == null) {
			return ItemStack.EMPTY;
		}
		return ICON_CACHE.computeIfAbsent("s:" + s.id(), k -> vanilla(s.icons()));
	}

	public static ItemStack icon(Category c) {
		if (c == null) {
			return ItemStack.EMPTY;
		}
		return ICON_CACHE.computeIfAbsent("c:" + c.id(), k -> vanilla(c.icons()));
	}

	/** 아무 바닐라 id 목록에서 먼저 있는 것. */
	public static ItemStack vanilla(List<String> ids) {
		if (ids != null) {
			for (String id : ids) {
				try {
					Item item = LunaCompat.itemById(id);
					if (item != null) {
						return new ItemStack(item);
					}
				} catch (Throwable ignored) {
				}
			}
		}
		Item fallback = LunaCompat.itemById("minecraft:paper");
		return fallback == null ? ItemStack.EMPTY : new ItemStack(fallback);
	}

	private static ItemStack withCount(ItemStack base, int count) {
		if (base.isEmpty() || count <= 1) {
			return base;
		}
		ItemStack c = base.copy();
		c.setCount(Math.min(99, count));
		return c;
	}

	/** 게임에서 본 아이템 - 이름이 자료의 아이템과 같으면 그 모양을 아이콘으로 기억한다. */
	public static void learn(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ensure();
		String name;
		try {
			name = stack.getName().getString();
		} catch (Throwable t) {
			return;
		}
		String key = KEY_BY_NAME.get(normalize(name));
		if (key == null || LEARNED.containsKey(key)) {
			return;
		}
		ItemStack c = stack.copy();
		c.setCount(1);
		LEARNED.put(key, c);
	}

	/** 비교용 이름: 색 코드, 앞의 [말머리], 공백 제거. */
	public static String normalize(String s) {
		String t = ChatState.stripFormatting(s).trim();
		while (t.startsWith("[")) {
			int close = t.indexOf(']');
			if (close < 0) {
				break;
			}
			t = t.substring(close + 1).trim();
		}
		return t.replace(" ", "");
	}
}
