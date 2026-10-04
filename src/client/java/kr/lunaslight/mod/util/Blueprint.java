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

	// ==================== 49-280차: 돌리기 / 뒤집기 ====================
	// 사용자: "설계도 불러온 거 여러 방향으로 뒤집거나 각도를 바꾸거나 하는 기능". 칸 배열과 기준 위치를 옮기고, 블록 상태(방향,
	// 축, 회전, 위아래, 계단 모양, 문 경첩, 상자 좌우, 레일, 울타리 연결 …)와 모양 상자도 같이 바꾼다 - 그래야 맞음/틀림 판정과
	// 진짜 블록 홀로그램이 돌린 모양 그대로 나온다. 위에서 볼 때 R = 오른쪽(시계 방향) 90도, L = 왼쪽 90도, X = 좌우(동서) 뒤집기,
	// Z = 앞뒤(남북) 뒤집기, Y = 위아래 뒤집기.

	public static final char ROT_CW = 'R', ROT_CCW = 'L', MIRROR_X = 'X', MIRROR_Z = 'Z', FLIP_Y = 'Y';

	/** 돌리거나 뒤집은 새 설계도(이름, 만든 때는 그대로). 기준 위치(rx, ry, rz)도 같이 옮긴다. */
	public Blueprint transformed(char op) {
		Blueprint b = new Blueprint();
		b.name = name;
		b.created = created;
		boolean rot = op == ROT_CW || op == ROT_CCW;
		b.w = rot ? l : w;
		b.h = h;
		b.l = rot ? w : l;
		b.cells = new int[cells.length];
		for (int y = 0; y < h; y++) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					int c = cells[index(x, y, z)];
					if (c != AIR) {
						int[] p = mapPos(op, x, y, z);
						b.cells[b.index(p[0], p[1], p[2])] = c;
					}
				}
			}
		}
		int[] r = mapPos(op, rx, ry, rz);
		b.rx = r[0];
		b.ry = r[1];
		b.rz = r[2];
		b.solid = solid;
		for (Entry e : palette) {
			b.palette.add(transformEntry(e, op));
		}
		return b;
	}

	private int[] mapPos(char op, int x, int y, int z) {
		return switch (op) {
			case ROT_CW -> new int[]{l - 1 - z, y, x};
			case ROT_CCW -> new int[]{z, y, w - 1 - x};
			case MIRROR_X -> new int[]{w - 1 - x, y, z};
			case MIRROR_Z -> new int[]{x, y, l - 1 - z};
			case FLIP_Y -> new int[]{x, h - 1 - y, z};
			default -> new int[]{x, y, z};
		};
	}

	private static final String[] HORIZ = {"north", "east", "south", "west"};

	/** 방향 이름 하나를 op대로. 방향이 아니면 그대로. */
	public static String dirOp(char op, String d) {
		if (d == null) {
			return null;
		}
		int i = java.util.Arrays.asList(HORIZ).indexOf(d);
		switch (op) {
			case ROT_CW:
				return i >= 0 ? HORIZ[(i + 1) & 3] : d;
			case ROT_CCW:
				return i >= 0 ? HORIZ[(i + 3) & 3] : d;
			case MIRROR_X:
				return "east".equals(d) ? "west" : "west".equals(d) ? "east" : d;
			case MIRROR_Z:
				return "north".equals(d) ? "south" : "south".equals(d) ? "north" : d;
			case FLIP_Y:
				return "up".equals(d) ? "down" : "down".equals(d) ? "up" : d;
			default:
				return d;
		}
	}

	private static String swap(String v, String a, String b) {
		return a.equals(v) ? b : b.equals(v) ? a : v;
	}

	private static Entry transformEntry(Entry e, char op) {
		Entry n = new Entry();
		n.id = e.id;
		n.item = e.item;
		n.name = e.name;
		boolean rot = op == ROT_CW || op == ROT_CCW;
		boolean mirror = op == MIRROR_X || op == MIRROR_Z;
		Map<String, String> q = new LinkedHashMap<>();
		for (Map.Entry<String, String> kv : parseProps(e.props).entrySet()) {
			String k = kv.getKey(), v = kv.getValue(), nk = k, nv = v;
			if (LINKS.contains(k)) {
				nk = dirOp(op, k);   // 울타리, 벽, 판유리, 레드스톤 가루, 덩굴, 버섯 블록의 연결 방향
			}
			switch (k) {
				case "facing", "vertical_direction" -> nv = dirOp(op, v);
				case "axis" -> nv = rot ? swap(v, "x", "z") : v;
				case "rotation" -> {
					try {
						int r = Integer.parseInt(v);
						r = switch (op) {
							case ROT_CW -> r + 4;
							case ROT_CCW -> r + 12;
							case MIRROR_X -> 16 - r;
							case MIRROR_Z -> 24 - r;
							default -> r;
						};
						nv = String.valueOf(((r % 16) + 16) % 16);
					} catch (NumberFormatException ignored) {
					}
				}
				case "half" -> nv = op == FLIP_Y ? swap(swap(v, "top", "bottom"), "upper", "lower") : v;
				case "type" -> nv = op == FLIP_Y ? swap(v, "top", "bottom") : mirror ? swap(v, "left", "right") : v;
				case "hinge" -> nv = mirror ? swap(v, "left", "right") : v;
				case "face", "attachment" -> nv = op == FLIP_Y ? swap(v, "floor", "ceiling") : v;
				case "orientation" -> {
					String[] parts = v.split("_");
					for (int i = 0; i < parts.length; i++) {
						parts[i] = dirOp(op, parts[i]);
					}
					nv = String.join("_", parts);
				}
				case "shape" -> nv = shapeOp(op, v, mirror);
				default -> {
				}
			}
			q.put(nk, nv);
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> kv : q.entrySet()) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(kv.getKey()).append('=').append(kv.getValue());
		}
		n.props = sb.toString();
		n.state = "Block{" + n.id + "}" + (n.props.isEmpty() ? "" : "[" + n.props + "]");
		n.shape = shapeBoxesOp(e.shape, op);
		return n;
	}

	/** 계단 모양(왼쪽/오른쪽)과 레일 모양(방향 둘). */
	private static String shapeOp(char op, String v, boolean mirror) {
		if (v.startsWith("inner_") || v.startsWith("outer_")) {
			return mirror ? swap(swap(v, "inner_left", "inner_right"), "outer_left", "outer_right") : v;
		}
		if (v.startsWith("ascending_")) {
			return "ascending_" + dirOp(op, v.substring("ascending_".length()));
		}
		String[] p = v.split("_");
		if (p.length != 2 || java.util.Arrays.asList(HORIZ).indexOf(p[0]) < 0 || java.util.Arrays.asList(HORIZ).indexOf(p[1]) < 0) {
			return v;
		}
		String a = dirOp(op, p[0]), b = dirOp(op, p[1]);
		java.util.Set<String> s = new java.util.HashSet<>(java.util.Arrays.asList(a, b));
		if (s.contains("north") && s.contains("south")) {
			return "north_south";
		}
		if (s.contains("east") && s.contains("west")) {
			return "east_west";
		}
		String ns = s.contains("north") ? "north" : "south", ew = s.contains("east") ? "east" : "west";
		return ns + "_" + ew;
	}

	/** 모양 상자 열쇠(칸 안 0~1)를 op대로. 형식은 shapeKey와 같게("[x, y, z] -> [X, Y, Z]"를 정렬해 ;로). */
	private static String shapeBoxesOp(String shape, char op) {
		if (shape == null || shape.isEmpty()) {
			return shape == null ? "" : shape;
		}
		List<String> out = new ArrayList<>();
		for (String part : shape.split(";")) {
			java.util.regex.Matcher m = NUM.matcher(part);
			double[] b = new double[6];
			int k = 0;
			while (k < 6 && m.find()) {
				b[k++] = Double.parseDouble(m.group());
			}
			if (k < 6) {
				return shape;   // 못 읽는 모양 - 그대로 둔다
			}
			double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
			double[] r = switch (op) {
				case ROT_CW -> new double[]{1 - z1, y0, x0, 1 - z0, y1, x1};
				case ROT_CCW -> new double[]{z0, y0, 1 - x1, z1, y1, 1 - x0};
				case MIRROR_X -> new double[]{1 - x1, y0, z0, 1 - x0, y1, z1};
				case MIRROR_Z -> new double[]{x0, y0, 1 - z1, x1, y1, 1 - z0};
				case FLIP_Y -> new double[]{x0, 1 - y1, z0, x1, 1 - y0, z1};
				default -> b;
			};
			for (int i = 0; i < 6; i++) {
				r[i] = Math.round(r[i] * 1e6) / 1e6 + 0.0;   // 0.30000000000000004 같은 꼬리와 -0.0 정리
			}
			out.add("[" + r[0] + ", " + r[1] + ", " + r[2] + "] -> [" + r[3] + ", " + r[4] + ", " + r[5] + "]");
		}
		java.util.Collections.sort(out);
		return String.join(";", out);
	}

	/**
	 * 돌리기/뒤집기 기록을 가장 짧게: 결과가 같은 것끼리 합친다. 위아래(Y)는 따로, 가로는 "좌우 뒤집기 한 번(X) 다음 오른쪽 90도 k번"
	 * 꼴로(앞뒤 뒤집기 Z = X 다음 R 두 번). 다시 불러올 때 이 순서대로 다시 적용한다.
	 */
	public static String composeOps(String ops) {
		boolean f = false, mx = false;
		int k = 0;
		for (char c : ops.toCharArray()) {
			switch (c) {
				case ROT_CW -> k = (k + 1) & 3;
				case ROT_CCW -> k = (k + 3) & 3;
				case MIRROR_X -> {
					mx = !mx;
					k = (4 - k) & 3;
				}
				case MIRROR_Z -> {
					mx = !mx;
					k = (6 - k) & 3;
				}
				case FLIP_Y -> f = !f;
				default -> {
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		if (f) {
			sb.append(FLIP_Y);
		}
		if (mx) {
			sb.append(MIRROR_X);
		}
		for (int i = 0; i < k; i++) {
			sb.append(ROT_CW);
		}
		return sb.toString();
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
