package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 49-27차: "내가 열어 본 상자"의 위치와 내용물 색인(아이템 찾기 기능의 저장소).
 *
 * 월드마다 파일 하나(config/lunaslight/containers/&lt;월드 키&gt;.json). 월드 키는 서버 주소(싱글은 월드 이름)와
 * 차원 이름을 합친 것. 한 월드에 최대 400곳까지 기억하고, 넘치면 오래 안 본 것부터 지운다.
 *
 * 상자 안에 든 셜커 상자는 그 내용물까지 같이 기록해서 "어느 셜커에 있는지"까지 찾을 수 있게 한다.
 * 아이템은 종류(등록 id)와 개수만 기록한다 - NBT는 저장하지 않으므로 파일이 작고, 찾기에는 충분하다.
 */
public final class ContainerIndex {
	private ContainerIndex() {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int MAX_PER_WORLD = 400;

	/** 상자 한 곳. items: 아이템 id → 개수, shulkers: 상자 안 셜커별 (셜커 이름 → (아이템 id → 개수)). */
	public static final class Entry {
		public int x, y, z;
		public String title = "상자";
		public long seen;
		/** 49-256차: 큰 상자면 다른 반쪽까지의 차이(-1/0/1). 기록 자리(x,y,z)는 두 칸 중 x, z가 작은 쪽. */
		public int ox, oz;
		public final Map<String, Integer> items = new LinkedHashMap<>();
		public final Map<String, Map<String, Integer>> shulkers = new LinkedHashMap<>();
	}

	/** 찾기 결과 한 줄. */
	public static final class Hit {
		public final Entry entry;
		public final String shulker;   // 셜커 안이면 그 이름, 아니면 null
		public final int count;
		public final boolean inInventory; // 내 인벤토리의 셜커면 true(좌표 없음)

		Hit(Entry entry, String shulker, int count, boolean inInventory) {
			this.entry = entry;
			this.shulker = shulker;
			this.count = count;
			this.inInventory = inInventory;
		}

		/**
		 * 49-47차(사용자: "글이 너무 많고 한 줄에 몰아넣고 있고"): HUD 줄에 쓰는 <b>짧은</b> 이름.
		 * 좌표는 빼고(월드 빛기둥이 어디인지 알려 준다) 장소 이름만 - "상자", "파란 셜커", "인벤토리".
		 * 셜커가 상자 안에 있으면 바깥 상자 이름은 생략한다(가서 열어 보면 보인다).
		 */
		public String shortName() {
			if (inInventory) {
				return "인벤토리";
			}
			if (shulker != null) {
				return shulker.replace(" 셜커 상자", " 셜커").replace("셜커 상자", "셜커");
			}
			return entry.title;
		}

		public String describe() {
			StringBuilder sb = new StringBuilder();
			if (inInventory) {
				sb.append("내 인벤토리의 ").append(shulker);
			} else if (shulker != null) {
				sb.append(shulker).append(" · ").append(entry.title)
					.append(" (").append(entry.x).append(", ").append(entry.y).append(", ").append(entry.z).append(") 안");
			} else {
				sb.append(entry.title)
					.append(" (").append(entry.x).append(", ").append(entry.y).append(", ").append(entry.z).append(")");
			}
			sb.append(" ×").append(count);
			return sb.toString();
		}

		public double distance(MinecraftClient client) {
			if (inInventory || client.player == null) {
				return 0;
			}
			double dx = entry.x + 0.5 - EntityPos.x(client.player);
			double dy = entry.y + 0.5 - EntityPos.y(client.player);
			double dz = entry.z + 0.5 - EntityPos.z(client.player);
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		/** 플레이어 기준 방향(북/북동/…). 인벤토리 안이면 빈 문자열. */
		public String direction(MinecraftClient client) {
			if (inInventory || client.player == null) {
				return "";
			}
			double dx = entry.x + 0.5 - EntityPos.x(client.player);
			double dz = entry.z + 0.5 - EntityPos.z(client.player);
			double angle = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = 남(+Z)
			if (angle < 0) {
				angle += 360;
			}
			String[] names = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};
			return names[(int) Math.round(angle / 45.0) % 8];
		}
	}

	// ==================== 월드 키 ====================

	public static String worldKey(MinecraftClient client) {   // 49-260차: 설계도 상태 저장도 같은 키를 쓴다
		// 49-246차(사용자: "아이템 찾기가 계속 상자 연 적도 없는 곳에 상자가 있다고 떠"): 싱글 월드 키가 월드 이름을 영문/숫자만 남기고 뭉갠 것이라
		// 한글 이름 월드("새로운 세계" 등)가 전부 "_____" 파일 하나를 같이 썼다 - 다른 월드에서 연 상자가 새 월드에 떴다.
		// 이제 싱글은 저장 폴더 이름 + 지문(crc32)으로 월드마다 따로. 서버는 주소라 예전 키 그대로(기록 유지).
		String folder = LunaCompat.currentWorldFolder(client);
		if (folder != null && !folder.isEmpty()) {
			java.util.zip.CRC32 crc = new java.util.zip.CRC32();
			crc.update(folder.getBytes(StandardCharsets.UTF_8));
			String base = folder.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
			String raw = "sp-" + base + "-" + String.format("%08x", crc.getValue()) + "@" + dimension(client);
			return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._@-]", "_");
		}
		String server = LunaCompat.currentServerLabel(client);
		if (server == null || server.isEmpty()) {
			server = "unknown";
		}
		String dim = dimension(client);
		String raw = server + "@" + dim;
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._@-]", "_");
	}

	private static String dimension(MinecraftClient client) {
		try {
			Object key = LunaCompat.invokeNoArg(client.world, "getRegistryKey");
			Object value = key == null ? null : LunaCompat.invokeNoArg(key, "getValue");
			return value == null ? "overworld" : String.valueOf(value);
		} catch (Throwable ignored) {
			return "overworld";
		}
	}

	private static Path fileFor(String key) {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("containers").resolve(key + ".json");
	}

	// ==================== 캐시 ====================

	private static String loadedKey;
	private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
	private static boolean dirty;
	private static long lastSaveNanos;

	private static Map<String, Entry> world(MinecraftClient client) {
		String key = worldKey(client);
		if (!key.equals(loadedKey)) {
			save(); // 이전 월드 저장
			CACHE.clear();
			loadedKey = key;
			load(key);
		}
		return CACHE;
	}

	private static void load(String key) {
		Path file = fileFor(key);
		if (!Files.exists(file)) {
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
			JsonArray arr = root.get("containers").getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) {
				JsonObject o = arr.get(i).getAsJsonObject();
				Entry e = new Entry();
				e.x = o.get("x").getAsInt();
				e.y = o.get("y").getAsInt();
				e.z = o.get("z").getAsInt();
				e.title = o.has("title") ? o.get("title").getAsString() : "상자";
				e.seen = o.has("seen") ? o.get("seen").getAsLong() : 0L;
				e.ox = o.has("ox") ? o.get("ox").getAsInt() : 0;
				e.oz = o.has("oz") ? o.get("oz").getAsInt() : 0;
				if (o.has("items")) {
					for (Map.Entry<String, com.google.gson.JsonElement> it : o.get("items").getAsJsonObject().entrySet()) {
						e.items.put(it.getKey(), it.getValue().getAsInt());
					}
				}
				if (o.has("shulkers")) {
					for (Map.Entry<String, com.google.gson.JsonElement> sh : o.get("shulkers").getAsJsonObject().entrySet()) {
						Map<String, Integer> inner = new LinkedHashMap<>();
						for (Map.Entry<String, com.google.gson.JsonElement> it : sh.getValue().getAsJsonObject().entrySet()) {
							inner.put(it.getKey(), it.getValue().getAsInt());
						}
						e.shulkers.put(sh.getKey(), inner);
					}
				}
				CACHE.put(posKey(e.x, e.y, e.z), e);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerIndex:load", t);
		}
	}

	/** 저장(변경이 있을 때만, 10초에 한 번 이상은 안 씀). */
	public static void save() {
		if (!dirty || loadedKey == null) {
			return;
		}
		try {
			Path file = fileFor(loadedKey);
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			JsonArray arr = new JsonArray();
			for (Entry e : CACHE.values()) {
				JsonObject o = new JsonObject();
				o.addProperty("x", e.x);
				o.addProperty("y", e.y);
				o.addProperty("z", e.z);
				o.addProperty("title", e.title);
				o.addProperty("seen", e.seen);
				if (e.ox != 0 || e.oz != 0) {
					o.addProperty("ox", e.ox);
					o.addProperty("oz", e.oz);
				}
				JsonObject items = new JsonObject();
				for (Map.Entry<String, Integer> it : e.items.entrySet()) {
					items.addProperty(it.getKey(), it.getValue());
				}
				o.add("items", items);
				if (!e.shulkers.isEmpty()) {
					JsonObject sh = new JsonObject();
					for (Map.Entry<String, Map<String, Integer>> s : e.shulkers.entrySet()) {
						JsonObject inner = new JsonObject();
						for (Map.Entry<String, Integer> it : s.getValue().entrySet()) {
							inner.addProperty(it.getKey(), it.getValue());
						}
						sh.add(s.getKey(), inner);
					}
					o.add("shulkers", sh);
				}
				arr.add(o);
			}
			root.add("containers", arr);
			try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			dirty = false;
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerIndex:save", t);
		}
	}

	private static String posKey(int x, int y, int z) {
		return x + ":" + y + ":" + z;
	}

	// ==================== 기록 ====================

	/** 상자 하나의 내용물을 기록(같은 위치면 덮어씀). */
	/** 49-125차(인벤토리 탭 미리보기): 이 위치의 기록(마지막으로 열어 봤을 때의 내용물). 없으면 null. */
	public static Entry get(MinecraftClient client, BlockPos pos) {
		if (client == null || client.world == null || pos == null) {
			return null;
		}
		try {
			Map<String, Entry> map = world(client);
			Entry e = map.get(posKey(pos.getX(), pos.getY(), pos.getZ()));
			if (e == null) {
				BlockPos other = otherHalf(client, pos);   // 49-256차: 큰 상자의 다른 반쪽 자리에 기록됐을 수도
				if (other != null) {
					e = map.get(posKey(other.getX(), other.getY(), other.getZ()));
				}
			}
			return e;
		} catch (Throwable t) {
			return null;
		}
	}

	// ==================== 49-256차: 큰 상자 ====================
	// 사용자: "큰 상자에 한 셋 들어가 있는데 상자에 각각 표시돼, 상자에서 빼도 한쪽 상자에서만 사라진 판정". 큰 상자를 왼쪽에서 열면 왼쪽 자리에,
	// 오른쪽에서 열면 오른쪽 자리에 54칸 전체가 따로 적혀 두 번 떴다. 이제 두 칸 중 x, z가 작은 쪽 한 자리에만 적고 다른 반쪽의 기록은 지운다.

	private static final String[] DIRS = {"north", "east", "south", "west"};
	private static final int[][] DXZ = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	/** pos가 큰 상자(상자/덫 상자, type=left/right)의 반쪽이면 다른 반쪽 자리, 아니면 null. 블록 상태 글자로 판단(버전 무관). */
	public static BlockPos otherHalf(MinecraftClient client, BlockPos pos) {
		try {
			if (client == null || client.world == null || pos == null) {
				return null;
			}
			String st = String.valueOf(client.world.getBlockState(pos));
			if (!st.contains("chest") || st.contains("ender_chest")) {
				return null;
			}
			boolean left = st.contains("type=left"), right = st.contains("type=right");
			if (!left && !right) {
				return null;
			}
			int fi = st.indexOf("facing=");
			if (fi < 0) {
				return null;
			}
			String f = st.substring(fi + 7).split("[,\\]]")[0];
			int d = -1;
			for (int i = 0; i < 4; i++) {
				if (DIRS[i].equals(f)) {
					d = i;
				}
			}
			if (d < 0) {
				return null;
			}
			// 바닐라 ChestBlock.getFacing: 왼쪽 반쪽이면 바라보는 방향의 시계 방향, 오른쪽이면 반시계 방향에 짝이 있다
			int nd = left ? (d + 1) % 4 : (d + 3) % 4;
			return new BlockPos(pos.getX() + DXZ[nd][0], pos.getY(), pos.getZ() + DXZ[nd][1]);
		} catch (Throwable t) {
			return null;
		}
	}

	/** 큰 상자 두 칸 중 기록에 쓰는 자리(x, z가 작은 쪽). */
	private static BlockPos canonical(BlockPos pos, BlockPos other) {
		if (other == null) {
			return pos;
		}
		if (other.getX() < pos.getX() || (other.getX() == pos.getX() && other.getZ() < pos.getZ())) {
			return other;
		}
		return pos;
	}

	private static long lastDoubleFix;

	/** 예전에 반쪽마다 따로 적힌 큰 상자 기록을 (불러온 청크에서) 한 자리로 합친다 - 최근에 연 쪽을 남긴다. */
	private static void mergeDoubles(MinecraftClient client, Map<String, Entry> map) {
		long now = System.currentTimeMillis();
		if (now - lastDoubleFix < 2000) {
			return;
		}
		lastDoubleFix = now;
		for (Entry e : new ArrayList<>(map.values())) {
			String key = posKey(e.x, e.y, e.z);
			if (map.get(key) != e || !loaded(client, e.x, e.z)) {
				continue;
			}
			BlockPos pos = new BlockPos(e.x, e.y, e.z);
			BlockPos other = otherHalf(client, pos);
			if (other == null) {
				if (e.ox != 0 || e.oz != 0) {
					e.ox = 0;
					e.oz = 0;   // 큰 상자가 한 칸으로 바뀜
					dirty = true;
				}
				continue;
			}
			BlockPos c = canonical(pos, other);
			BlockPos p2 = c.equals(pos) ? other : pos;
			String otherKey = posKey(other.getX(), other.getY(), other.getZ());
			Entry twin = map.get(otherKey);
			Entry keep = twin != null && twin != e && twin.seen > e.seen ? twin : e;
			int nox = p2.getX() - c.getX(), noz = p2.getZ() - c.getZ();
			boolean changed = twin != null && twin != e || keep.x != c.getX() || keep.z != c.getZ() || keep.ox != nox || keep.oz != noz;
			if (!changed) {
				continue;
			}
			map.remove(otherKey);
			map.remove(key);
			keep.x = c.getX();
			keep.y = c.getY();
			keep.z = c.getZ();
			keep.ox = nox;
			keep.oz = noz;
			map.put(posKey(keep.x, keep.y, keep.z), keep);
			dirty = true;
		}
	}

	/**
	 * 49-194차(블록 정보 - 엔더 상자): 제목이 title인 기록 중 가장 최근 것. 엔더 상자는 어디서 열어도 내용물이 같아서
	 * 위치 대신 제목(엔더 상자 화면 제목 = 블록 이름)으로 찾는다. 없으면 null.
	 */
	public static Entry latestTitled(MinecraftClient client, String title) {
		if (client == null || client.world == null || title == null) {
			return null;
		}
		try {
			Entry best = null;
			for (Entry e : world(client).values()) {
				if (title.equals(e.title) && (best == null || e.seen > best.seen)) {
					best = e;
				}
			}
			return best;
		} catch (Throwable t) {
			return null;
		}
	}

	public static void record(MinecraftClient client, BlockPos pos, String title, List<ItemStack> contents) {
		if (client == null || client.world == null || pos == null) {
			return;
		}
		Map<String, Entry> map = world(client);
		Entry e = new Entry();
		// 49-256차: 큰 상자는 두 칸 중 한 자리에만
		BlockPos other = otherHalf(client, pos);
		BlockPos c = canonical(pos, other);
		if (other != null) {
			map.remove(posKey(other.getX(), other.getY(), other.getZ()));
			map.remove(posKey(pos.getX(), pos.getY(), pos.getZ()));
			BlockPos p2 = c.equals(pos) ? other : pos;
			e.ox = p2.getX() - c.getX();
			e.oz = p2.getZ() - c.getZ();
		}
		e.x = c.getX();
		e.y = c.getY();
		e.z = c.getZ();
		e.title = title == null || title.isEmpty() ? "상자" : title;
		e.seen = System.currentTimeMillis();
		for (ItemStack stack : contents) {
			String id = idOf(stack);
			if (id == null) {
				continue;
			}
			e.items.merge(id, stack.getCount(), Integer::sum);
			// 셜커 상자면 안쪽까지
			List<ItemStack> inner = shulkerContents(stack);
			if (inner != null) {
				String name = stack.getName().getString();
				Map<String, Integer> counts = e.shulkers.computeIfAbsent(name, k -> new LinkedHashMap<>());
				for (ItemStack in : inner) {
					String innerId = idOf(in);
					if (innerId != null) {
						counts.merge(innerId, in.getCount(), Integer::sum);
					}
				}
			}
		}
		map.put(posKey(e.x, e.y, e.z), e);
		dirty = true;
		trim(map);
		long now = System.nanoTime();
		if (now - lastSaveNanos > 10_000_000_000L) {
			lastSaveNanos = now;
			save();
		}
	}

	private static void trim(Map<String, Entry> map) {
		if (map.size() <= MAX_PER_WORLD) {
			return;
		}
		List<Map.Entry<String, Entry>> list = new ArrayList<>(map.entrySet());
		list.sort(Comparator.comparingLong(a -> a.getValue().seen));
		for (int i = 0; i < list.size() - MAX_PER_WORLD; i++) {
			map.remove(list.get(i).getKey());
		}
	}

	private static String idOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		net.minecraft.util.Identifier id = LunaCompat.getItemId(stack.getItem());
		return id == null ? null : id.toString();
	}

	/** 셜커 상자면 내용물, 아니면 null. */
	private static List<ItemStack> shulkerContents(ItemStack stack) {
		String id = idOf(stack);
		if (id == null || !id.endsWith("shulker_box")) {
			return null;
		}
		return LunaCompat.containerSlots(stack, 27);
	}

	// ==================== 찾기 ====================

	/** target과 같은 종류의 아이템이 있는 곳을 가까운 순서로. 내 인벤토리의 셜커도 포함. */
	public static List<Hit> find(MinecraftClient client, ItemStack target, int limit) {
		List<Hit> out = new ArrayList<>();
		String id = idOf(target);
		if (id == null || client.player == null) {
			return out;
		}
		// ① 내 인벤토리의 셜커 상자 안
		try {
			net.minecraft.entity.player.PlayerInventory inv = LunaCompat.getPlayerInventory(client.player);
			for (int i = 0; i < 41; i++) {
				ItemStack s;
				try {
					s = LunaCompat.invGetStack(inv, i); // 49-36차: 1.15.2 getInvStack
				} catch (Throwable ignored) {
					break;
				}
				List<ItemStack> inner = shulkerContents(s);
				if (inner == null) {
					continue;
				}
				int count = 0;
				for (ItemStack in : inner) {
					if (id.equals(idOf(in))) {
						count += in.getCount();
					}
				}
				if (count > 0) {
					out.add(new Hit(null, s.getName().getString(), count, true));
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerIndex:invScan", t);
		}

		// ② 기록해 둔 상자들
		Map<String, Entry> map = world(client);
		pruneNear(client, map);
		mergeDoubles(client, map);
		List<Hit> world = new ArrayList<>();
		for (Entry e : map.values()) {
			Integer direct = e.items.get(id);
			if (direct != null && direct > 0) {
				world.add(new Hit(e, null, direct, false));
			}
			for (Map.Entry<String, Map<String, Integer>> sh : e.shulkers.entrySet()) {
				Integer c = sh.getValue().get(id);
				if (c != null && c > 0) {
					world.add(new Hit(e, sh.getKey(), c, false));
				}
			}
		}
		world.sort(Comparator.comparingDouble(h -> h.distance(client)));
		out.addAll(world);
		if (out.size() > limit) {
			return new ArrayList<>(out.subList(0, limit));
		}
		return out;
	}


	// ==================== 49-234차: 진짜 상자만 ====================
	// 사용자: "아이템 찾기 상자 없는 곳에 막 있다고 뜨고 버그가 심해". 예전엔 화면이 뜨기 직전 바라본 블록을 무조건 상자 자리로
	// 적어서, 서버 메뉴(명령어/NPC로 여는 상자 모양 화면)나 엉뚱한 블록을 보던 중 뜬 화면이 땅이나 벽에 "상자"로 남았다.
	// 이제 그 자리에 보관함 블록(상자, 통, 셜커, 호퍼 등 + 엔더 상자)이 실제로 있고 칸 수가 맞을 때만 적고,
	// 찾을 때 가까이(32블록) 있는 기록 중 블록이 없어진 곳은 지운다.

	/** pos에 보관함 블록이 있고(엔더 상자 포함) 화면의 상자 칸 수가 그 블록과 맞으면 true. slots &lt; 0이면 칸 수는 안 본다. */
	public static boolean isStorageAt(MinecraftClient client, BlockPos pos, int slots) {
		if (client == null || client.world == null || pos == null) {
			return false;
		}
		try {
			Object be = client.world.getBlockEntity(pos);
			if (be instanceof net.minecraft.block.entity.EnderChestBlockEntity) {
				return true;
			}
			if (!(be instanceof net.minecraft.inventory.Inventory)) {
				return false;
			}
			if (slots < 0) {
				return true;
			}
			int size = containerSize(be);
			return size <= 0 || slots == size || slots == size * 2;   // 큰 상자는 두 칸 합
		} catch (Throwable t) {
			return true;   // 확인을 못 하면 예전처럼
		}
	}

	private static int containerSize(Object be) {
		int n = LunaCompat.callNoArgInt(be, "size", -1);
		if (n < 0) {
			n = LunaCompat.callNoArgInt(be, "getInvSize", -1);   // 1.15.2 이하 이름
		}
		return n;
	}

	/**
	 * 49-245차: 블록이 불러와진 청크에 있는 기록 중 그 자리에 보관함 블록이 없는 곳을 전부 지운다(예전엔 가로 32블록 안만 - 그 밖의 잘못된 기록이
	 * 처음 찾을 때 엉뚱한 곳에 "상자"로 떴다). 불러오지 않은 청크는 모르니 그대로 둔다.
	 */
	private static void pruneNear(MinecraftClient client, Map<String, Entry> map) {
		if (client.player == null || client.world == null) {
			return;
		}
		boolean removed = map.values().removeIf(e -> loaded(client, e.x, e.z) && !isStorageAt(client, new BlockPos(e.x, e.y, e.z), -1));
		if (removed) {
			dirty = true;
		}
	}

	private static boolean loaded(MinecraftClient client, int x, int z) {
		try {
			return client.world.isChunkLoaded(x >> 4, z >> 4);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 49-245차: 표시 직전 확인 - 청크가 불러와져 있는데 그 자리에 보관함이 없으면 기록에서 빼고 false. */
	public static boolean stillThere(MinecraftClient client, Entry e) {
		if (e == null || client == null || client.world == null) {
			return false;
		}
		if (!loaded(client, e.x, e.z) || isStorageAt(client, new BlockPos(e.x, e.y, e.z), -1)) {
			return true;
		}
		try {
			if (world(client).remove(posKey(e.x, e.y, e.z)) != null) {
				dirty = true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** 이 월드에 기록된 상자 수(설정 화면 표시용). */
	public static int size(MinecraftClient client) {
		try {
			return world(client).size();
		} catch (Throwable ignored) {
			return 0;
		}
	}

	/** 이 월드 기록 전부 지우기. */
	public static void clear(MinecraftClient client) {
		try {
			world(client).clear();
			dirty = true;
			save();
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerIndex:clear", t);
		}
	}
}
