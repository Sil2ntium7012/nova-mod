package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.gui.LunaDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-538차(사용자: "왜 안바뀌는데", "던전 핫바가 적용이 안되냐고"): <b>너굴 던전 UI</b>.
 *
 * <p>너굴마을은 아래 HUD(체력, 배고픔, 방어구, 레벨, 스킬 카드와 쿨타임, 경험치 얻음)를 보스바 이름 한 줄에 ng:hud 글꼴 글자로 그린다
 * (NeogulHudProbe로 실측). 서버 팩의 ng/font/hud.json에서 글자 → 그림 파일 이름을 읽어 뜻을 풀고(hp_27, txt_digits, armor_12,
 * lv_digits, skill_cards_*, skill_keycaps, skill_cd_digits, hud_gain_* …), 켜져 있으면 보스바 이름을 왼쪽 위 상자(돈, 캐시, 지역)까지만
 * 남기고 자른다. 대신 화면 아래에 LF식 핫바(가운데 하트, 양쪽 칸, 금빛 곡선), 하트 테두리 갑옷, 핫바 위 양쪽 대칭 스킬 카드(서버 그림 그대로 +
 * 아래 납작한 키 버튼 + 도는 쿨타임), 맨 아래 얇은 경험치 바를 그린다.
 *
 * <p>켜는 때: 던전에 <b>들어오는 순간</b>에만(싸우는 중에 화면이 바뀌면 위험 - 사용자 요청). 끄면 바로 원래 서버 HUD.
 */
public final class NeogulDungeonHud {
	private NeogulDungeonHud() {
	}

	/** 기능(모듈) - 꺼져 있어도 틱을 돌려 원래 HUD로 되돌려야 해서 따로 묻는다. */
	public static volatile java.util.function.BooleanSupplier enabled = () -> false;
	private static boolean registered;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(NeogulDungeonHud::blockSwap);
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				wanted = enabled.getAsBoolean();
				tick();
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:init", t);
		}
	}

	/**
	 * 49-540차(사용자: "양손 들기는 Shift + F로 메뉴 열 때 아니면 왼손 무기는 F 안 되게", 던전에서만): 던전에 있는 동안 손 바꾸기 키(F)의 눌림을
	 * 매 틱 처음에 비운다. Shift를 누르고 있으면(너굴 메뉴) 그대로 둔다. 던전 UI를 켰든 껐든 적용. 스킬 키 처리(NG-SkillKeydown)는 키 이벤트에서
	 * 먼저 하므로 영향 없음.
	 */
	private static void blockSwap(Minecraft mc) {
		try {
			if (mc == null || mc.player == null || mc.options == null || !inDungeon() || !NeogulData.onNeogul(mc)) {
				return;
			}
			if ((LunaCompat.heldModifiersAny(mc) & LunaCompat.MOD_SHIFT) != 0) {
				return;
			}
			while (mc.options.keySwapOffhand.consumeClick()) {
				// 비움
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:swap", t);
		}
	}

	// ==================== 상태 ====================

	/** 기능 켬(모듈이 매 틱 넣는다). */
	public static volatile boolean wanted;
	/** 지금 노바 던전 UI를 그리는 중(바닐라 핫바, 체력, 경험치 숨김). */
	public static volatile boolean active;
	/** 마지막으로 읽은 지역(dungeon, lobby, island …). */
	public static volatile String location;
	private static long lastSeen;
	private static volatile BossEvent hudEv;
	private static volatile Map<?, ?> lastEvents;
	private static boolean wasDungeon;

	public static final class Skill {
		public String sheet;   // skill_cards_a_blank …
		public int col;        // 그 그림의 몇 번째 칸
		public int keyCol = -1;
		public int cd = -1;    // 남은 초(없으면 준비됨)
		int digits;
	}

	public static final List<Skill> skills = new ArrayList<>();
	public static int hp = -1, hpMax = -1, hunger = -1, armor = -1, level = -1;
	public static int pct = -1;
	public static String gain;
	public static long gainAt;

	// 쿨타임 매끄럽게: 카드(그림+칸 묶음)별 시작 시각과 전체 초
	private static final Map<String, long[]> CD = new HashMap<>();

	// 보스바: 서버가 보낸 원래 이름 / 우리가 넣은 자른 이름
	private static final Map<BossEvent, Component> RAW = new IdentityHashMap<>();
	private static final Map<BossEvent, Component> MINE = new IdentityHashMap<>();

	// ==================== hud.json 글자표 ====================

	private record Glyph(String file, String path, int col, int cols) {
	}

	private static Map<Integer, Glyph> glyphs;
	private static long glyphsAt;
	private static final Map<String, int[]> TEX_SIZE = new HashMap<>();
	private static final Map<String, int[]> CELL_BOX = new HashMap<>();

	private static Map<Integer, Glyph> glyphs() {
		long now = System.currentTimeMillis();
		if (glyphs != null && (!glyphs.isEmpty() || now - glyphsAt < 5000)) {
			return glyphs;
		}
		glyphsAt = now;
		Map<Integer, Glyph> m = new HashMap<>();
		try {
			var rm = Minecraft.getInstance().getResourceManager();
			var res = rm.getResource(Identifier.fromNamespaceAndPath("ng", "font/hud.json"));
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					for (JsonElement e : root.getAsJsonArray("providers")) {
						JsonObject p = e.getAsJsonObject();
						if (!"bitmap".equals(p.has("type") ? p.get("type").getAsString() : "") || !p.has("file")) {
							continue;
						}
						String file = p.get("file").getAsString();
						String base = file.substring(file.lastIndexOf('/') + 1).replace(".png", "");
						JsonArray rows = p.getAsJsonArray("chars");
						String row0 = rows.get(0).getAsString();
						int cols = row0.codePointCount(0, row0.length());
						for (JsonElement r : rows) {
							String s = r.getAsString();
							int c = 0;
							for (int i = 0; i < s.length(); ) {
								int cp = s.codePointAt(i);
								if (cp != 0) {
									m.putIfAbsent(cp, new Glyph(base, file, c, cols));
								}
								c++;
								i += Character.charCount(cp);
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:json", t);
		}
		glyphs = m;
		return m;
	}

	/** 서버 팩 그림의 텍스처 id(ng:font/hud/x.png → ng:textures/font/hud/x.png). */
	private static Identifier texOf(String file) {
		int c = file.indexOf(':');
		String ns = c < 0 ? "minecraft" : file.substring(0, c);
		String p = c < 0 ? file : file.substring(c + 1);
		return Identifier.fromNamespaceAndPath(ns, "textures/" + p);
	}

	/** 그림의 칸마다 실제로 그려진(불투명) 네모 [x, y, w, h]. 그림 하나를 한 번만 읽어 모든 칸을 같이 구해 둔다. */
	private static int[] cellBox(String file, int cols, int col) {
		int[] box = CELL_BOX.get(file + "#" + col);
		if (box != null) {
			return box;
		}
		loadSheet(file, cols);
		box = CELL_BOX.get(file + "#" + col);
		if (box == null) {
			int[] size = texSize(file);
			int cw = Math.max(1, size[0] / Math.max(1, cols));
			box = new int[]{col * cw, 0, cw, size[1]};
			CELL_BOX.put(file + "#" + col, box);
		}
		return box;
	}

	private static void loadSheet(String file, int cols) {
		if (TEX_SIZE.containsKey(file) && CELL_BOX.containsKey(file + "#0")) {
			return;
		}
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(texOf(file));
			if (res.isEmpty()) {
				TEX_SIZE.put(file, new int[]{16, 16});
				return;
			}
			try (InputStream in = res.get().open(); var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
				int w = img.getWidth(), h = img.getHeight();
				TEX_SIZE.put(file, new int[]{w, h});
				int n = Math.max(1, cols);
				int cw = Math.max(1, w / n);
				for (int col = 0; col < n; col++) {
					int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
					for (int y = 0; y < h; y++) {
						for (int x = col * cw; x < Math.min(col * cw + cw, w); x++) {
							if (((img.getPixel(x, y) >>> 24) & 0xFF) > 60) {
								x0 = Math.min(x0, x);
								y0 = Math.min(y0, y);
								x1 = Math.max(x1, x);
								y1 = Math.max(y1, y);
							}
						}
					}
					CELL_BOX.put(file + "#" + col, x1 >= 0 ? new int[]{x0, y0, x1 - x0 + 1, y1 - y0 + 1} : new int[]{col * cw, 0, cw, h});
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:sheet", t);
			TEX_SIZE.putIfAbsent(file, new int[]{16, 16});
		}
	}

	private static int[] texSize(String file) {
		int[] s = TEX_SIZE.get(file);
		if (s != null) {
			return s;
		}
		loadSheet(file, 1);
		s = TEX_SIZE.get(file);
		return s != null ? s : new int[]{16, 16};
	}

	/**
	 * 49-541차(사용자: "던전 갔다고 리팩이 껴지는 게 아니라 기존에 껴져 있고 그걸 불러오는 식으로"): 던전에 들어가는 순간 그림을 읽지 않게
	 * 너굴마을에 처음 들어왔을 때(로비) 미리 다 읽어 둔다 - 서버 팩의 카드, 키, 아이콘 칸 크기와 노바 던전 그림(텍스처 등록까지).
	 */
	private static boolean warmed;

	private static void warm() {
		if (warmed) {
			return;
		}
		warmed = true;
		try {
			Map<String, Integer> sheets = new HashMap<>();
			for (Glyph g : glyphs().values()) {
				if (g.file.startsWith("skill_card") || g.file.equals("skill_keycaps") || g.file.equals("icon_hunger")) {
					sheets.put(g.path, g.cols);
				}
			}
			for (var e : sheets.entrySet()) {
				loadSheet(e.getKey(), e.getValue());
			}
			var tm = Minecraft.getInstance().getTextureManager();
			for (var e : sheets.keySet()) {
				tm.getTexture(texOf(e));
			}
			tm.getTexture(HOTBAR);
			tm.getTexture(SEL);
			for (Identifier id : BACK) {
				tm.getTexture(id);
			}
			for (Identifier id : HEART) {
				tm.getTexture(id);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:warm", t);
		}
	}

	private static String sheetPath(String sheet) {
		for (Glyph g : glyphs().values()) {
			if (g.file.equals(sheet)) {
				return g.path;
			}
		}
		return "ng:font/hud/" + sheet + ".png";
	}

	private static int sheetCols(String sheet) {
		for (Glyph g : glyphs().values()) {
			if (g.file.equals(sheet)) {
				return g.cols;
			}
		}
		return 1;
	}

	// ==================== 보스바 ====================

	private static final Pattern HP_N = Pattern.compile("^hp_(\\d+)$");
	private static final Pattern HUNGER_N = Pattern.compile("^hunger_(\\d+)$");
	private static final Pattern ARMOR_N = Pattern.compile("^armor_(\\d+)$");

	/** BossHealthOverlay.update 끝(NeogulBossHudMixin). */
	public static void onBossUpdate(Map<?, ?> events) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || !NeogulData.onNeogul(mc)) {
				return;
			}
			RAW.keySet().removeIf(ev -> !events.containsValue(ev));
			MINE.keySet().removeIf(ev -> !events.containsValue(ev));
			for (Object o : events.values()) {
				if (!(o instanceof BossEvent ev)) {
					continue;
				}
				Component name = ev.getName();
				if (name != MINE.get(ev)) {
					RAW.put(ev, name);   // 서버가 새 이름을 보냄
					MINE.remove(ev);
				}
				Component raw = RAW.get(ev);
				int cut = parse(raw);
				if (cut < 0) {
					continue;   // 너굴 HUD 줄이 아님
				}
				lastSeen = System.currentTimeMillis();
				warm();
				hudEv = ev;
				lastEvents = events;
				boolean dungeon = "dungeon".equals(location);
				if (dungeon && !wasDungeon) {
					active = wanted;   // 들어오는 순간에만 켠다
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 던전 UI: 던전 들어옴 - {}", active ? "켬" : "꺼져 있어 서버 화면 그대로");
				}
				if (!dungeon && wasDungeon) {
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 던전 UI: 던전 나감({})", location);
				}
				if (!dungeon) {
					active = false;
				}
				wasDungeon = dungeon;
				if (active) {
					Component mine = MINE.get(ev);
					if (mine == null) {
						mine = cutAt(raw, cut);
						MINE.put(ev, mine);
					}
					ev.setName(mine);
				} else if (MINE.remove(ev) != null) {
					ev.setName(raw);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:boss", t);
		}
	}

	// ==================== 49-553차: 토벌 레벨(/레벨 창) ====================
	// 사용자: "본인 레벨이 적용이 제대로 안 되고 있고, /레벨 하면 본인 레벨 던전 보이는데 토벌 꺼 그거야". 서버 HUD의 Lv는 토벌 레벨이 아니다 -
	// /레벨 창(제목에 "레벨")이 열려 있는 동안 0.5초마다 칸을 보고, 이름에 "토벌"이 든 아이템의 이름+설명에서 레벨 숫자를 읽어 기억한다
	// (config/lunaslight/neogul-dungeon.json). 처음 열 때 창 아이템 글을 latest.log에 한 번 적는다(모양이 바뀌면 고치려고).

	public static volatile int tobeolLv = -1;
	/** 49-566차: /레벨 창 토벌 아이템 설명의 "경험치: 5,037 / 25,080 (20.1%)"(마지막으로 본 값). 모르면 -1. */
	public static volatile long tobeolExp = -1, tobeolNeed = -1;
	public static volatile float tobeolPct = -1f;
	private static final Pattern EXP_NUM = Pattern.compile("경험치\\s*[:：]?\\s*([\\d,]+)\\s*/\\s*([\\d,]+)(?:\\s*\\(\\s*([\\d.]+)\\s*%\\s*\\))?");
	private static boolean lvLoaded;
	private static Object lvScreen;
	private static int lvTick;
	private static final Pattern LV_NUM = Pattern.compile("(?i)(?:lv\\.?|레벨)\\s*[:.]?\\s*(\\d{1,3})|(\\d{1,3})\\s*레벨");

	private static java.nio.file.Path lvFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-dungeon.json");
	}

	private static void loadLv() {
		if (lvLoaded) {
			return;
		}
		lvLoaded = true;
		try {
			java.nio.file.Path p = lvFile();
			if (java.nio.file.Files.exists(p)) {
				JsonObject o = JsonParser.parseString(java.nio.file.Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
				if (o.has("tobeol")) {
					tobeolLv = o.get("tobeol").getAsInt();
				}
				if (o.has("exp")) {
					tobeolExp = o.get("exp").getAsLong();
					tobeolNeed = o.get("need").getAsLong();
					tobeolPct = o.get("pct").getAsFloat();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:lvload", t);
		}
	}

	private static void saveLv() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("tobeol", tobeolLv);
			if (tobeolNeed > 0) {
				o.addProperty("exp", tobeolExp);
				o.addProperty("need", tobeolNeed);
				o.addProperty("pct", tobeolPct);
			}
			java.nio.file.Path p = lvFile();
			java.nio.file.Files.createDirectories(p.getParent());
			java.nio.file.Files.writeString(p, o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:lvsave", t);
		}
	}

	private static String plainText(Component c) {
		return c == null ? "" : c.getString().replaceAll("§.", "").replaceAll("[\\uE000-\\uF8FF]", " ").replaceAll("\\s+", " ").trim();
	}

	private static void scanLevelMenu(Minecraft mc) {
		Object screen = LunaCompat.screenOf(mc);
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)
				|| screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
				|| !NeogulData.onNeogul(mc)) {   // 49-566차: /레벨 창 제목은 그림 글자(EXP)라 "레벨"이 없다 - 제목 대신 칸의 "토벌 레벨" 아이템으로 찾는다
			lvScreen = null;
			return;
		}
		boolean first = screen != lvScreen;
		if (first) {
			lvScreen = screen;
			lvTick = 0;
		}
		if (++lvTick % 10 != 3) {
			return;   // 칸이 들어온 뒤 0.5초마다
		}
		var slots = cs.getMenu().slots;
		int n = Math.max(0, slots.size() - 36);
		StringBuilder dump = null;
		for (int i = 0; i < n; i++) {
			ItemStack st = slots.get(i).getItem();
			if (st.isEmpty()) {
				continue;
			}
			StringBuilder all = new StringBuilder(plainText(st.getHoverName()));
			var lore = st.get(net.minecraft.core.component.DataComponents.LORE);
			if (lore != null) {
				for (Component l : lore.lines()) {
					all.append(" | ").append(plainText(l));
				}
			}
			String text = all.toString();
			String name = plainText(st.getHoverName());
			if (name.contains("토벌") && name.contains("레벨")) {
				if (lvTick == 3) {
					dump = new StringBuilder("\n  [").append(i).append("] ").append(text);
				}
				boolean changed = false;
				Matcher m = LV_NUM.matcher(name);
				if (!m.find()) {
					m = LV_NUM.matcher(text);
					m = m.find() ? m : null;
				}
				if (m != null) {
					int v = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
					if (v >= 1 && v <= NeogulLevels.MAX_LV && v != tobeolLv) {
						tobeolLv = v;
						changed = true;
					}
				}
				Matcher e = EXP_NUM.matcher(text);
				if (e.find()) {
					long cur = Long.parseLong(e.group(1).replace(",", ""));
					long need = Long.parseLong(e.group(2).replace(",", ""));
					float pct = e.group(3) != null ? Float.parseFloat(e.group(3)) : (need > 0 ? cur * 100f / need : 0f);
					if (cur != tobeolExp || need != tobeolNeed) {
						tobeolExp = cur;
						tobeolNeed = need;
						tobeolPct = pct;
						changed = true;
					}
				}
				if (changed) {
					saveLv();
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 던전 UI: 토벌 레벨 {} 경험치 {} / {} (/레벨 창)", tobeolLv, tobeolExp, tobeolNeed);
				}
			}
		}
		if (dump != null) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 던전 UI: /레벨 창 내용{}", dump);
		}
	}

	/** 매 틱: 기능을 껐거나 HUD 줄이 사라졌으면 원래대로. */
	public static void tick() {
		loadLv();
		try {
			scanLevelMenu(Minecraft.getInstance());
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonHud:lvscan", t);
		}
		// 서버가 HUD 줄을 한동안 안 바꿔도(가만히 있을 때) 보스바가 화면에 남아 있으면 계속 본 것으로
		try {
			Map<?, ?> ev = lastEvents;
			if (ev != null && hudEv != null && ev.containsValue(hudEv)) {
				lastSeen = System.currentTimeMillis();
			}
		} catch (Throwable ignored) {
		}
		if (active && (!wanted || System.currentTimeMillis() - lastSeen > 4000)) {
			active = false;
		}
		if (System.currentTimeMillis() - lastSeen > 4000) {
			wasDungeon = false;
			location = null;
		}
		if (!active && !MINE.isEmpty()) {
			for (var e : MINE.entrySet()) {
				Component raw = RAW.get(e.getKey());
				if (raw != null) {
					e.getKey().setName(raw);
				}
			}
			MINE.clear();
		}
	}

	public static boolean inDungeon() {
		return "dungeon".equals(location) && System.currentTimeMillis() - lastSeen < 4000;
	}

	/** 너굴 HUD 줄이면 지역 글자 다음(자를 자리, 글자 수)을 돌려주고 값들을 채운다. 아니면 -1. */
	private static int parse(Component c) {
		Map<Integer, Glyph> map = glyphs();
		if (map.isEmpty() || c == null) {
			return -1;
		}
		StringBuilder all = new StringBuilder();
		List<Boolean> hud = new ArrayList<>();
		c.visit((FormattedText.StyledContentConsumer<Object>) (style, s) -> {
			Object f = style.getFont();
			boolean h = f != null && f.toString().contains("ng:hud");
			for (int i = 0; i < s.length(); i++) {
				hud.add(h);
			}
			all.append(s);
			return Optional.empty();
		}, Style.EMPTY);
		String s = all.toString();
		int cut = -1;
		String loc = null;
		List<Skill> sk = new ArrayList<>();
		Skill cur = null;
		int hpA = -1, hpB = -1, hun = -1, arm = -1, lv = -1, pc = -1;
		boolean slash = false, hpDone = false;
		StringBuilder gn = null;
		boolean inPct = false;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			int n = Character.charCount(cp);
			Glyph g = hud.get(i) ? map.get(cp) : null;
			i += n;
			if (g == null) {
				continue;
			}
			String f = g.file;
			Matcher m;
			if (f.startsWith("hud_channel_") && !f.equals("hud_channel_label")) {
				loc = f.substring("hud_channel_".length());
				// 지역 글자 뒤 띄어쓰기 글자들까지 남긴다
				int j = i;
				while (j < s.length() && hud.get(j) && !map.containsKey(s.codePointAt(j))) {
					j += Character.charCount(s.codePointAt(j));
				}
				cut = j;
			} else if (f.startsWith("skill_cards_") || f.startsWith("skill_card_")) {
				cur = new Skill();
				cur.sheet = f;
				cur.col = g.col;
				sk.add(cur);
			} else if (f.equals("skill_keycaps")) {
				if (cur != null) {
					cur.keyCol = g.col;
				}
			} else if (f.startsWith("skill_cd_digits")) {
				if (cur != null) {
					cur.digits = cur.digits * 10 + g.col;
					cur.cd = cur.digits;
				}
			} else if ((m = HP_N.matcher(f)).find()) {
				cur = null;
			} else if (f.equals("txt_digits") && !hpDone) {
				if (!slash) {
					hpA = Math.max(0, hpA) * 10 + g.col;
				} else {
					hpB = Math.max(0, hpB) * 10 + g.col;
				}
			} else if (f.equals("txt_slash")) {
				slash = true;
			} else if ((m = HUNGER_N.matcher(f)).find()) {
				hpDone = true;
				hun = Integer.parseInt(m.group(1));
			} else if ((m = ARMOR_N.matcher(f)).find()) {
				hpDone = true;
				arm = Integer.parseInt(m.group(1));
			} else if (f.equals("lv_digits")) {
				lv = Math.max(0, lv) * 10 + g.col;
			} else if (f.equals("hud_gain_plus")) {
				gn = new StringBuilder("+");
			} else if (f.equals("hud_gain_digits") && gn != null && !inPct) {
				gn.append(g.col);
			} else if (f.equals("hud_gain_dot") && gn != null && !inPct) {
				gn.append('.');
			} else if (f.equals("hud_gain_paren")) {
				inPct = true;
			} else if (f.equals("hud_gain_digits_g") && inPct) {
				pc = Math.max(0, pc) * 10 + g.col;
			} else if (f.equals("hud_gain_pct")) {
				inPct = false;
			}
		}
		if (cut < 0) {
			return -1;
		}
		location = loc;
		synchronized (skills) {
			skills.clear();
			skills.addAll(sk);
		}
		long now = System.currentTimeMillis();
		for (Skill k : sk) {
			String key = k.sheet + "#" + readyCol(k);
			if (k.cd > 0) {
				long[] st = CD.get(key);
				if (st == null || k.cd > st[1]) {
					st = new long[]{now, k.cd, now + k.cd * 1000L};
					CD.put(key, st);
				} else {
					// 서버 숫자는 올림이라 남은 시간을 (cd-1, cd] 안으로 맞춘다
					long left = st[2] - now;
					if (left > k.cd * 1000L || left <= (k.cd - 1) * 1000L) {
						st[2] = now + k.cd * 1000L - 500;
					}
				}
			} else {
				CD.remove(key);
			}
		}
		if (hpA >= 0) {
			hp = hpA;
			hpMax = hpB > 0 ? hpB : hpA;
		}
		if (hun >= 0) {
			hunger = hun;
		}
		if (arm >= 0) {
			armor = arm;
		}
		if (lv >= 0) {
			level = lv;
		}
		if (pc >= 0) {
			pct = Math.min(100, pc);
		}
		if (gn != null && gn.length() > 1) {
			String g = gn.toString();
			if (!g.equals(gain) || now - gainAt > 3000) {
				gainAt = now;
			}
			gain = g;
		}
		return cut;
	}

	private static Component cutAt(Component c, int cut) {
		MutableComponent out = Component.empty();
		int[] left = {cut};
		c.visit((FormattedText.StyledContentConsumer<Object>) (style, s) -> {
			if (left[0] <= 0) {
				return Optional.of(Boolean.TRUE);
			}
			String part = s.length() <= left[0] ? s : s.substring(0, left[0]);
			left[0] -= part.length();
			out.append(Component.literal(part).setStyle(style));
			return left[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** 그 카드의 "준비됨" 칸(a: 스킬마다 3칸, b: 0칸은 이름표 + 스킬마다 2칸). */
	private static int readyCol(Skill k) {
		int[] st = states(k.sheet);
		return st[0] + (k.col - st[0]) / st[1] * st[1];
	}

	private static int stateOf(Skill k) {
		int[] st = states(k.sheet);
		return Math.max(0, (k.col - st[0]) % st[1]);
	}

	/** [첫 스킬 칸, 스킬마다 칸 수]. */
	private static int[] states(String sheet) {
		if (sheet.startsWith("skill_cards_b")) {
			return new int[]{1, 2};
		}
		if (sheet.startsWith("skill_cards_a")) {
			return new int[]{0, 3};
		}
		return new int[]{0, 2};
	}

	// ==================== 그리기 ====================

	private static final String NS = "lunaslight";
	private static final Identifier HOTBAR = Identifier.fromNamespaceAndPath(NS, "textures/dungeon/hotbar.png");
	private static final Identifier SEL = Identifier.fromNamespaceAndPath(NS, "textures/dungeon/hotbar_selection.png");
	private static final Style HPN = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath(NS, "hpn")));
	private static final Identifier[] BACK = new Identifier[6];
	private static final Identifier[] HEART = new Identifier[21];

	static {
		for (int i = 0; i < 6; i++) {
			BACK[i] = Identifier.fromNamespaceAndPath(NS, "textures/dungeon/hb_back_" + i + ".png");
		}
		for (int i = 0; i <= 20; i++) {
			HEART[i] = Identifier.fromNamespaceAndPath(NS, "textures/dungeon/heart_" + i + ".png");
		}
	}

	/** 경험치 바 두께. 핫바 묶음은 이만큼 위로 올라간다. */
	public static final int BAR = 3;

	/** 다른 노바 HUD를 밀어낼 자리(화면 아래 가운데). [x0, y0, x1, y1]. */
	public static int[] reserved(int sw, int sh) {
		int cx = sw / 2;
		return new int[]{cx - 163 - 70, sh - 62, cx + 163 + 120, sh};   // 49-554차: 곡선 밖 양옆 레벨/경험치 글자 자리까지
	}

	/** 손에 든 아이템 이름, 액션바를 스킬 카드 위로 올림(HudElementHide 믹스인). 올렸으면 true. */
	public static boolean liftBegin(GuiGraphicsExtractor ctx) {
		if (!active) {
			return false;
		}
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, 0f, -26f);
		return true;
	}

	public static void liftEnd(GuiGraphicsExtractor ctx) {
		LunaCompat.guiPop(ctx);
	}

	/** HUD 위치(HudPosition.resolveX): 던전 UI 자리와 겹치면 가까운 옆으로 민다. */
	public static int pushX(kr.lunaslight.mod.module.setting.HudPosition pos, int x, int w, int h, int sw) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || LunaCompat.screenOf(mc) instanceof kr.lunaslight.mod.gui.LunaHudEditorScreen) {
				return x;
			}
			int sh = mc.getWindow().getGuiScaledHeight();
			int y = pos.predictY(sh, h);
			int[] r = reserved(sw, sh);
			if (x + w <= r[0] || x >= r[2] || y + h <= r[1] || y >= r[3]) {
				return x;
			}
			int mid = x + w / 2;
			return mid < sw / 2 ? r[0] - w - 2 : r[2] + 2;
		} catch (Throwable t) {
			return x;
		}
	}

	/** 화면 아래에 던전 UI 전체. */
	public static void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		List<Skill> sk;
		synchronized (skills) {
			sk = new ArrayList<>(skills);
		}
		drawAt(ctx, mc, sw / 2, sh, mc.player, sk, hp, hpMax, armor, hunger, level, pct, false);
	}

	/** 미리보기(처음 묻는 창)에도 쓰려고 값을 인자로 받는다. */
	public static void drawAt(GuiGraphicsExtractor ctx, Minecraft mc, int cx, int sh, Player pl, List<Skill> sk,
			int hpNow, int hpTop, int arm, int hun, int lv, int pc, boolean sample) {
		var P = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
		int B = sh - BAR;
		long now = System.currentTimeMillis();

		// 0) 49-555차(사용자: "체력 왼쪽이랑 오른쪽 끝부분이 딸리잖아"): 곡선 그림은 4번 칸과 하트 받침 사이(cx-19 ~ cx-15),
		// 받침 위쪽(칸 높이)이 투명이라 하트 양옆으로 바깥 화면이 비쳤다 - 두 칸 묶음 사이를 곡선과 같은 남색으로 먼저 채운다.
		// 49-556차(사용자: "여전히 체력바 옆이랑 이어지지가 않아, 자연스럽지가 않아, 숫자도 위치가 이상해"): 사용자가 준 LF 화면에 조각마다
		// 그림을 대 보고(픽셀 맞춤) 자리를 그대로 옮겼다. 곡선 6조각은 이어 붙여 cx-164부터(전엔 cx-161이라 칸, 하트보다 3칸 오른쪽으로 틀어져
		// 4번 칸 옆 투명 줄이 하트 옆으로 나와 끊겨 보였다), 칸 묶음 cx-100, 하트 (cx-19, 바닥-39), 하트 숫자 윗줄은 하트 위 + 15.
		// 1) 금빛 곡선(6조각, 4배 그림)
		int x = cx - 164;
		for (int i = 0; i < 6; i++) {
			int w = i == 5 ? 22 : 60;
			ctx.blit(P, BACK[i], x, B - 32, 0f, 0f, w, 32, w * 4, 128, w * 4, 128);
			x += w;
		}
		// 2) 칸(2배 그림) + 아이템
		ctx.blit(P, HOTBAR, cx - 100, B - 22, 0f, 0f, 224, 22, 448, 44, 448, 44);
		var inv = pl.getInventory();
		int sel = inv.getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			int ix = slotX(cx, i);
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty()) {
				ctx.item(pl, st, ix, B - 19, i + 1);
				ctx.itemDecorations(mc.font, st, ix, B - 19);
			}
		}
		ItemStack off = pl.getOffhandItem();
		// 49-553차(사용자: "왼손 배경은 왜 투명이고"): 곡선 그림의 왼손 칸은 테두리만 있어 속이 비친다 - 다른 칸과 같은 바탕을 깐다
		ctx.fill(cx - 127, B - 19, cx - 111, B - 3, 0xEB040406);
		if (!off.isEmpty()) {
			ctx.item(pl, off, cx - 127, B - 19, 10);
			ctx.itemDecorations(mc.font, off, cx - 127, B - 19);
		}
		int sx = slotX(cx, Math.max(0, Math.min(8, sel)));
		ctx.blit(P, SEL, sx - 4, B - 23, 0f, 0f, 24, 23, 96, 92, 96, 92);

		// 3) 하트 + 체력 글자 + 갑옷(테두리가 아래에서 위로 차오름)
		int hpN = hpNow < 0 || hpTop <= 0 ? (int) Math.ceil(pl.getHealth() / Math.max(1f, pl.getMaxHealth()) * 20f)
				: Math.round(20f * hpNow / hpTop);
		if (hpNow > 0 && hpN == 0) {
			hpN = 1;
		}
		hpN = Math.max(0, Math.min(20, hpN));
		// 49-554차(사용자: "그림 자체가 옆으로 틀어졌잖아"): 곡선 그림의 하트 받침(금빛 V)과 칸 사이 빈자리 가운데가 cx+2 쯤이라
		// 하트를 cx에 두면 왼쪽으로 쏠려 보였다 - 하트, 하트 숫자, 배고픔, 스킬 카드를 모두 받침 가운데(hc = cx + 2)에 맞춘다.
		int hc = cx + 1;   // 49-556차: LF 실측(하트 cx-19 ~ cx+21)
		int hx = hc - 20, hy = B - 39;
		ctx.blit(P, HEART[hpN], hx, hy, 0f, 0f, 40, 38, 160, 152, 160, 152);
		// 49-546차(사용자: "갑옷 삭제해 줘"): 하트 테두리 갑옷 표시는 뺐다.
		String ht = hpNow >= 0 ? hpNow + "/" + Math.max(hpTop, hpNow) : Math.round(pl.getHealth()) + "/" + Math.round(pl.getMaxHealth());
		// 체력 글자(49-545차 사용자: "폰트가 사진이랑 다르잖아"): LF 사진과 같은 글꼴 - LF 팩 hpn과 같게 배민 주아체(노바에 이미 있는 jua.ttf) 7.5 크기를
		// lunaslight:hpn(font/hpn.json)으로 쓴다. 흰 글자 + 진한 빨강 그림자(반 칸 아래 오른쪽). 숫자 위가 하트 위 + 14(LF 사진 실측).
		Component htc = Component.literal(ht).withStyle(HPN);
		int htw = Math.max(1, mc.font.width(htc));
		float hs = htw <= 32 ? 1f : 32f / htw;
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, hc - htw * hs / 2f, hy + 13.7f + (6 - 6 * hs) / 2f);
			LunaCompat.guiScale(ctx, hs, hs);
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, 0.5f, 0.5f);
			ctx.text(mc.font, htc, 0, 0, 0xFF7A0E18, false);
			LunaCompat.guiPop(ctx);
			ctx.text(mc.font, htc, 0, 0, 0xFFFFFFFF, false);
		} finally {
			LunaCompat.guiPop(ctx);
		}

		// 4) 배고픔(49-553차 사용자: "배고픔도 어디에다 제대로 보이게"): 9번 칸 오른쪽 곡선 위에 작은 판 + 서버 고기 그림 + 주아체 숫자
		int hg = hun >= 0 ? hun : pl.getFoodData().getFoodLevel();
		String hgs = String.valueOf(hg);
		int hsw = Math.round(jw(mc, hgs));
		// 49-554차(사용자: "배고픔도 사이드에 있으면 안 보인다고"): 하트 바로 위 가운데(양쪽 안쪽 스킬 카드 사이)
		int fpw = 14 + hsw + 4, fph = 11, fpx = hc - fpw / 2, fpy = B - 53;
		LunaDraw.roundRect(ctx, fpx, fpy, fpw, fph, 3, 0xD80C0E16);
		LunaDraw.roundRectOutline(ctx, fpx, fpy, fpw, fph, 3, hg <= 6 ? 0xC0FF7A5A : 0x80B89A5A);
		if (!glyphs().isEmpty()) {
			String ip = sheetPath("icon_hunger");
			int[] it = texSize(ip);
			int[] ib = cellBox(ip, 1, 0);
			int ih = 9, iw = Math.max(1, Math.round(ib[2] * 9f / Math.max(1, ib[3])));
			ctx.blit(P, texOf(ip), fpx + 2, fpy + 1, ib[0], ib[1], iw, ih, ib[2], ib[3], it[0], it[1]);
		}
		jua(ctx, mc, hgs, fpx + 14, fpy + 1.5f, hg <= 6 ? 0xFFFF9A7A : 0xFFFFF0D0, 1f, false);

		// 5) 스킬 카드: 하트를 사이에 두고 양쪽 핫바 칸 위에 대칭으로
		drawSkills(ctx, mc, cx, B, sk, now, sample);

		// 6) 경험치 바(맨 아래 3px). 49-554차(사용자: "끊김이 여전하잖아"): 바가 곡선 그림 아래 가장자리(cx-135 ~ cx+147, 남색)와
		// 폭, 색이 달라 이어지지 않고 끊겨 보였다 - 곡선 아래 가장자리와 똑같은 폭, 같은 남색으로 이어 붙인다.
		int x0 = cx - 138, x1 = cx + 144;   // 49-556차: 곡선 아래 가장자리(cx-164 기준 실측)
		ctx.fill(x0, sh - BAR, x1, sh, 0xF00E0D23);
		int p = pc;
		boolean tob = !sample && tobeolLv > 0 && tobeolNeed > 0 && tobeolPct >= 0f;
		if (tob) {
			p = Math.round(tobeolPct);   // 49-566차: 바도 토벌 경험치(/레벨 창에서 마지막으로 본 값)
		}
		if (p < 0 && lv >= 0 && pl.experienceLevel == lv) {
			p = Math.round(pl.experienceProgress * 100f);   // 서버가 바닐라 경험치에 같은 값을 넣는 경우
		}
		if (p >= 0) {
			int fx = x0 + Math.round((x1 - x0) * p / 100f);
			ctx.fill(x0, sh - BAR + 1, fx, sh, 0xE8D6AA4A);
			ctx.fill(x0, sh - BAR, fx, sh - BAR + 1, 0xF0FFE496);
		}
		// 글자는 핫바를 가리지 않게 곡선 그림 바깥 양옆(왼쪽 끝 cx-164에서 왼쪽으로, 오른쪽 끝 cx+164에서 오른쪽으로)
		int tx0 = cx - 166, tx1 = cx + 161;
		// 49-553차(사용자: "본인 레벨이 제대로 안 됨 - /레벨의 토벌 레벨", 글자가 깨져 보임): 레벨은 /레벨 창에서 읽은 토벌 레벨(없으면 서버 HUD 값),
		// 글자는 주아체 1배(작은 마크 글꼴을 줄이면 깨져 보였다). 오른쪽에 %와 지금/필요 경험치(위키 표 NeogulLevels.REQ 토벌).
		int showLv = !sample && tobeolLv > 0 ? tobeolLv : lv;
		if (showLv >= 0) {
			jua(ctx, mc, "Lv." + showLv + (!sample && tobeolLv > 0 ? " 토벌" : ""), tx0, sh - BAR - 9, 0xF0FFECBE, 1f, true);
		}
		if (p >= 0) {
			String right = p + "%";
			if (tob) {
				right = String.format("%,d / %,d  (%.1f%%)", tobeolExp, tobeolNeed, tobeolPct);
			} else if (showLv >= 1 && showLv < NeogulLevels.MAX_LV) {
				long need = NeogulLevels.REQ[1][showLv];
				if (need > 0) {
					right = String.format("%,d / %,d  (%d%%)", need * p / 100, need, p);
				}
			}
			jua(ctx, mc, right, tx1, sh - BAR - 9, 0xF0FFECBE, 1f, false);
		}
		if (gain != null && !sample) {
			long age = now - gainAt;
			// 49-568차(사용자: "던전 경험치 들어오는 거 가운데에도 뜨게, 조금 잘 보이게, 기존 것도 조금 더 잘 보이게"):
			// 오른쪽 것은 1.25배, 밝은 연두, 3초. 화면 가운데(조준점 아래)에도 2배 금색으로 떠올랐다가 사라진다.
			if (age < 3000) {
				int al = (int) (255 * Math.min(1f, (3000 - age) / 700f));
				if (al >= 8)
				jua(ctx, mc, gain + " 경험치", tx1, sh - BAR - 22 - age / 400f, (al << 24) | 0xC8FF8C, 1.25f, false);
			}
			if (age < 2200) {
				float in = Math.min(1f, age / 150f);
				float out = Math.min(1f, (2200 - age) / 600f);
				int al = (int) (255 * Math.min(in, out));
				float sc = 2.0f + 0.25f * (1f - in);
				String t = gain + " 경험치";
				float tw = jw(mc, t) * sc;
				int sw = mc.getWindow().getGuiScaledWidth();
				if (al >= 8)
				jua(ctx, mc, t, sw / 2f - tw / 2f, mc.getWindow().getGuiScaledHeight() / 2f + 22 - age / 120f, (al << 24) | 0xFFE27A, sc, false);
			}
		}
	}

	/** 주아체(lunaslight:hpn, 7.5) 글자 + 진한 그림자. */
	private static void jua(GuiGraphicsExtractor ctx, Minecraft mc, String s, float x, float y, int color, float scale, boolean right) {
		Component c = Component.literal(s).withStyle(HPN);
		float w = mc.font.width(c) * scale;
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, right ? x - w : x, y);
			LunaCompat.guiScale(ctx, scale, scale);
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, 0.5f, 0.5f);
			ctx.text(mc.font, c, 0, 0, (color & 0xFF000000) | 0x1A1208, false);
			LunaCompat.guiPop(ctx);
			ctx.text(mc.font, c, 0, 0, color, false);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private static float jw(Minecraft mc, String s) {
		return mc.font.width(Component.literal(s).withStyle(HPN));
	}

	private static int slotX(int cx, int i) {
		return i < 4 ? cx - 97 + 20 * i : cx + 25 + 20 * (i - 4);   // 49-556차: 칸 묶음 cx-100(LF 실측)
	}

	private static void drawSkills(GuiGraphicsExtractor ctx, Minecraft mc, int cx, int B, List<Skill> sk, long now, boolean sample) {
		int n = sk.size();
		if (n == 0) {
			return;
		}
		// 안쪽(하트 옆)부터 바깥으로. 홀수면 가운데 하나는 하트 위.
		// 49-539차(사용자: "하트 위에는 스킬 안 들어갔으면"): 홀수면 남는 하나는 왼쪽에(서버 순서 R F C V 그대로 왼쪽부터)
		int left = (n + 1) / 2;
		int[] xs = new int[n];
		int[] ys = new int[n];
		int top = B - 57;   // 49-553차: 카드(20) + 1 + 키캡(12) 바닥이 핫바 선택 테두리 바로 위(키캡이 카드를 안 가림)
		// 하트 가운데에서 32, 56, 80 … 떨어진 자리(카드 20 + 틈 4)
		for (int i = 0; i < left; i++) {
			xs[i] = cx + 1 - 32 - 24 * (left - 1 - i);   // 왼쪽: 바깥 → 안쪽 순서
			ys[i] = top;
		}
		for (int i = left; i < n; i++) {
			xs[i] = cx + 1 + 32 + 24 * (i - left);   // 오른쪽: 안쪽 → 바깥
			ys[i] = top;
		}
		var P = net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
		for (int i = 0; i < n; i++) {
			Skill k = sk.get(i);
			int x = xs[i] - 10, y = ys[i];
			String path = sheetPath(k.sheet);
			int cols = sheetCols(k.sheet);
			int[] tex = texSize(path);
			int state = stateOf(k);
			boolean cooling = k.cd > 0;
			boolean locked = !cooling && state > 0 && k.sheet.startsWith("skill_cards_a") && state == 2;
			boolean ready = !cooling && !locked && state == 0;
			int col = cooling ? readyCol(k) : k.col;
			int[] bx = cellBox(path, cols, col);
			// 49-553차(사용자: "그 스킬 키 가능한 거 삭제해 주고"): 쓸 수 있을 때 금빛 테두리 강조는 뺐다
			ctx.blit(P, texOf(path), x, y, bx[0], bx[1], 20, 20, bx[2], bx[3], tex[0], tex[1]);
			if (cooling) {
				long[] st = CD.get(k.sheet + "#" + readyCol(k));
				float frac = sample ? 0.6f : 1f;
				if (st != null && st[1] > 0) {
					frac = Math.max(0f, Math.min(1f, (st[2] - now) / (st[1] * 1000f)));
				}
				pie(ctx, x, y, 20, frac, 0xB4080A12);
				// 49-553차(사용자: "쿨타임 돌 때 초가 제대로 안 보여"): 주아체 1.5배 + 사방 검은 테두리, 카드 가운데
				String t = String.valueOf(k.cd);
				Component tc = Component.literal(t).withStyle(HPN);
				float sc = 1.5f, tw2 = mc.font.width(tc) * sc;
				LunaCompat.guiPush(ctx);
				try {
					LunaCompat.guiTranslate(ctx, x + 10 - tw2 / 2f, y + 10 - 4.2f * sc);
					LunaCompat.guiScale(ctx, sc, sc);
					for (int d = 0; d < 4; d++) {
						LunaCompat.guiPush(ctx);
						LunaCompat.guiTranslate(ctx, d == 0 ? -0.7f : d == 1 ? 0.7f : 0f, d == 2 ? -0.7f : d == 3 ? 0.7f : 0f);
						ctx.text(mc.font, tc, 0, 0, 0xFF000000, false);
						LunaCompat.guiPop(ctx);
					}
					ctx.text(mc.font, tc, 0, 0, 0xFFFFFFFF, false);
				} finally {
					LunaCompat.guiPop(ctx);
				}
			}
			// 키 표시(49-553차 사용자: "키가 제대로 안 보이고"): 카드 바로 아래(가리지 않게) 높이 12 키캡, 주아체 흰 글자.
			// 테두리는 둥근 판 두 장(바깥 테두리색 → 1칸 들인 바탕)이라 모서리가 어긋나지 않는다.
			String key = keyName(k.keyCol);
			if (key != null) {
				Component kc = Component.literal(key).withStyle(HPN);
				int tw = Math.max(1, mc.font.width(kc));
				int kw = Math.max(14, tw + 8), kh = 12;
				int kx = x + 10 - kw / 2, ky = y + 21;
				LunaDraw.roundRect(ctx, kx, ky + 1, kw, kh, 3, 0x80000000);   // 아래 그림자(키 두께)
				LunaDraw.roundRect(ctx, kx, ky, kw, kh, 3, cooling ? 0xFF3A3F4C : 0xFF8A93A6);
				LunaDraw.roundRect(ctx, kx + 1, ky + 1, kw - 2, kh - 2, 2, cooling ? 0xFF15171E : 0xFF20232C);
				LunaCompat.guiPush(ctx);
				try {
					LunaCompat.guiTranslate(ctx, kx + (kw - tw) / 2f, ky + 2.2f);
					ctx.text(mc.font, kc, 0, 0, cooling ? 0xFF9AA0AC : 0xFFFFFFFF, false);
				} finally {
					LunaCompat.guiPop(ctx);
				}
			}
		}
	}

	/** 서버 키캡 그림(skill_keycaps)의 칸 순서 - 칸 번호 → 키 이름. */
	private static final String[] KEYS = {"R", "F", "C", "V", "G", "Z", "X", "B", "N", "M", "H", "J", "K", "L", "I", "O", "P", "U", "Y",
		"2", "3", "4", "5", "6", "7", "8", "9", "F1", "F2", "F3", "F4", "F6", "F7", "F8", "F9", "F10", "F12", "-", "=", "[", "]", ";", "'", ",", "."};

	/** 지금 HUD 스킬 카드에 붙은 키들(GLFW 번호). 노바 키 지정 막기(NeogulSkillKeys.blocksNova)용. */
	public static java.util.Set<Integer> skillKeyCodes() {
		java.util.Set<Integer> out = new java.util.HashSet<>();
		synchronized (skills) {
			for (Skill k : skills) {
				String n = keyName(k.keyCol);
				if (n == null) {
					continue;
				}
				if (n.length() == 1) {
					char c = n.charAt(0);
					out.add(switch (c) {
						case '-' -> 45;
						case '=' -> 61;
						case '[' -> 91;
						case ']' -> 93;
						case ';' -> 59;
						case '\'' -> 39;
						case ',' -> 44;
						case '.' -> 46;
						default -> (int) Character.toUpperCase(c);   // A~Z 65~90, 0~9 48~57
					});
				} else if (n.startsWith("F")) {
					out.add(289 + Integer.parseInt(n.substring(1)));   // F1 = 290
				}
			}
		}
		return out;
	}

	private static String keyName(int col) {
		return col >= 0 && col < KEYS.length ? KEYS[col] : null;
	}

	/** 가운데에서 12시부터 시계 방향으로 frac만큼 남은 부채꼴을 덮는다(줄마다 가로 칸 묶어 칠함). */
	private static void pie(GuiGraphicsExtractor ctx, int x, int y, int s, float frac, int color) {
		if (frac <= 0f) {
			return;
		}
		if (frac >= 0.999f) {
			ctx.fill(x, y, x + s, y + s, color);
			return;
		}
		// 지나간 각도(0 ~ 1-frac)는 비우고 나머지를 덮는다
		double start = (1f - frac) * Math.PI * 2;
		float c = s / 2f;
		for (int py = 0; py < s; py++) {
			int run = -1;
			for (int px = 0; px <= s; px++) {
				boolean in = false;
				if (px < s) {
					double dx = px + 0.5 - c, dy = py + 0.5 - c;
					double ang = Math.atan2(dx, -dy);   // 12시 = 0, 시계 방향
					if (ang < 0) {
						ang += Math.PI * 2;
					}
					in = ang >= start;
				}
				if (in && run < 0) {
					run = px;
				} else if (!in && run >= 0) {
					ctx.fill(x + run, y + py, x + px, y + py + 1, color);
					run = -1;
				}
			}
		}
	}

	private static void outlined(GuiGraphicsExtractor ctx, Minecraft mc, String s, int cx, int y, int color, int edge) {
		int w = mc.font.width(s) - 1;
		int x = cx - w / 2;
		ctx.text(mc.font, s, x - 1, y, edge, false);
		ctx.text(mc.font, s, x + 1, y, edge, false);
		ctx.text(mc.font, s, x, y - 1, edge, false);
		ctx.text(mc.font, s, x, y + 1, edge, false);
		ctx.text(mc.font, s, x, y, color, false);
	}

	private static void small(GuiGraphicsExtractor ctx, Minecraft mc, String s, int x, int y, int color, float scale, boolean right) {
		int w = mc.font.width(s);
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, right ? x - w * scale : x, y);
			LunaCompat.guiScale(ctx, scale, scale);
			ctx.text(mc.font, s, 0, 0, color, true);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	/** 처음 묻는 창의 미리보기용 예시 스킬(서버 그림이 있으면). */
	public static List<Skill> sample() {
		List<Skill> out = new ArrayList<>();
		synchronized (skills) {
			if (!skills.isEmpty()) {
				return new ArrayList<>(skills);
			}
		}
		if (!glyphs().isEmpty()) {
			int[][] spec = {{0, 0, 0, -1}, {3, 1, 1, 7}, {1, 0, 2, -1}, {7, 1, 3, 3}};
			for (int[] sp : spec) {
				Skill k = new Skill();
				k.sheet = sp[2] < 2 ? "skill_cards_a_blank" : "skill_cards_b_blank";
				k.col = sp[0];
				k.keyCol = sp[2];
				k.cd = sp[3];
				out.add(k);
			}
		}
		return out;
	}
}
