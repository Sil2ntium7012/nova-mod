package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 49-253차: 설계도(사용자: "라이트매티카 같은 걸 우리 쪽에서 편하게 따로").
 *
 * <p>설계도 한 장 = 직육면체 한 구역의 블록들 + 기준 위치. 파일은 config/lunaslight/blueprints/&lt;이름&gt;.json.
 * <ul>
 *   <li>palette: 구역 안에 나온 블록 상태 종류(상태 글자, 블록 id, 속성, 아이템 id, 보이는 이름, 모양 열쇠).</li>
 *   <li>data: 칸마다 palette 번호+1(0 = 공기)을 y → z → x 순서로, "번호*개수" 런 길이로 줄여 적는다.</li>
 *   <li>ref: 구역 왼쪽 아래 구석(최소 좌표)에서 본 기준 위치. 불러올 때 내가 서 있는 칸이 이 자리가 된다.</li>
 * </ul>
 *
 * <p><b>맞게 놓였나</b>({@link #check}): 블록 id가 다르면 틀림(빨강). 같으면 속성을 비교하는데, 놓는 방법과 상관없이 주변에 따라
 * 저절로 바뀌는 속성(물에 잠김, 신호, 잎 거리, 울타리/벽/판유리 연결 등)은 뺀다. 속성이 다르더라도 <b>생긴 모양(외곽 상자들)이 같고</b>
 * 꽉 찬 블록이 아니면 맞음으로 친다(ㄱ자 계단이 방향만 다르게 같은 모양이 되는 경우 등 - 사용자: "블록이 생긴 게 같으면 무조건 OK").
 * 꽉 찬 블록(유약 테라코타, 통나무 등)은 모양이 늘 같아서 속성이 다르면 방향 틀림(주황).
 */
public final class Blueprint {

	public static final int AIR = 0;
	public static final int MISSING = 1, OK = 2, WRONG = 3, ROTATED = 4, UNKNOWN = 0;
	/** 한 번에 저장할 수 있는 최대 칸 수(약 128×128×64). */
	public static final long MAX_VOLUME = 1_048_576L;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static final class Entry {
		public String state = "";
		public String id = "";
		public String props = "";
		public String item = "";
		public String name = "";
		public String shape = "";
		/** 비교용으로 정리한 속성(무시하는 속성 뺌). */
		public transient Map<String, String> cmp;
	}

	public String name = "";
	public long created;
	public int w, h, l;
	public int rx, ry, rz;
	public final List<Entry> palette = new ArrayList<>();
	/** 칸 값(0 = 공기, k = palette[k-1]), 인덱스 = (y * l + z) * w + x. */
	public int[] cells = new int[0];
	/** 공기가 아닌 칸 수. */
	public int solid;

	public int index(int x, int y, int z) {
		return (y * l + z) * w + x;
	}

	public Entry entry(int cell) {
		return cell <= 0 || cell > palette.size() ? null : palette.get(cell - 1);
	}

	// ==================== 파일 ====================

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("blueprints");
	}

	/** 파일 이름에 못 쓰는 글자를 뺀다. 비면 null. */
	public static String cleanName(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", " ");
		if (s.length() > 48) {
			s = s.substring(0, 48).trim();
		}
		return s.isEmpty() ? null : s;
	}

	/** 이름을 안 적었을 때: Nova-blueprint1, Nova-blueprint2 … 중 아직 없는 것. */
	public static String nextAutoName() {
		for (int i = 1; i < 100000; i++) {
			String n = "Nova-blueprint" + i;
			if (!Files.exists(folder().resolve(n + ".json"))) {
				return n;
			}
		}
		return "Nova-blueprint" + System.currentTimeMillis();
	}

	/** 저장된 설계도 이름들(최근 순). */
	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try {
			Path dir = folder();
			if (!Files.isDirectory(dir)) {
				return out;
			}
			List<Path> files = new ArrayList<>();
			try (var st = Files.list(dir)) {
				st.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(files::add);
			}
			files.sort((a, b) -> {
				try {
					return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
				} catch (Throwable t) {
					return 0;
				}
			});
			for (Path p : files) {
				String f = p.getFileName().toString();
				out.add(f.substring(0, f.length() - 5));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:list", t);
		}
		return out;
	}

	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(folder().resolve(name + ".json"));
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:delete", t);
			return false;
		}
	}

	public void save() throws Exception {
		Files.createDirectories(folder());
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.addProperty("name", name);
		root.addProperty("created", created);
		JsonArray size = new JsonArray();
		size.add(w);
		size.add(h);
		size.add(l);
		root.add("size", size);
		JsonArray ref = new JsonArray();
		ref.add(rx);
		ref.add(ry);
		ref.add(rz);
		root.add("ref", ref);
		JsonArray pal = new JsonArray();
		for (Entry e : palette) {
			JsonObject o = new JsonObject();
			o.addProperty("state", e.state);
			o.addProperty("id", e.id);
			o.addProperty("props", e.props);
			o.addProperty("item", e.item);
			o.addProperty("name", e.name);
			o.addProperty("shape", e.shape);
			pal.add(o);
		}
		root.add("palette", pal);
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < cells.length) {
			int v = cells[i];
			int j = i + 1;
			while (j < cells.length && cells[j] == v) {
				j++;
			}
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(v);
			if (j - i > 1) {
				sb.append('*').append(j - i);
			}
			i = j;
		}
		root.addProperty("data", sb.toString());
		try (BufferedWriter wtr = Files.newBufferedWriter(folder().resolve(name + ".json"), StandardCharsets.UTF_8)) {
			GSON.toJson(root, wtr);
		}
	}

	public static Blueprint load(String name) throws Exception {
		Path file = folder().resolve(name + ".json");
		JsonObject root;
		try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			root = new com.google.gson.JsonParser().parse(r).getAsJsonObject();
		}
		Blueprint b = new Blueprint();
		b.name = name;
		b.created = root.has("created") ? root.get("created").getAsLong() : 0L;
		JsonArray size = root.getAsJsonArray("size");
		b.w = size.get(0).getAsInt();
		b.h = size.get(1).getAsInt();
		b.l = size.get(2).getAsInt();
		JsonArray ref = root.getAsJsonArray("ref");
		b.rx = ref.get(0).getAsInt();
		b.ry = ref.get(1).getAsInt();
		b.rz = ref.get(2).getAsInt();
		for (var el : root.getAsJsonArray("palette")) {
			JsonObject o = el.getAsJsonObject();
			Entry e = new Entry();
			e.state = str(o, "state");
			e.id = str(o, "id");
			e.props = str(o, "props");
			e.item = str(o, "item");
			e.name = str(o, "name");
			e.shape = str(o, "shape");
			e.cmp = cmpProps(e.id, parseProps(e.props));
			b.palette.add(e);
		}
		long vol = (long) b.w * b.h * b.l;
		if (vol <= 0 || vol > MAX_VOLUME * 4) {
			throw new IllegalStateException("크기가 이상합니다");
		}
		b.cells = new int[(int) vol];
		String data = root.get("data").getAsString();
		int pos = 0;
		for (String part : data.split(",")) {
			if (part.isEmpty()) {
				continue;
			}
			int star = part.indexOf('*');
			int v = Integer.parseInt(star < 0 ? part : part.substring(0, star));
			int n = star < 0 ? 1 : Integer.parseInt(part.substring(star + 1));
			for (int k = 0; k < n && pos < b.cells.length; k++) {
				b.cells[pos++] = v;
			}
		}
		for (int c : b.cells) {
			if (c != AIR) {
				b.solid++;
			}
		}
		return b;
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	// ==================== 만들기(빌드) ====================

	/**
	 * a·b 두 꼭짓점이 만드는 구역의 블록을 읽어 설계도를 만든다. ref = 기준 위치(월드 좌표). 블록이 안 불러와진 칸은 공기로 읽힌다.
	 */
	public static Blueprint capture(MinecraftClient client, BlockPos a, BlockPos b, BlockPos refWorld, String name) {
		int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
		Blueprint bp = new Blueprint();
		bp.name = name;
		bp.created = System.currentTimeMillis();
		bp.w = x1 - x0 + 1;
		bp.h = y1 - y0 + 1;
		bp.l = z1 - z0 + 1;
		bp.rx = refWorld.getX() - x0;
		bp.ry = refWorld.getY() - y0;
		bp.rz = refWorld.getZ() - z0;
		bp.cells = new int[bp.w * bp.h * bp.l];
		Map<String, Integer> seen = new HashMap<>();
		for (int y = 0; y < bp.h; y++) {
			for (int z = 0; z < bp.l; z++) {
				for (int x = 0; x < bp.w; x++) {
					BlockPos p = new BlockPos(x0 + x, y0 + y, z0 + z);
					BlockState st = client.world.getBlockState(p);
					if (st == null || st.isAir()) {
						continue;
					}
					String key = st.toString();
					Integer idx = seen.get(key);
					if (idx == null) {
						Entry e = describe(client, st, p);
						if (e == null) {
							continue;
						}
						bp.palette.add(e);
						idx = bp.palette.size();
						seen.put(key, idx);
					}
					bp.cells[bp.index(x, y, z)] = idx;
					bp.solid++;
				}
			}
		}
		return bp;
	}

	/** 블록 상태 하나를 palette 항목으로. */
	private static Entry describe(MinecraftClient client, BlockState st, BlockPos p) {
		Entry e = new Entry();
		e.state = st.toString();
		e.id = idOf(e.state);
		e.props = propsOf(e.state);
		if (e.id.isEmpty() || isFluidId(e.id)) {
			return null;   // 물/용암은 설계도에 안 넣는다
		}
		try {
			Item item = st.getBlock().asItem();
			Object iid = item == null ? null : LunaCompat.getItemId(item);
			e.item = iid == null ? "" : iid.toString();
			if ("minecraft:air".equals(e.item)) {
				e.item = "";
			}
			e.name = item == null || e.item.isEmpty() ? e.id : new ItemStack(item).getName().getString();
		} catch (Throwable t) {
			e.name = e.id;
		}
		e.shape = shapeKey(client, st, p);
		e.cmp = cmpProps(e.id, parseProps(e.props));
		return e;
	}

	/** 외곽선 상자들을 글자로(같은 모양 비교용). */
	public static String shapeKey(MinecraftClient client, BlockState st, BlockPos p) {
		try {
			List<String> boxes = new ArrayList<>();
			for (Object box : st.getOutlineShape(client.world, p).getBoundingBoxes()) {
				String bs = String.valueOf(box);
				int br = bs.indexOf('[');
				boxes.add(br > 0 ? bs.substring(br) : bs);   // Box[ / AABB[ 앞머리는 버전마다 달라서 뗀다(설계도를 버전끼리 나눠 쓰게)
			}
			java.util.Collections.sort(boxes);
			return String.join(";", boxes);
		} catch (Throwable t) {
			return "";
		}
	}

	// ==================== 비교 ====================

	/** "Block{minecraft:oak_stairs}[facing=north,...]" → "minecraft:oak_stairs". */
	public static String idOf(String state) {
		int a = state.indexOf('{'), b = state.indexOf('}');
		return a >= 0 && b > a ? state.substring(a + 1, b) : state;
	}

	public static String propsOf(String state) {
		int a = state.indexOf('['), b = state.lastIndexOf(']');
		return a >= 0 && b > a ? state.substring(a + 1, b) : "";
	}

	public static Map<String, String> parseProps(String props) {
		Map<String, String> m = new LinkedHashMap<>();
		if (props == null || props.isEmpty()) {
			return m;
		}
		for (String kv : props.split(",")) {
			int eq = kv.indexOf('=');
			if (eq > 0) {
				m.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
			}
		}
		return m;
	}

	private static final Set<String> IGNORE = Set.of("waterlogged", "powered", "distance", "persistent", "triggered",
			"power", "moisture", "age", "stage", "note", "instrument", "occupied", "lit", "has_book", "has_record",
			"open", "enabled", "signal_fire", "snowy", "bloom", "shrieking", "can_summon", "honey_level");
	private static final Set<String> LINKS = Set.of("north", "south", "east", "west", "up", "down");

	private static boolean linksBlock(String id) {
		return id.contains("fence") || id.endsWith("_wall") || id.contains("pane") || id.endsWith("iron_bars")
				|| id.contains("redstone_wire") || id.contains("tripwire") || id.contains("chorus_plant")
				|| id.contains("mushroom_stem") || id.endsWith("mushroom_block") || id.endsWith("chain");
	}

	static Map<String, String> cmpProps(String id, Map<String, String> props) {
		Map<String, String> m = new LinkedHashMap<>(props);
		m.keySet().removeIf(IGNORE::contains);
		if (linksBlock(id)) {
			m.keySet().removeIf(LINKS::contains);
		}
		return m;
	}

	public static boolean isFluidId(String id) {
		return "minecraft:water".equals(id) || "minecraft:lava".equals(id) || "minecraft:bubble_column".equals(id);
	}

	private static final String FULL_A = "[0.0, 0.0, 0.0] -> [1.0, 1.0, 1.0]";

	/**
	 * 칸 하나의 상태: 공기/액체면 MISSING, id가 다르면 WRONG, 속성이 같거나(무시 속성 빼고) 모양이 같고 꽉 찬 블록이 아니면 OK,
	 * 아니면 ROTATED.
	 */
	public static int check(MinecraftClient client, Entry want, BlockState have, BlockPos p) {
		if (want == null) {
			return OK;
		}
		if (have == null || have.isAir()) {
			return MISSING;
		}
		String s = have.toString();
		String id = idOf(s);
		if (isFluidId(id)) {
			return MISSING;
		}
		if (!id.equals(want.id)) {
			return WRONG;
		}
		if (want.cmp == null) {
			want.cmp = cmpProps(want.id, parseProps(want.props));
		}
		Map<String, String> got = cmpProps(id, parseProps(propsOf(s)));
		if (got.equals(want.cmp)) {
			return OK;
		}
		String shape = shapeKey(client, have, p);
		if (!shape.isEmpty() && shape.equals(want.shape) && !isFullCube(shape)) {
			return OK;
		}
		return ROTATED;
	}

	private static final java.util.regex.Pattern NUM = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:E-?\\d+)?");

	/**
	 * 49-258차(사용자: "온전한 블록이 아닌 건 이상하게 나와"): 모양 열쇠 → 상자들 {x0,y0,z0,x1,y1,z1}(칸 안 0~1). 꽉 찬 블록이거나
	 * 읽을 수 없으면 null(호출부가 한 칸 통째로 그린다). 계단은 상자 2개, 반 블록은 1개 등.
	 */
	public static double[][] parseBoxes(String shape) {
		if (shape == null || shape.isEmpty() || isFullCube(shape)) {
			return null;
		}
		List<double[]> out = new ArrayList<>();
		for (String part : shape.split(";")) {
			java.util.regex.Matcher m = NUM.matcher(part);
			double[] b = new double[6];
			int n = 0;
			while (n < 6 && m.find()) {
				b[n++] = Double.parseDouble(m.group());
			}
			if (n < 6) {
				continue;
			}
			for (int i = 0; i < 6; i++) {
				b[i] = Math.max(-0.5, Math.min(1.5, b[i]));
			}
			if (b[3] - b[0] > 0.001 && b[4] - b[1] > 0.001 && b[5] - b[2] > 0.001) {
				out.add(b);
			}
		}
		return out.isEmpty() ? null : out.toArray(new double[0][]);
	}

	private static boolean isFullCube(String shape) {
		return shape.indexOf(';') < 0 && (shape.contains("[0.0, 0.0, 0.0] -> [1.0, 1.0, 1.0]") || FULL_A.equals(shape));
	}

	// ==================== 방향 안내 ====================

	private static final Map<String, String> DIR = Map.of("north", "북", "south", "남", "east", "동", "west", "서", "up", "위", "down", "아래");

	public static String dirName(String d) {
		return DIR.getOrDefault(d, d);
	}

	public static String opposite(String d) {
		switch (d) {
			case "north": return "south";
			case "south": return "north";
			case "east": return "west";
			case "west": return "east";
			case "up": return "down";
			case "down": return "up";
			default: return d;
		}
	}

	/**
	 * 이 블록을 맞는 방향으로 놓으려면 어떻게 해야 하는지 한두 줄. playerFacing = 내가 지금 보는 가로 방향(north/south/east/west).
	 * 모르는 블록은 필요한 속성만 보여 준다. 방향이 없는 블록이면 빈 목록.
	 */
	public static List<String> placeHint(Entry e, String playerFacing) {
		List<String> out = new ArrayList<>();
		if (e == null) {
			return out;
		}
		Map<String, String> p = parseProps(e.props);
		String facing = p.get("facing");
		String axis = p.get("axis");
		String half = p.get("half");
		String type = p.get("type");
		if (facing != null) {
			String need;
			if (e.id.contains("glazed_terracotta")) {
				need = opposite(facing);   // 유약 테라코타: 내가 보는 방향의 반대쪽을 향해 놓인다
			} else if (e.id.contains("stairs") || e.id.contains("door") || e.id.contains("trapdoor") || e.id.contains("fence_gate")
					|| e.id.contains("bed") || e.id.contains("repeater") || e.id.contains("comparator")) {
				need = facing;
			} else if (e.id.contains("observer") || e.id.contains("piston") || e.id.contains("dispenser") || e.id.contains("dropper")) {
				need = opposite(facing);   // 앞면이 나를 보게 놓인다
			} else if (e.id.contains("furnace") || e.id.contains("smoker") || e.id.contains("chest") || e.id.contains("barrel")
					|| e.id.contains("pumpkin") || e.id.contains("loom") || e.id.contains("stonecutter") || e.id.contains("lectern")
					|| e.id.contains("beehive") || e.id.contains("bee_nest") || e.id.contains("anvil")) {
				need = opposite(facing);
			} else {
				need = null;
			}
			if (need != null && !"up".equals(need) && !"down".equals(need)) {
				boolean ok = need.equals(playerFacing);
				out.add("방향 " + dirName(facing) + "  |  " + dirName(need) + "쪽을 보고 놓기" + (ok ? " §a(지금 맞음)" : " §c(지금 " + dirName(playerFacing) + ")"));
			} else {
				out.add("방향 " + dirName(facing) + (facing.equals("up") || facing.equals("down") ? "  |  위아래를 보고 놓기" : ""));
			}
		}
		if (axis != null) {
			String how = "y".equals(axis) ? "바닥이나 천장에 붙여 놓기" : "x".equals(axis) ? "동쪽이나 서쪽 면에 붙여 놓기" : "남쪽이나 북쪽 면에 붙여 놓기";
			out.add("축 " + axis.toUpperCase() + "  |  " + how);
		}
		if (half != null && ("top".equals(half) || "bottom".equals(half))) {
			out.add("top".equals(half) ? "위쪽  |  블록 윗부분이나 천장을 보고 놓기" : "아래쪽  |  블록 아랫부분이나 바닥을 보고 놓기");
		}
		if (type != null && (e.id.endsWith("_slab"))) {
			out.add("top".equals(type) ? "위 반 블록  |  윗부분을 보고 놓기" : "bottom".equals(type) ? "아래 반 블록" : "두 겹(반 블록 두 개)");
		}
		return out;
	}
}
