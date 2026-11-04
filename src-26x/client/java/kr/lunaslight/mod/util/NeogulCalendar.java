package kr.lunaslight.mod.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-518차(사용자: 일일 퀘스트 화면 사진 - "보상 받으면 일퀘 보상 받음, 안 받으면 보상 안 받음, 위 목록이 다 완료가 안 되거나 한 적이 없으면 일퀘 안 함
 * 이런 식으로 너굴 달력을 주고 조작 불가능하게. 추천이랑 핫타임도 받았는지 표시하고 그날 접속한 시간도"): <b>너굴 달력</b> 기록.
 *
 * <ul>
 *   <li>일퀘: /퀘스트 화면("N월 N일 …의 퀘스트")이 열리면 칸의 아이템 이름, 설명에서 "완료"나 "a / b"를 읽어 그날 전부 완료인지 적는다.
 *       다 완료된 채 [보상 받기]를 누르면(1.5초 안에 "아직", "완료하지" 같은 거절 메시지가 없으면) 보상 받음. 버튼 이름에 "수령 완료",
 *       "받음"이 보여도 받음.</li>
 *   <li>추천, 핫타임: {@code NeogulDailyModule}이 감지한 것을 {@link #mark}로 넣는다.</li>
 *   <li>접속 시간: 통계(LunaStats)의 너굴마을 서버 기록의 날짜별 시간.</li>
 * </ul>
 * 사용자가 고칠 수 있는 곳은 없다(감지한 것만). 파일: config/lunaslight/neogul-calendar.json.
 */
public final class NeogulCalendar {
	private NeogulCalendar() {
	}

	/** 그날 일퀘 상태. */
	public enum Quest {
		NONE, PARTIAL, DONE, CLAIMED
	}

	public static final class Day {
		public Quest quest = Quest.NONE;
		public int done, total;
		public boolean rec, hot;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern TITLE_DATE = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
	private static final Pattern PROGRESS = Pattern.compile("(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)");
	private static final Map<String, Day> DAYS = new TreeMap<>();
	private static boolean loaded, registered;
	private static Object lastScreen;
	private static String questDate;
	private static long claimAt;
	private static String claimDate;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NeogulCalendar::tick);
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof AbstractContainerScreen<?>) {
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.beforeMouseClick(screen).register((s, e) -> noteClick(s));
				}
			});
			ChatState.addPlainListener(NeogulCalendar::onChat);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:init", t);
		}
	}

	// ==================== 기록 ====================

	public static synchronized Day day(String date) {
		load();
		return DAYS.computeIfAbsent(date, d -> new Day());
	}

	public static synchronized Day peek(String date) {
		load();
		return DAYS.get(date);
	}

	/** 추천("rec"), 핫타임("hot")을 오늘 날짜에 적는다. */
	public static synchronized void mark(String field, boolean on) {
		Day d = day(LocalDate.now().toString());
		boolean before = "rec".equals(field) ? d.rec : d.hot;
		if (before == on) {
			return;
		}
		if ("rec".equals(field)) {
			d.rec = on;
		} else {
			d.hot = on;
		}
		save();
	}

	/** 그날 너굴마을 접속 시간(ms) - 통계의 너굴마을 서버 기록 합. */
	public static long playMs(String date) {
		long sum = 0;
		try {
			for (LunaStats.Scope s : LunaStats.worlds()) {
				if (s.key != null && s.key.toLowerCase(java.util.Locale.ROOT).contains("mcng")) {
					sum += s.days.getOrDefault(date, 0L);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:play", t);
		}
		return sum;
	}

	// ==================== 퀘스트 화면 ====================

	private static void tick(Minecraft mc) {
		if (mc == null || mc.player == null) {
			return;
		}
		Object screen = LunaCompat.screenOf(mc);
		if (claimAt > 0 && System.currentTimeMillis() - claimAt > 1500) {
			// 거절 메시지 없이 지나감 - 받은 것으로
			synchronized (NeogulCalendar.class) {
				Day d = day(claimDate);
				if (d.quest == Quest.DONE) {
					d.quest = Quest.CLAIMED;
					save();
				}
			}
			claimAt = 0;
		}
		if (!(screen instanceof AbstractContainerScreen<?> hs) || screen instanceof InventoryScreen) {
			lastScreen = screen;
			questDate = null;
			return;
		}
		try {
			if (!NeogulData.onNeogul(mc)) {
				return;
			}
			String title = plain(hs.getTitle().getString());
			if (!title.contains("퀘스트")) {
				questDate = null;
				return;
			}
			Matcher m = TITLE_DATE.matcher(title);
			LocalDate today = LocalDate.now();
			String date = today.toString();
			if (!m.find()) {
				questDate = null;   // 날짜 없는 퀘스트 화면(길라잡이 등)은 일퀘가 아니다
				return;
			}
			{
				try {
					int mo = Integer.parseInt(m.group(1)), dd = Integer.parseInt(m.group(2));
					LocalDate t = LocalDate.of(today.getYear(), mo, dd);
					if (t.isAfter(today.plusDays(1))) {
						t = t.minusYears(1);   // 1월 1일 직후에 12월 31일 화면
					}
					date = t.toString();
				} catch (Throwable ignored) {
				}
			}
			questDate = date;
			readQuestScreen(hs, date);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:read", t);
		}
		lastScreen = screen;
	}

	private static void readQuestScreen(AbstractContainerScreen<?> hs, String date) {
		var slots = hs.getMenu().slots;
		int n = Math.max(0, slots.size() - 36);
		int total = 0, done = 0;
		boolean claimedButton = false;
		for (int i = 0; i < n; i++) {
			ItemStack st = slots.get(i).getItem();
			if (st == null || st.isEmpty()) {
				continue;
			}
			String name = plain(st.getHoverName().getString());
			List<String> lines = new ArrayList<>();
			lines.add(name);
			try {
				var lore = st.get(DataComponents.LORE);
				if (lore != null) {
					for (Component c : lore.lines()) {
						lines.add(plain(c.getString()));
					}
				}
			} catch (Throwable ignored) {
			}
			String all = String.join(" ", lines);
			if (name.contains("보상")) {
				if (all.contains("수령 완료") || all.contains("수령완료") || all.contains("이미 받") || all.contains("받음")) {
					claimedButton = true;
				}
				continue;   // 버튼
			}
			Boolean ok = null;
			if (all.contains("완료")) {
				ok = true;
			} else {
				Matcher pm = PROGRESS.matcher(all);
				if (pm.find()) {
					try {
						long a = Long.parseLong(pm.group(1).replace(",", "")), b = Long.parseLong(pm.group(2).replace(",", ""));
						ok = b > 0 && a >= b;
					} catch (Throwable ignored) {
					}
				}
			}
			if (ok != null) {
				total++;
				if (ok) {
					done++;
				}
			}
		}
		if (total == 0) {
			return;
		}
		synchronized (NeogulCalendar.class) {
			Day d = day(date);
			Quest q = claimedButton ? Quest.CLAIMED : done >= total ? Quest.DONE : Quest.PARTIAL;
			if (d.quest == Quest.CLAIMED) {
				q = Quest.CLAIMED;   // 한 번 받았으면 그대로
			}
			if (q != d.quest || d.done != done || d.total != total) {
				d.quest = q;
				d.done = done;
				d.total = total;
				save();
			}
		}
	}

	/** [보상 받기]를 눌렀다 - 다 완료된 날이면 1.5초 뒤 받음(그 사이 거절 메시지가 오면 취소). */
	private static void noteClick(Object s) {
		if (questDate == null || !(s instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc)) {
			return;
		}
		try {
			Slot sl = acc.lunaslight$hoveredSlot();
			if (sl == null) {
				return;
			}
			String name = plain(sl.getItem().getHoverName().getString());
			if (name.contains("보상") && name.contains("받기")) {
				Day d = peek(questDate);
				if (d != null && d.quest == Quest.DONE) {
					claimAt = System.currentTimeMillis();
					claimDate = questDate;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static void onChat(String line) {
		if (claimAt == 0 || line == null) {
			return;
		}
		String l = line.replace(" ", "");
		if (l.contains("아직") || l.contains("완료하지") || l.contains("완료해야") || l.contains("받을수없") || l.contains("이미받")) {
			if (l.contains("이미받")) {
				synchronized (NeogulCalendar.class) {
					day(claimDate).quest = Quest.CLAIMED;
					save();
				}
			}
			claimAt = 0;
		}
	}

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
			.forEach(sb::appendCodePoint);
		return sb.toString().trim();
	}

	// ==================== 파일 ====================

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-calendar.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = file();
			if (!Files.exists(p)) {
				return;
			}
			@SuppressWarnings("deprecation")
			JsonElement el = new JsonParser().parse(Files.readString(p, StandardCharsets.UTF_8));
			if (el == null || !el.isJsonObject()) {
				return;
			}
			for (var e : el.getAsJsonObject().entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				JsonObject o = e.getValue().getAsJsonObject();
				Day d = new Day();
				try {
					d.quest = Quest.valueOf(o.has("quest") ? o.get("quest").getAsString() : "NONE");
				} catch (Throwable ignored) {
				}
				d.done = o.has("done") ? o.get("done").getAsInt() : 0;
				d.total = o.has("total") ? o.get("total").getAsInt() : 0;
				d.rec = o.has("rec") && o.get("rec").getAsBoolean();
				d.hot = o.has("hot") && o.get("hot").getAsBoolean();
				DAYS.put(e.getKey(), d);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:load", t);
		}
	}

	private static void save() {
		try {
			JsonObject root = new JsonObject();
			for (var e : DAYS.entrySet()) {
				Day d = e.getValue();
				JsonObject o = new JsonObject();
				o.addProperty("quest", d.quest.name());
				o.addProperty("done", d.done);
				o.addProperty("total", d.total);
				o.addProperty("rec", d.rec);
				o.addProperty("hot", d.hot);
				root.add(e.getKey(), o);
			}
			Path p = file();
			Files.createDirectories(p.getParent());
			Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:save", t);
		}
	}
}
