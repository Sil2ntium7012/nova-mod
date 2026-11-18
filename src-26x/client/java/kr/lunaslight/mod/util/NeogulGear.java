package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-679차: <b>장비 신호</b>(LUNARIS docs/장비-신호-규격.md v1) - 루나리스 [자동 일퀘] 연동이 켜진 사람이 가진 중요한 장비와
 * 창고에 넣고 뺀 것, 지금 하는 일(잠광, 낚시, 자리 비움)을 봇에 보낸다. 신호는 {@code {"ev":"gear",...}} 한 줄(길면 pg로 나눔).
 *
 * <p>보는 장비: [치장도구] 초월 1단계 이상, [편의] 전부, [채집도구] (Lv.8) 이상, [낚시] (Lv.7) 이상 또는 (Lv.Max).
 * <p>우리 섬 판단(규격 9장): 채팅창으로 "/섬"(자기 섬으로 이동)을 친 뒤 월드가 바뀌면 우리 섬, "/섬 방문", "/스폰", "/워프" 등을 친 뒤면
 * 우리 섬 아님, 그 밖에 월드가 바뀌면 모름. 섬 상자(box isl)는 우리 섬일 때만 보낸다(모르면 안 보냄).
 */
public final class NeogulGear {
	private NeogulGear() {
	}

	/** 큐에 넣는 곳(LunarisLinkModule): 같은 boxKey 의 아직 안 나간 장비 신호는 바꿔치기. */
	public interface Sink {
		void gear(List<JsonObject> pages, String boxKey);
	}

	public enum Island { YES, NO, UNKNOWN }

	public static volatile Island island = Island.UNKNOWN;

	private static final Pattern LV = Pattern.compile("\\(\\s*Lv\\.?\\s*(\\d+|Max)\\s*\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TRANS = Pattern.compile("초월[^0-9\\n]{0,12}?(\\d+)\\s*단계");

	// ---- 상태 ----
	private static Object lastLevel, lastConn;
	private static long joinAt;
	private static boolean initPending = true;
	private static String sentSig = "";
	private static String pendSig;
	private static long pendSince;
	private static int sentH = -2;
	private static int handSeen = -2;
	private static long handSince, lastHandSend;
	private static long lastBeat;
	private static boolean emptySent;
	private static String sentAct = "";
	private static final long[] actSends = new long[2];

	// 활동
	private static long lastMine, lastFish;
	private static String kind = "etc";
	private static long kindStart = System.currentTimeMillis();
	private static final java.util.Map<String, Long> kindEnd = new java.util.HashMap<>();
	private static boolean afk;
	private static long afkStart, afkSum;

	// 우리 섬
	private static String pendCmd;
	private static long pendCmdAt;
	private static int recentSize = -1;
	private static String recentLast;

	// 보관함
	private static Object boxScreen;
	private static long boxOpenedAt;
	private static String boxSentSig;

	/** TodoGameModeMixin: 블록을 캤다 - 사암이면 잠광. */
	public static void onBreak(BlockState st) {
		try {
			if (st != null && String.valueOf(st).contains("sandstone")) {
				lastMine = System.currentTimeMillis();
			}
		} catch (Throwable ignored) {
		}
	}

	/** 연동 코드를 새로 넣었다 - 처음 신호(ini)부터. */
	public static void relink() {
		initPending = true;
		joinAt = System.currentTimeMillis() + 2000;
		sentSig = "";
	}

	private static String clean(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length());
		s.replaceAll("§.", "").codePoints()
				.filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString().replaceAll("\\s+", " ").trim();
	}

	/** 보는 장비면 {n, x, m, c}, 아니면 null. */
	static JsonObject item(ItemStack st) {
		if (st == null || st.isEmpty()) {
			return null;
		}
		String n = clean(st.getHoverName().getString());
		int x = 0;
		try {
			for (String l : LunaCompat.itemLore(st)) {
				Matcher m = TRANS.matcher(clean(l));
				if (m.find()) {
					x = Integer.parseInt(m.group(1));
					break;
				}
			}
		} catch (Throwable ignored) {
		}
		boolean keep;
		if (n.startsWith("[치장도구]")) {
			keep = x >= 1;
		} else if (n.startsWith("[편의]")) {
			keep = true;
		} else if (n.startsWith("[채집도구]") || n.startsWith("[낚시]")) {
			Matcher m = LV.matcher(n);
			int lv = -1;
			if (m.find()) {
				lv = m.group(1).equalsIgnoreCase("max") ? 10 : Integer.parseInt(m.group(1));
			}
			keep = n.startsWith("[채집도구]") ? lv >= 8 : lv >= 7;
		} else {
			keep = false;
		}
		if (!keep) {
			return null;
		}
		JsonObject o = new JsonObject();
		o.addProperty("n", n);
		if (x > 0) {
			o.addProperty("x", x);
		}
		String m = null;
		try {
			Object model = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
			if (model != null) {
				m = String.valueOf(model);
			}
		} catch (Throwable ignored) {
		}
		if (m == null) {
			try {
				var id = LunaCompat.getItemId(st.getItem());
				m = id == null ? null : id.toString();
			} catch (Throwable ignored) {
			}
		}
		if (m != null) {
			o.addProperty("m", m);
		}
		if (st.getCount() > 1) {
			o.addProperty("c", st.getCount());
		}
		return o;
	}

	private static String sigOf(JsonArray a) {
		List<String> l = new ArrayList<>();
		for (var e : a) {
			JsonObject o = e.getAsJsonObject();
			l.add(o.get("n").getAsString() + "|" + (o.has("x") ? o.get("x").getAsInt() : 0) + "|" + (o.has("c") ? o.get("c").getAsInt() : 1));
		}
		java.util.Collections.sort(l);
		return String.join(";", l);
	}

	/** 매 틱(연동돼 있고 너굴마을일 때). */
	public static void tick(Minecraft mc, Sink sink) {
		try {
			if (mc == null || mc.player == null || mc.level == null) {
				return;
			}
			long now = System.currentTimeMillis();
			watchCommands(mc, now);
			Object conn = mc.getConnection();
			if (conn != lastConn) {
				lastConn = conn;   // 서버에 새로 들어옴(이동 포함) - 4초 뒤 처음 신호
				joinAt = now + 4000;
				initPending = true;
			}
			if (mc.level != lastLevel) {
				lastLevel = mc.level;
				// 우리 섬: 방금 친 명령어로 판단
				if (pendCmd != null && now - pendCmdAt < 30_000) {
					island = pendCmd.equals("home") ? Island.YES : Island.NO;
				} else {
					island = Island.UNKNOWN;
				}
				pendCmd = null;
			}
			activity(mc, now);
			// ---- 몸 ----
			var inv = mc.player.getInventory();
			JsonArray invArr = new JsonArray();
			int h = -1;
			int sel = LunaCompat.selectedSlot(mc.player);
			for (int i = 0; i < 36; i++) {
				JsonObject o = item(inv.getItem(i));
				if (o != null) {
					if (i == sel) {
						h = invArr.size();
					}
					invArr.add(o);
				}
			}
			JsonObject off = item(mc.player.getOffhandItem());
			if (off != null) {
				invArr.add(off);
			}
			JsonObject cur = item(mc.player.containerMenu.getCarried());
			if (cur != null) {
				invArr.add(cur);
			}
			// ---- 보관함 ----
			JsonObject box = box(mc, now);
			String sig = sigOf(invArr) + "#" + (box == null ? "" : box.get("id").getAsString() + ":" + sigOf(box.getAsJsonArray("it")));
			JsonObject act = actJson();
			String actSig = act.get("k").getAsString() + "|" + (afk ? 1 : 0);

			if (initPending) {
				if (now < joinAt) {
					return;
				}
				initPending = false;
				send(sink, invArr, h, act, box, true, now);
				sentSig = sig;
				sentH = h;
				sentAct = actSig;
				return;
			}
			boolean doSend = false;
			// 보관함을 막 열었으면 내용을 받자마자(0.25초 뒤) 한 번
			if (box != null && boxSentSig == null && now - boxOpenedAt > 250) {
				doSend = true;
			}
			// 구성이 바뀜: 0.4초 모아서
			if (!sig.equals(sentSig)) {
				if (!sig.equals(pendSig)) {
					pendSig = sig;
					pendSince = now;
				} else if (now - pendSince > 400) {
					doSend = true;
				}
			} else {
				pendSig = null;
			}
			// 활동, 자리 비움이 바뀜: 바로(1분에 2번까지)
			if (!actSig.equals(sentAct) && now - actSends[0] > 60_000) {
				doSend = true;
			}
			// 손에 든 것만 바뀜: 3초 그대로, 30초에 한 번
			if (h != handSeen) {
				handSeen = h;
				handSince = now;
			}
			if (h != sentH && now - handSince > 3000 && now - lastHandSend > 30_000) {
				doSend = true;
				lastHandSend = now;
			}
			// 5분마다(가진 동안)
			if (invArr.size() > 0 && now - lastBeat > 5 * 60_000) {
				doSend = true;
			}
			if (!doSend) {
				return;
			}
			if (!actSig.equals(sentAct)) {
				actSends[0] = actSends[1];
				actSends[1] = now;
			}
			send(sink, invArr, h, act, box, false, now);
			sentSig = sig;
			sentH = h;
			sentAct = actSig;
			pendSig = null;
			emptySent = invArr.size() == 0;
			if (box != null) {
				boxSentSig = sig;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("gear:tick", t);
		}
	}

	// ---- 우리 섬: 채팅창으로 보낸 명령어 ----
	private static void watchCommands(Minecraft mc, long now) {
		try {
			Object chat = LunaCompat.callNoArg(mc.gui, "getChat");   // 26.3에서 자리가 바뀌어 리플렉션으로
			if (chat == null) {
				chat = LunaCompat.getFieldValue(mc.gui, "chat");
			}
			Object rc = chat == null ? null : LunaCompat.callNoArg(chat, "getRecentChat");
			if (!(rc instanceof java.util.List<?> recent)) {
				return;
			}
			int size = recent.size();
			String last = size == 0 ? null : String.valueOf(recent.get(size - 1));
			if (recentSize >= 0 && (size != recentSize || (last != null && !last.equals(recentLast)))) {
				String c = last == null ? "" : last.trim();
				if (c.equals("/섬") || c.equals("/섬 이동") || c.equals("/섬 홈")) {
					pendCmd = "home";
					pendCmdAt = now;
				} else if (c.startsWith("/섬 방문") || c.startsWith("/섬 알바이동") || c.startsWith("/섬 워프") || c.startsWith("/스폰")
						|| c.startsWith("/워프") || c.startsWith("/티피") || c.startsWith("/비행티켓")) {
					pendCmd = "other";
					pendCmdAt = now;
				}
			}
			recentSize = size;
			recentLast = last;
		} catch (Throwable t) {
			LunaCompat.warnOnce("gear:cmd", t);
		}
	}

	// ---- 활동 ----
	private static void activity(Minecraft mc, long now) {
		if (mc.player.fishing != null) {
			lastFish = now;
		}
		String k;
		if (now - lastMine < 120_000) {
			k = "mine";
		} else if (now - lastFish < 120_000) {
			k = "fish";
		} else if (kr.lunaslight.mod.module.impl.render.AfkModule.isAfkNow()) {
			k = "afk";
		} else {
			k = "etc";
		}
		if (!k.equals(kind)) {
			kindEnd.put(kind, now);
			Long end = kindEnd.get(k);
			if (end == null || now - end > 5 * 60_000) {
				kindStart = now;
				afkSum = 0;
			}
			kind = k;
		}
		boolean a = kr.lunaslight.mod.module.impl.render.AfkModule.isAfkNow();
		if (a != afk) {
			if (a) {
				afkStart = now;
			} else if (afkStart > 0) {
				afkSum += now - afkStart;
				afkStart = 0;
			}
			afk = a;
		}
	}

	private static JsonObject actJson() {
		JsonObject a = new JsonObject();
		a.addProperty("k", kind);
		a.addProperty("s", kindStart);
		a.addProperty("f", afk ? afkStart : 0);
		a.addProperty("fm", afkSum);
		return a;
	}

	// ---- 보관함 ----
	private static JsonObject box(Minecraft mc, long now) {
		Object scr = LunaCompat.screenOf(mc);
		if (!(scr instanceof AbstractContainerScreen<?> hs) || scr instanceof InventoryScreen) {
			boxScreen = null;
			boxSentSig = null;
			return null;
		}
		if (scr != boxScreen) {
			boxScreen = scr;
			boxOpenedAt = now;
			boxSentSig = null;
		}
		String title = clean(hs.getTitle().getString());
		String k, id, nm;
		if (title.contains("엔더")) {
			k = "my";
			id = "ender";
			nm = "엔더 상자";
		} else if (title.contains("창고") && !title.contains("광물") && !title.contains("귀속")) {
			k = "my";
			id = "gui:" + title;
			nm = title;
		} else {
			BlockPos pos = kr.lunaslight.mod.module.impl.inventory.ItemFinderModule.usedPosRecent(3000);
			if (pos == null || island != Island.YES) {
				return null;   // 우리 섬 상자인지 모름 - 안 보냄
			}
			BlockState st = mc.level.getBlockState(pos);
			String bs = String.valueOf(st);
			if (!(bs.contains("chest") || bs.contains("barrel") || bs.contains("shulker_box"))) {
				return null;
			}
			pos = chestMin(mc, pos, st);
			k = "isl";
			id = dimension(mc) + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
			nm = title.isEmpty() ? "상자" : title;
		}
		var slots = hs.getMenu().slots;
		int n = Math.max(0, slots.size() - 36);
		JsonArray it = new JsonArray();
		for (int i = 0; i < n; i++) {
			JsonObject o = item(slots.get(i).getItem());
			if (o != null) {
				it.add(o);
			}
		}
		JsonObject b = new JsonObject();
		b.addProperty("k", k);
		b.addProperty("id", id);
		b.addProperty("nm", nm);
		b.add("it", it);
		return b;
	}

	/** 큰 상자는 두 칸 중 좌표가 작은 쪽. */
	private static BlockPos chestMin(Minecraft mc, BlockPos pos, BlockState st) {
		try {
			if (st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
					&& st.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
				BlockPos o = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(st));
				if (o.getX() < pos.getX() || (o.getX() == pos.getX() && (o.getY() < pos.getY() || (o.getY() == pos.getY() && o.getZ() < pos.getZ())))) {
					return o;
				}
			}
		} catch (Throwable ignored) {
		}
		return pos;
	}

	private static String dimension(Minecraft mc) {
		String s = String.valueOf(mc.level.dimension());   // ResourceKey[minecraft:dimension / minecraft:overworld]
		int i = s.lastIndexOf(" / ");
		if (i >= 0) {
			s = s.substring(i + 3).replace("]", "").trim();
		}
		return s;
	}

	// ---- 보내기(길면 나눔) ----
	private static void send(Sink sink, JsonArray inv, int h, JsonObject act, JsonObject box, boolean ini, long now) {
		lastBeat = now;
		String boxKey = box == null ? "" : box.get("id").getAsString();
		List<JsonObject> pages = new ArrayList<>();
		JsonObject first = head(now, ini, h, act);
		first.add("inv", new JsonArray());
		if (box != null) {
			first.add("box", boxHead(box));
		}
		pages.add(first);
		// 몸 → 상자 순서로 넣다가 1700자를 넘으면 다음 조각
		List<JsonObject[]> items = new ArrayList<>();
		for (var e : inv) {
			items.add(new JsonObject[]{e.getAsJsonObject(), null});
		}
		if (box != null) {
			for (var e : box.getAsJsonArray("it")) {
				items.add(new JsonObject[]{null, e.getAsJsonObject()});
			}
		}
		JsonObject page = first;
		for (JsonObject[] it : items) {
			JsonObject o = it[0] != null ? it[0] : it[1];
			if (page.toString().length() + o.toString().length() > 1700 && pages.size() < 8) {
				page = new JsonObject();
				page.addProperty("ev", "gear");
				page.addProperty("t", now);
				page.add("inv", new JsonArray());
				pages.add(page);
			}
			if (it[0] != null) {
				page.getAsJsonArray("inv").add(o);
			} else {
				if (!page.has("box")) {
					page.add("box", boxHead(box));
				}
				page.getAsJsonObject("box").getAsJsonArray("it").add(o);
			}
		}
		if (pages.size() > 1) {
			for (int i = 0; i < pages.size(); i++) {
				JsonArray pg = new JsonArray();
				pg.add(i + 1);
				pg.add(pages.size());
				pages.get(i).add("pg", pg);
			}
		}
		sink.gear(pages, boxKey);
	}

	private static JsonObject head(long now, boolean ini, int h, JsonObject act) {
		JsonObject o = new JsonObject();
		o.addProperty("ev", "gear");
		o.addProperty("t", now);
		if (ini) {
			o.addProperty("ini", 1);
		}
		o.addProperty("h", h);
		o.add("a", act);
		return o;
	}

	private static JsonObject boxHead(JsonObject box) {
		JsonObject b = new JsonObject();
		b.addProperty("k", box.get("k").getAsString());
		b.addProperty("id", box.get("id").getAsString());
		b.addProperty("nm", box.get("nm").getAsString());
		b.add("it", new JsonArray());
		return b;
	}
}
