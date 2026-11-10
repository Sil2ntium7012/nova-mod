package kr.lunaslight.mod.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 49-608차(사용자 사진: 광산에서 캐면 인벤토리가 아니라 광물 창고로 바로 들어가고 화면 가운데 오른쪽에 "돌 1개 +"만 떠서 아이템 획득이 안 뜸):
 * 서버 광산 획득 알림 줄(보스바 글자 ng:font/hud/mine/*, 최근 3줄, 흐려짐 _f1/_f2)을 읽어 <b>새로 생긴 줄</b>만 골라 알린다.
 *
 * <p>줄은 위에서부터 0, 1, 2(글꼴 높이로 나뉨). 새 줄이 위에 붙는지 아래에 붙는지는 처음 한 줄 → 두 줄이 될 때 보고 정한다(모르면 위).
 * 새것 쪽부터 줄 세운 지난 목록과 지금 목록을 맞춰 보아, 앞에 k줄이 새로 붙었다고 볼 수 있는 가장 작은 k를 고른다
 * (같은 줄은 아이템과 개수가 같고 흐려짐이 줄지 않아야 한다). 모두 같은 줄이라 구별이 안 되면 방금 블록을 캤을 때만 한 줄 새것으로 본다.
 * 바뀔 때마다 config/lunaslight/neogul-mine-probe.txt(300KB까지)에 적는다 - 모양이 생각과 다르면 고치려고.
 */
public final class NeogulMineFeed {
	private NeogulMineFeed() {
	}

	/** 새로 얻은 것(아이템 그림 이름, 개수)을 받는 곳(NeogulMineModule). */
	public static volatile BiConsumer<String, Integer> listener;

	private static List<NeogulDungeonHud.MineRow> prev;
	private static Boolean newestTop;
	private static long breakAt;
	private static boolean breakUsed = true;
	/**
	 * 49-612차(사용자 사진: 돌만 계속 캐서 알림 3줄이 "돌 1개"로 그대로면 더 들어오는 걸 못 셈): 서버가 똑같은 줄을 다시 보내면(또는 안 보내면)
	 * 줄만으로는 새로 얻은 걸 알 수 없다. 광산에서 블록을 캔 때를 쌓아 두고, 줄 변화로 설명되지 않은 캐기는 0.9초 뒤 가장 새 줄(같은 광물)로 센다.
	 * 그렇게 센 것은 잠깐 기억해 두었다가(credit) 늦게 온 줄 변화가 같은 것을 또 세지 않게 한다.
	 */
	private static final java.util.ArrayDeque<Long> breaks = new java.util.ArrayDeque<>();
	private static final java.util.ArrayDeque<Object[]> credit = new java.util.ArrayDeque<>();

	/** 블록을 캤다(TodoGameModeMixin). */
	public static void onBreak() {
		breakAt = System.currentTimeMillis();
		breakUsed = false;
		if ("mine".equals(NeogulDungeonHud.location)) {
			synchronized (breaks) {
				breaks.addLast(breakAt);
				while (breaks.size() > 20) {
					breaks.pollFirst();
				}
			}
		}
	}

	private static NeogulDungeonHud.MineRow newest() {
		if (prev == null || prev.isEmpty()) {
			return null;
		}
		boolean topNew = newestTop == null || newestTop;
		return topNew ? prev.get(0) : prev.get(prev.size() - 1);
	}

	/** 매 틱(NeogulMineModule): 설명 안 된 캐기 세기, 액션바/타이틀의 광산 알림 숨기기, 가끔 상태 기록. */
	public static void tick(net.minecraft.client.Minecraft mc, boolean hide) {
		long now = System.currentTimeMillis();
		synchronized (credit) {
			while (!credit.isEmpty() && now - (Long) credit.peekFirst()[1] > 3000) {
				credit.pollFirst();
			}
		}
		List<Long> due = new ArrayList<>();
		synchronized (breaks) {
			while (!breaks.isEmpty() && now - breaks.peekFirst() > 900) {
				due.add(breaks.pollFirst());
			}
		}
		for (Long t : due) {
			NeogulDungeonHud.MineRow r = newest();
			if (r == null || r.frame() > 0 || now - t > 3000 || !"mine".equals(NeogulDungeonHud.location)) {
				continue;
			}
			synchronized (credit) {
				credit.addLast(new Object[]{r.sig(), now});
			}
			probeLine("break-count " + r.sig());
			BiConsumer<String, Integer> l = listener;
			if (l != null) {
				l.accept(r.item(), Math.max(1, r.count()));
			}
		}
		if (hide && mc != null && mc.gui != null) {
			for (String f : new String[]{"overlayMessageString", "title", "subtitle"}) {
				hideField(mc.gui, f);
			}
		}
		if (now - diagAt > 10_000 && prev != null && !prev.isEmpty() && mc != null) {
			diagAt = now;
			diag(mc, hide);
		}
	}

	private static long diagAt;
	private static final java.util.Map<String, Object> HIDDEN = new java.util.HashMap<>();

	private static void hideField(Object gui, String name) {
		try {
			java.lang.reflect.Field f = LunaCompat.findField(gui.getClass(), name);
			if (f == null) {
				return;
			}
			f.setAccessible(true);
			Object v = f.get(gui);
			if (!(v instanceof net.minecraft.network.chat.Component c) || v == HIDDEN.get(name)) {
				return;
			}
			if (NeogulDungeonHud.mineRows(c).isEmpty()) {
				return;
			}
			net.minecraft.network.chat.Component h = NeogulDungeonHud.hideMineIn(c);
			f.set(gui, h);
			HIDDEN.put(name, h);
		} catch (Throwable t) {
			LunaCompat.warnOnce("mineFeed:hide:" + name, t);
		}
	}

	/** 광산 알림이 어디(보스바 몇 번, 액션바, 타이틀)에 아직 보이는지 기록 - 숨김이 안 먹을 때 원인 찾기. */
	private static void diag(net.minecraft.client.Minecraft mc, boolean hide) {
		try {
			StringBuilder sb = new StringBuilder("diag hide=" + hide);
			Object gui = mc.gui;
			for (String f : new String[]{"overlayMessageString", "title", "subtitle"}) {
				Object v = LunaCompat.getFieldValue(gui, f);
				if (v instanceof net.minecraft.network.chat.Component c && !NeogulDungeonHud.mineRows(c).isEmpty()) {
					sb.append(' ').append(f).append("=보임");
				}
			}
			Object overlay = LunaCompat.getFieldValue(gui, "bossOverlay");
			Object events = overlay == null ? null : LunaCompat.getFieldValue(overlay, "events");
			if (events instanceof java.util.Map<?, ?> m) {
				int i = 0;
				for (Object o : m.values()) {
					if (o instanceof net.minecraft.world.BossEvent ev && !NeogulDungeonHud.mineRows(ev.getName()).isEmpty()) {
						sb.append(" boss").append(i).append("=보임");
					}
					i++;
				}
				sb.append(" bosses=").append(m.size());
			}
			probeLine(sb.toString());
		} catch (Throwable t) {
			LunaCompat.warnOnce("mineFeed:diag", t);
		}
	}

	private static String sigs(List<NeogulDungeonHud.MineRow> l) {
		StringBuilder sb = new StringBuilder();
		for (NeogulDungeonHud.MineRow r : l) {
			sb.append(r.row()).append(':').append(r.sig()).append('/').append(r.frame()).append(' ');
		}
		return sb.toString().trim();
	}

	private static boolean same(List<NeogulDungeonHud.MineRow> a, List<NeogulDungeonHud.MineRow> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!a.get(i).sig().equals(b.get(i).sig())) {
				return false;
			}
		}
		return true;
	}

	/** 앞에 k줄이 새로 붙고 나머지는 지난 목록 앞부분과 같은 줄(흐려짐은 그대로이거나 더 흐려짐)인가. 둘 다 새것 쪽부터. */
	private static boolean fits(List<NeogulDungeonHud.MineRow> cur, List<NeogulDungeonHud.MineRow> old, int k) {
		int keep = cur.size() - k;
		if (keep < 0 || keep > old.size()) {
			return false;
		}
		for (int i = 0; i < keep; i++) {
			var c = cur.get(k + i);
			var o = old.get(i);
			if (!c.sig().equals(o.sig()) || c.frame() < o.frame()) {
				return false;
			}
		}
		return true;
	}

	/** NeogulDungeonHud.onBossUpdate 끝에서(이번 보스바들에서 모은 광산 줄, 위 → 아래). */
	public static void onRows(List<NeogulDungeonHud.MineRow> rows) {
		try {
			List<NeogulDungeonHud.MineRow> cur = new ArrayList<>(rows);
			if (prev == null) {
				prev = cur;   // 들어온 뒤 처음 본 것은 이미 있던 것
				return;
			}
			if (sigs(cur).equals(sigs(prev))) {
				return;
			}
			// 위/아래 방향 배우기: 한 줄 → 두 줄
			if (newestTop == null && prev.size() >= 1 && cur.size() == prev.size() + 1) {
				boolean top = same(cur.subList(1, cur.size()), prev);
				boolean bottom = same(cur.subList(0, cur.size() - 1), prev);
				if (top != bottom) {
					newestTop = top;
				}
			}
			boolean topNew = newestTop == null || newestTop;
			List<NeogulDungeonHud.MineRow> c = new ArrayList<>(cur), o = new ArrayList<>(prev);
			if (!topNew) {
				Collections.reverse(c);
				Collections.reverse(o);
			}
			int k = c.size();
			for (int t = Math.max(0, c.size() - o.size()); t <= c.size(); t++) {
				if (fits(c, o, t)) {
					k = t;
					break;
				}
			}
			long now = System.currentTimeMillis();
			if (k == 0 && !c.isEmpty() && !breakUsed && now - breakAt < 1500 && fits(c, o, 1)) {
				k = 1;   // 모두 같은 줄이라 구별이 안 됨 - 방금 캤으니 한 줄 새것
			}
			if (k > 0) {
				breakUsed = true;
			}
			probe(cur, k, topNew);
			prev = cur;
			synchronized (breaks) {
				for (int i = 0; i < k && !breaks.isEmpty(); i++) {
					breaks.pollFirst();   // 이 캐기들은 줄 변화로 셌다
				}
			}
			BiConsumer<String, Integer> l = listener;
			if (l != null) {
				for (int i = k - 1; i >= 0; i--) {   // 오래된 것부터
					String sig = c.get(i).sig();
					boolean already = false;
					synchronized (credit) {
						for (var it = credit.iterator(); it.hasNext(); ) {
							if (sig.equals(it.next()[0])) {
								it.remove();
								already = true;   // 캐기로 미리 센 것
								break;
							}
						}
					}
					if (!already) {
						l.accept(c.get(i).item(), Math.max(1, c.get(i).count()));
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("mineFeed", t);
		}
	}

	/** 다른 서버로 가거나 다시 들어오면 처음부터. */
	public static void reset() {
		prev = null;
	}

	private static long probeSize = -1;

	private static void probe(List<NeogulDungeonHud.MineRow> cur, int k, boolean top) {
		probeLine("new=" + k + (top ? " top" : " bottom") + " | " + sigs(cur));
	}

	private static void probeLine(String text) {
		try {
			Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-mine-probe.txt");
			if (probeSize < 0) {
				probeSize = Files.exists(f) ? Files.size(f) : 0;
			}
			if (probeSize > 300_000) {
				return;
			}
			String line = java.time.LocalTime.now().withNano(0) + " " + text + "\n";
			Files.createDirectories(f.getParent());
			Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			probeSize += line.getBytes(StandardCharsets.UTF_8).length;
		} catch (Throwable ignored) {
		}
	}
}
