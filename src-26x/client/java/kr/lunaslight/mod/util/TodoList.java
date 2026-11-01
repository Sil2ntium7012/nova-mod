package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 49-473차(사용자: "기능에 카테고리에 서버처럼 할 일 목록 만들어서 거기에 내가 할 일 목록 추가하고 행동 관련된 것도 등록할 수 있게,
 * 너무 깊게는 말고, 너굴 관련 마크 기본 관련 추가 안 해도 되게, 그 행동을 하면 자동 체크 혹은 직접 체크, HUD로도 뜨게"): <b>할 일 목록</b>.
 *
 * <p>할 일 하나 = 이름 + 행동(직접 체크, 블록 캐기, 몹 잡기, 낚시, 아이템 얻기, 플레이 시간, 채팅 글) + 대상(이름에 이 글이 들어간 것만,
 * 비우면 아무거나) + 목표 수 + [매일](자정에 다시 0) + [너굴](너굴마을 접속 중에만 셈). 행동을 하면 수가 올라가고 목표에 닿으면 저절로 체크,
 * 언제든 눌러서 직접 체크도 된다. 자주 쓰는 것은 {@link #PRESETS}(마크 기본, 너굴마을)로 바로 넣는다.
 *
 * <p>세는 법(전부 이 컴퓨터에서 보이는 것만):
 * <ul>
 *   <li>블록 캐기: MultiPlayerGameMode.destroyBlock이 true(TodoGameModeMixin) - 블록 이름에 대상 글.</li>
 *   <li>몹 잡기: 내가 때린 생물이 5초 안에 죽으면(TodoGameModeMixin.attack → 매 틱 확인) - 생물 이름(서버가 붙인 이름 포함)에 대상 글.</li>
 *   <li>낚시: 찌가 걷힌 뒤 2초 안에 인벤토리에 아이템이 늘면 한 번.</li>
 *   <li>아이템 얻기: 상자 같은 창이 닫혀 있을 때 인벤토리에서 늘어난 수(아이템 보이는 이름에 대상 글).</li>
 *   <li>플레이 시간: 월드에 있는 동안 분 단위.</li>
 *   <li>채팅 글: 서버 채팅 한 줄에 대상 글(띄어쓰기 무시, | 로 여러 개)이 들어 있으면 한 번. 플레이어 채팅(&lt;이름&gt;)은 아님.</li>
 * </ul>
 * 저장: config/lunaslight/todo.json.
 */
public final class TodoList {
	private TodoList() {
	}

	public static final String MANUAL = "manual";
	public static final String BREAK = "break";
	public static final String KILL = "kill";
	public static final String FISH = "fish";
	public static final String GAIN = "gain";
	public static final String PLAY = "play";
	public static final String CHAT = "chat";

	/** 할 일 하나(저장 형식 그대로). */
	public static final class Task {
		public String text = "";
		public String type = MANUAL;
		public String target = "";
		public int goal = 1;
		public int progress;
		public boolean done;
		public boolean daily;
		public boolean neogul;
		public String day = "";
	}

	/** 미리 만든 할 일. */
	public record Preset(String group, String label, String type, String text, String target, int goal, boolean daily, boolean neogul) {
	}

	public static final List<Preset> PRESETS = List.of(
		new Preset("기본", "직접 체크", MANUAL, "", "", 1, false, false),
		new Preset("기본", "블록 캐기", BREAK, "블록 100개 캐기", "", 100, false, false),
		new Preset("기본", "몹 잡기", KILL, "몹 20마리 잡기", "", 20, false, false),
		new Preset("기본", "낚시", FISH, "낚시 10번", "", 10, false, false),
		new Preset("기본", "아이템 얻기", GAIN, "아이템 64개 모으기", "", 64, false, false),
		new Preset("기본", "플레이 시간", PLAY, "1시간 하기", "", 60, false, false),
		new Preset("기본", "채팅 글", CHAT, "", "", 1, false, false),
		new Preset("너굴", "서버 추천", CHAT, "서버 추천하기", "추천 보상|추천해 주셔서|추천 감사", 1, true, true),
		new Preset("너굴", "핫타임", CHAT, "핫타임 받기", "핫타임 보상|핫타임 지급", 1, true, true),
		new Preset("너굴", "접속보상", PLAY, "접속보상 1시간", "", 60, true, true),
		new Preset("너굴", "던전 사냥", KILL, "던전 몹 100마리", "", 100, true, true),
		new Preset("너굴", "작물 캐기", GAIN, "감자 300개 캐기", "감자", 300, true, true),
		new Preset("너굴", "낚시", FISH, "낚시 30번", "", 30, true, true),
		new Preset("너굴", "채광", BREAK, "광산 50번 캐기", "", 50, true, true),
		new Preset("너굴", "허수아비", KILL, "허수아비 잡기", "허수아비", 1, false, true),
		new Preset("너굴", "일일퀘스트", MANUAL, "일일퀘스트 올클리어", "", 1, true, true));

	/** 행동 이름(화면, HUD). */
	public static String typeName(String type) {
		return switch (type == null ? "" : type) {
			case BREAK -> "블록 캐기";
			case KILL -> "몹 잡기";
			case FISH -> "낚시";
			case GAIN -> "아이템 얻기";
			case PLAY -> "플레이 시간";
			case CHAT -> "채팅 글";
			default -> "직접 체크";
		};
	}

	/** 목표 수 단위. */
	public static String unit(String type) {
		return switch (type == null ? "" : type) {
			case BREAK, GAIN -> "개";
			case KILL -> "마리";
			case PLAY -> "분";
			default -> "번";
		};
	}

	public static boolean needsTarget(String type) {
		return BREAK.equals(type) || KILL.equals(type) || GAIN.equals(type) || CHAT.equals(type);
	}

	public static boolean needsGoal(String type) {
		return !MANUAL.equals(type);
	}

	// ==================== 목록, 저장 ====================

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static List<Task> tasks;
	private static boolean dirty;
	private static long savedAt;

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("todo.json");
	}

	public static synchronized List<Task> all() {
		if (tasks == null) {
			tasks = new ArrayList<>();
			try {
				Path f = file();
				if (Files.isRegularFile(f)) {
					List<Task> l = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Task>>() { }.getType());
					if (l != null) {
						for (Task t : l) {
							if (t != null) {
								tasks.add(t);
							}
						}
					}
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("todo:load", t);
			}
		}
		return tasks;
	}

	public static synchronized void add(Task t) {
		t.day = LocalDate.now().toString();
		all().add(t);
		changed();
	}

	public static synchronized void remove(Task t) {
		all().remove(t);
		changed();
	}

	public static synchronized void toggle(Task t) {
		t.done = !t.done;
		if (t.done && t.progress < t.goal) {
			t.progress = t.goal;
		} else if (!t.done && t.progress >= t.goal) {
			t.progress = 0;
		}
		changed();
	}

	public static void changed() {
		dirty = true;
		savedAt = 0;
		saveIfDirty();
	}

	private static synchronized void saveIfDirty() {
		if (!dirty || System.currentTimeMillis() - savedAt < 5000) {
			return;
		}
		dirty = false;
		savedAt = System.currentTimeMillis();
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling("todo.json.tmp");
			Files.writeString(tmp, GSON.toJson(all()), StandardCharsets.UTF_8);
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable t) {
			LunaCompat.warnOnce("todo:save", t);
		}
	}

	public static int openCount() {
		int n = 0;
		for (Task t : all()) {
			if (!t.done) {
				n++;
			}
		}
		return n;
	}

	// ==================== 세기 ====================

	private static boolean onNeogul;

	private static void count(String type, String name, int n) {
		if (n <= 0) {
			return;
		}
		boolean any = false;
		synchronized (TodoList.class) {
			for (Task t : all()) {
				if (t.done || !type.equals(t.type) || (t.neogul && !onNeogul) || !matches(t.target, name)) {
					continue;
				}
				t.progress = Math.min(t.goal, t.progress + n);
				any = true;
				if (t.progress >= t.goal) {
					t.done = true;
					finished(t);
				}
			}
		}
		if (any) {
			dirty = true;
		}
	}

	private static boolean matches(String target, String name) {
		if (target == null || target.isBlank()) {
			return true;
		}
		if (name == null) {
			return false;
		}
		String n = name.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
		for (String w : target.split("\\|")) {
			String c = w.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
			if (!c.isEmpty() && n.contains(c)) {
				return true;
			}
		}
		return false;
	}

	private static void finished(Task t) {
		Minecraft mc = Minecraft.getInstance();
		LunaCompat.playUiSound(mc, "BLOCK_NOTE_BLOCK_CHIME", 1.5f);
		LunaCompat.sendActionBar(mc, "§a할 일 완료 §f" + (t.text == null || t.text.isBlank() ? typeName(t.type) : t.text));
	}

	/** TodoGameModeMixin: 블록을 캤다. */
	public static void onBreak(String blockName) {
		count(BREAK, blockName, 1);
	}

	private record Hit(Entity e, long at) {
	}

	private static final List<Hit> HITS = new ArrayList<>();
	private static long tick;

	/** TodoGameModeMixin: 생물을 때렸다(5초 안에 죽으면 잡은 것). */
	public static void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || e instanceof Player) {
			return;
		}
		for (Iterator<Hit> it = HITS.iterator(); it.hasNext(); ) {
			if (it.next().e() == e) {
				it.remove();
			}
		}
		HITS.add(new Hit(e, tick));
		if (HITS.size() > 64) {
			HITS.remove(0);
		}
	}

	/** ChatState: 서버 채팅 한 줄. */
	public static void onChat(String line) {
		if (line == null || line.isEmpty() || line.startsWith("<")) {
			return;
		}
		count(CHAT, line, 1);
	}

	private static Map<String, Integer> lastInv;
	private static boolean fishing;
	private static long fishEnd = -1000;
	private static int playTicks;
	private static String today = "";

	/** TodoModule(늘 켜짐)이 매 틱 부른다. */
	public static void tick(Minecraft mc) {
		tick++;
		String d = LocalDate.now().toString();
		if (!d.equals(today)) {
			today = d;
			synchronized (TodoList.class) {
				for (Task t : all()) {
					if (t.daily && !d.equals(t.day)) {
						t.day = d;
						t.progress = 0;
						t.done = false;
						dirty = true;
					}
				}
			}
		}
		if (mc == null || mc.player == null || mc.level == null) {
			lastInv = null;
			HITS.clear();
			fishing = false;
			saveIfDirty();
			return;
		}
		onNeogul = NeogulData.onNeogul(mc);
		Player p = mc.player;
		// 몹 잡기
		for (Iterator<Hit> it = HITS.iterator(); it.hasNext(); ) {
			Hit h = it.next();
			Entity e = h.e();
			boolean dead = e instanceof LivingEntity le && (le.isDeadOrDying() || le.getHealth() <= 0f);
			if (dead) {
				it.remove();
				String name = e.getName().getString();
				count(KILL, name, 1);
			} else if (tick - h.at() > 100 || e.isRemoved()) {
				it.remove();
			}
		}
		// 플레이 시간
		if (++playTicks >= 1200) {
			playTicks = 0;
			count(PLAY, "", 1);
		}
		// 낚시: 찌가 걷힌 순간
		boolean f = p.fishing != null;
		if (fishing && !f) {
			fishEnd = tick;
		}
		fishing = f;
		// 인벤토리 늘어난 것(상자 같은 창이 열려 있으면 기준만 다시)
		boolean container = p.containerMenu != p.inventoryMenu;
		Map<String, Integer> now = invCounts(p);
		if (lastInv != null && !container) {
			int gained = 0;
			for (Map.Entry<String, Integer> en : now.entrySet()) {
				int diff = en.getValue() - lastInv.getOrDefault(en.getKey(), 0);
				if (diff > 0) {
					count(GAIN, en.getKey(), diff);
					gained += diff;
				}
			}
			if (gained > 0 && tick - fishEnd <= 40) {
				fishEnd = -1000;
				count(FISH, "", 1);
			}
		}
		lastInv = now;
		saveIfDirty();
	}

	private static Map<String, Integer> invCounts(Player p) {
		Map<String, Integer> m = new HashMap<>();
		try {
			var inv = p.getInventory();
			for (int i = 0; i < 36; i++) {
				ItemStack s = inv.getItem(i);
				if (s != null && !s.isEmpty()) {
					m.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("todo:inv", t);
		}
		return m;
	}
}
