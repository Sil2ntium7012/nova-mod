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
	private static final java.util.ArrayDeque<Brk> breaks = new java.util.ArrayDeque<>();
	private static final java.util.ArrayDeque<Object[]> credit = new java.util.ArrayDeque<>();

	/**
	 * 49-630차(사용자: "곡괭이 2칸씩 캐는데 들어오는 게 1개씩이고 추가 드롭도 무시해"): 한 번 캘 때 서버가 옆 칸도 같이 부수면(범위 곡괭이)
	 * 그 칸은 서버에서 "공기가 됨"으로만 온다. 캔 자리 둘레(2칸 안)에서 1.2초 안에 공기가 된 칸을 그 캐기에 더해(blocks) 알림 줄 수와 맞춘다.
	 */
	private static final class Brk {
		final long at;
		final long x, y, z;
		int blocks = 1;
		final java.util.Set<Long> seen = new java.util.HashSet<>();

		Brk(long at, long x, long y, long z) {
			this.at = at;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	/** 블록을 캤다(TodoGameModeMixin). */
	public static void onBreak() {
		onBreak(null);
	}

	public static void onBreak(net.minecraft.core.BlockPos pos) {
		breakAt = System.currentTimeMillis();
		breakUsed = false;
		if ("mine".equals(NeogulDungeonHud.location)) {
			synchronized (breaks) {
				Brk b = pos == null ? new Brk(breakAt, Long.MIN_VALUE, 0, 0) : new Brk(breakAt, pos.getX(), pos.getY(), pos.getZ());
				if (pos != null) {
					b.seen.add(pos.asLong());
				}
				breaks.addLast(b);
				while (breaks.size() > 20) {
					breaks.pollFirst();
				}
			}
		}
	}

	/** 49-630차: 서버가 칸을 공기로 바꿨다(NeogulBlockGoneMixin, 메인 스레드). 방금 캔 자리 옆이면 그 캐기에 한 칸 더. */
	public static void onBlockGone(net.minecraft.core.BlockPos pos) {
		if (pos == null || !"mine".equals(NeogulDungeonHud.location)) {
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (breaks) {
			for (var it = breaks.descendingIterator(); it.hasNext(); ) {
				Brk b = it.next();
				if (now - b.at > 1200) {
					break;
				}
				if (b.x == Long.MIN_VALUE) {
					continue;
				}
				if (Math.abs(pos.getX() - b.x) <= 2 && Math.abs(pos.getY() - b.y) <= 2 && Math.abs(pos.getZ() - b.z) <= 2) {
					if (b.seen.add(pos.asLong()) && b.blocks < 9) {
						b.blocks++;
					}
					return;
				}
			}
		}
	}

	/** 아직 줄 변화로 안 센 캔 칸 수(최근 1.5초). */
	private static int pendingBlocks(long now) {
		int n = 0;
		synchronized (breaks) {
			for (Brk b : breaks) {
				if (now - b.at < 1500) {
					n += b.blocks;
				}
			}
		}
		return n;
	}

	/** 줄 변화로 k칸을 셌다 - 오래된 캐기부터 그만큼 지운다. */
	private static void consumeBlocks(int k) {
		synchronized (breaks) {
			while (k > 0 && !breaks.isEmpty()) {
				Brk b = breaks.peekFirst();
				int use = Math.min(k, b.blocks);
				b.blocks -= use;
				k -= use;
				if (b.blocks <= 0) {
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
		List<Brk> due = new ArrayList<>();
		synchronized (breaks) {
			while (!breaks.isEmpty() && now - breaks.peekFirst().at > 1300) {   // 49-630차: 옆 칸 소식을 기다리려고 0.9 → 1.3초
				due.add(breaks.pollFirst());
			}
		}
		for (Brk t : due) {
			NeogulDungeonHud.MineRow r = newest();
			if (r == null || r.frame() > 0 || now - t.at > 3000 || !"mine".equals(NeogulDungeonHud.location)) {
				continue;
			}
			for (int i = 0; i < t.blocks; i++) {   // 49-630차: 같이 부서진 칸만큼
				synchronized (credit) {
					credit.addLast(new Object[]{r.sig(), now});
				}
				probeLine("break-count " + r.sig() + (t.blocks > 1 ? " (" + (i + 1) + "/" + t.blocks + ")" : ""));
				BiConsumer<String, Integer> l = listener;
				if (l != null) {
					l.accept(r.item(), Math.max(1, r.count()));
				}
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
			// 49-630차: 줄이 모두 같은 것(돌 1개 셋)이라 몇 줄이 새로 붙었는지 모를 때는 방금 같이 부서진 칸 수만큼(가장 작은 k보다 크면)
			if (k > 0 && allSame(c, o)) {
				int pb = pendingBlocks(now);
				for (int t = Math.min(c.size(), pb); t > k; t--) {
					if (fits(c, o, t)) {
						k = t;
						break;
					}
				}
			}
			if (k > 0) {
				breakUsed = true;
			}
			probe(cur, k, topNew);
			prev = cur;
			consumeBlocks(k);   // 이 칸들은 줄 변화로 셌다(49-630차: 캐기 하나에 여러 칸)
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

	/** 지금 줄과 지난 줄이 모두 같은 것(아이템, 개수)인가. */
	private static boolean allSame(List<NeogulDungeonHud.MineRow> c, List<NeogulDungeonHud.MineRow> o) {
		String sig = null;
		for (var l : List.of(c, o)) {
			for (var r : l) {
				if (sig == null) {
					sig = r.sig();
				} else if (!sig.equals(r.sig())) {
					return false;
				}
			}
		}
		return sig != null;
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
