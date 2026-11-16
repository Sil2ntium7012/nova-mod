package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 49-669차(사용자: "작물 하나 캘 때 몇 개 나오는지 확률 계산해 주는 거 만들어 줘, 허수아비같이. 너굴마을이 행운이 높게 적용돼서
 * 수박 호박 사과 밀 벼 등등. 사과는 몇 개의 나뭇잎에 하나씩 나오는지 확률로, 다른 건 하나에 몇 개 나오는지"):
 * <b>작물 드랍 재기</b>.
 *
 * <p>캔 것: 다 자란 작물(허수아비와 같은 기준, TodoGameModeMixin.destroyBlock)과 내가 부순 나뭇잎.
 * 얻은 것: 가방 안 그 작물 아이템 수가 늘어난 만큼(자동 줍기든 바닥에서 줍든). 그 작물을 15초 안에 캤을 때만 센다
 * (상자에서 꺼내기, 거래 등은 빼려고 - 화면이 열려 있으면 안 셈, 방금 버렸다 다시 주운 만큼도 뺌).
 * 아이템은 바닐라 id 또는 이름(씨앗 제외)으로 고른다 - 너굴마을 리소스팩 아이템도 이름에 "밀", "수박"이 들어간다.
 * 저장: config/lunaslight/neogul-droprate.json.
 */
public final class NeogulDropRate {
	private NeogulDropRate() {
	}

	/** 작물 하나: 블록 이름(path), 아이템 id(path), 화면 이름. */
	public record Crop(String key, String item, String label, boolean perLeaves) {
	}

	public static final Map<String, Crop> CROPS = new LinkedHashMap<>();

	static {
		add("melon", "melon_slice", "수박", false);
		add("pumpkin", "pumpkin", "호박", false);
		add("wheat", "wheat", "밀", false);
		add("beetroots", "beetroot", "벼", false);
		add("potatoes", "potato", "감자", false);
		add("carrots", "carrot", "당근", false);
		add("nether_wart", "nether_wart", "고추", false);
		add("leaves", "apple", "사과", true);
	}

	private static void add(String key, String item, String label, boolean perLeaves) {
		CROPS.put(key, new Crop(key, item, label, perLeaves));
	}

	/** 작물마다 [캔 수, 얻은 수]. */
	public static final Map<String, long[]> STATS = new LinkedHashMap<>();
	/** 마지막으로 캔 작물(HUD에서 강조). */
	public static volatile String lastKey;

	private static final Map<String, Long> lastBreak = new HashMap<>();
	private static final long WINDOW_MS = 15_000L;
	private static final long LOSS_MS = 10_000L;
	private static Map<String, Integer> lastCounts;
	private static final Map<String, long[]> recentLoss = new HashMap<>();
	private static Object lastPlayer, lastLevel;
	private static long graceUntil;
	private static boolean loaded, registered, dirty;
	private static long lastSave;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NeogulDropRate::tick);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> save());
		} catch (Throwable t) {
			LunaCompat.warnOnce("dropRate:init", t);
		}
	}

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-droprate.json");
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
				for (String k : CROPS.keySet()) {
					if (o.has(k)) {
						JsonObject c = o.getAsJsonObject(k);
						STATS.put(k, new long[] {c.get("b").getAsLong(), c.get("g").getAsLong()});
					}
				}
				if (o.has("last")) {
					lastKey = o.get("last").getAsString();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dropRate:load", t);
		}
	}

	public static synchronized void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		try {
			JsonObject o = new JsonObject();
			for (Map.Entry<String, long[]> e : STATS.entrySet()) {
				JsonObject c = new JsonObject();
				c.addProperty("b", e.getValue()[0]);
				c.addProperty("g", e.getValue()[1]);
				o.add(e.getKey(), c);
			}
			if (lastKey != null) {
				o.addProperty("last", lastKey);
			}
			Path p = file();
			Files.createDirectories(p.getParent());
			Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("dropRate:save", t);
		}
	}

	public static synchronized void reset() {
		load();
		STATS.clear();
		lastKey = null;
		dirty = true;
		save();
	}

	private static void maybeSave() {
		dirty = true;
		long now = System.currentTimeMillis();
		if (now - lastSave > 30_000L) {
			lastSave = now;
			save();
		}
	}

	/** 블록 상태 글자에서 path("minecraft:wheat" → "wheat"). */
	private static String pathOf(BlockState st) {
		String s = String.valueOf(st);
		int a = s.indexOf('{'), b = s.indexOf('}');
		String id = a >= 0 && b > a ? s.substring(a + 1, b) : s;
		return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
	}

	/** TodoGameModeMixin: 블록을 캤다(캐기 전 상태). */
	public static void onBreak(BlockState st) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (st == null || !NeogulData.onNeogul(mc)) {
				return;
			}
			String path = pathOf(st);
			String key;
			if (path.endsWith("_leaves")) {
				key = "leaves";
			} else if (CROPS.containsKey(path) && NeogulScareRate.matureCrop(st)) {
				key = path;
			} else {
				return;
			}
			synchronized (NeogulDropRate.class) {
				load();
				STATS.computeIfAbsent(key, k -> new long[2])[0]++;
				lastBreak.put(key, System.currentTimeMillis());
				lastKey = key;
				maybeSave();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dropRate:break", t);
		}
	}

	/** 이 아이템이 어느 작물 것인지(없으면 null). */
	private static String cropOf(ItemStack s) {
		try {
			net.minecraft.resources.Identifier id = LunaCompat.getItemId(s.getItem());
			String path = id == null ? "" : id.getPath();
			String name = s.getHoverName().getString();
			if (name.contains("씨앗") || name.contains("씨 ") || name.endsWith("씨") || path.endsWith("_seeds")) {
				return null;
			}
			for (Crop c : CROPS.values()) {
				if (path.equals(c.item())) {
					return c.key();
				}
			}
			String n = name.replaceAll("§.", "").replace(" ", "");
			for (Crop c : CROPS.values()) {
				if (n.equals(c.label()) || n.endsWith(c.label())) {
					return c.key();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Map<String, Integer> countInventory(Minecraft mc) {
		Map<String, Integer> out = new HashMap<>();
		Object inv = LunaCompat.getPlayerInventory(mc.player);
		if (inv == null) {
			return out;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 0; i < size; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty()) {
				String k = cropOf(s);
				if (k != null) {
					out.merge(k, s.getCount(), Integer::sum);
				}
			}
		}
		return out;
	}

	private static void tick(Minecraft mc) {
		try {
			if (mc.player == null || mc.level == null) {
				lastCounts = null;
				return;
			}
			long now = System.currentTimeMillis();
			if (mc.player != lastPlayer || mc.level != lastLevel) {
				lastPlayer = mc.player;
				lastLevel = mc.level;
				lastCounts = null;
				recentLoss.clear();
				graceUntil = now + 5000;   // 월드를 옮기면 서버가 가방을 다시 보낸다 - 그동안은 안 셈
			}
			if (!NeogulData.onNeogul(mc)) {
				lastCounts = null;
				return;
			}
			Map<String, Integer> counts = countInventory(mc);
			if (lastCounts == null || LunaCompat.screenOf(mc) != null || now < graceUntil) {
				lastCounts = counts;   // 상자, 가방 화면에서 옮긴 것은 안 셈
				return;
			}
			recentLoss.values().removeIf(l -> now > l[1]);
			boolean changed = false;
			for (Map.Entry<String, Integer> e : lastCounts.entrySet()) {
				int lost = e.getValue() - counts.getOrDefault(e.getKey(), 0);
				if (lost > 0) {
					long[] l = recentLoss.computeIfAbsent(e.getKey(), k -> new long[2]);
					l[0] += lost;
					l[1] = now + LOSS_MS;
				}
			}
			for (Map.Entry<String, Integer> e : counts.entrySet()) {
				int gained = e.getValue() - lastCounts.getOrDefault(e.getKey(), 0);
				if (gained <= 0) {
					continue;
				}
				long[] l = recentLoss.get(e.getKey());
				if (l != null) {
					int back = (int) Math.min(gained, l[0]);
					gained -= back;
					l[0] -= back;
				}
				Long br = lastBreak.get(e.getKey());
				if (gained > 0 && br != null && now - br <= WINDOW_MS) {
					synchronized (NeogulDropRate.class) {
						load();
						STATS.computeIfAbsent(e.getKey(), k -> new long[2])[1] += gained;
					}
					changed = true;
				}
			}
			lastCounts = counts;
			if (changed) {
				synchronized (NeogulDropRate.class) {
					maybeSave();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dropRate:tick", t);
		}
	}
}
