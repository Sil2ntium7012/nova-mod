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
 * 49-608차: 서버 광산 획득 알림 줄(보스바 글자 ng:font/hud/mine/*, 최근 3줄, 흐려짐 _f1/_f2)을 읽어 <b>새로 생긴 줄</b>만 골라 알린다.
 *
 * <p>49-649차(사용자: "예측으로 하면 안 돼, 서버에서 나오는 로그(화면 알림)를 토대로, 시간 계산해서 새로 올라온 것만"): 캐기 수, 옆 칸 공기 같은
 * 짐작(49-612, 49-630)은 다 뺐다. 오직 서버가 보낸 알림 줄만 본다.
 * <ul>
 *   <li>줄은 새것부터 줄 세운다(새 줄이 위에 붙는지 아래에 붙는지는 한 줄 → 두 줄이 될 때 배움, 모르면 위).</li>
 *   <li>앞에 k줄이 새로 붙었다고 볼 수 있는 가장 작은 k: 남은 줄은 지난 줄과 아이템, 개수가 같고 흐려짐이 줄지 않아야 하고, 새 줄은 흐려짐 0.</li>
 *   <li>시간: 줄마다 처음 본 때를 기억한다. 줄은 약 3초 뒤 흐려지기 시작하므로, 3.5초 넘게 지난 줄이 아직 흐려짐 0이면 서버가 새로 올린 것
 *       (같은 줄이 다시 와서 새로 고쳐진 것) - 거기까지 새 줄로 센다.</li>
 *   <li>서버가 글을 다시 보냈는데(새 이름) 내용이 똑같으면(돌 1개 셋 그대로) 새 줄 하나가 올라온 것으로 센다(알림이 바뀔 일이 그것뿐).</li>
 * </ul>
 * 바뀔 때마다 config/lunaslight/neogul-mine-probe.txt에 적는다(300KB가 차면 .old로 넘김).
 */
public final class NeogulMineFeed {
	private NeogulMineFeed() {
	}

	/** 새로 얻은 것(아이템 그림 이름, 개수)을 받는 곳(NeogulMineModule). */
	public static volatile BiConsumer<String, Integer> listener;

	private static List<NeogulDungeonHud.MineRow> prev;
	/** prev 줄마다(새것부터) 처음 본 때. */
	private static List<Long> births = new ArrayList<>();
	private static Boolean newestTop;
	private static long lastSameAt;
	/** 한 번 새로 올라온 줄이 흐려지기 시작하기까지(실측 약 3초) + 여유. 이보다 오래된 줄이 아직 흐려짐 0이면 새로 고쳐진 것. */
	private static final long FRESH_MS = 3500;

	/** 49-649차: 짐작은 뺐다 - 믹스인이 부르는 자리만 남겨 둔다. */
	public static void onBreak() {
	}

	public static void onBreak(net.minecraft.core.BlockPos pos) {
	}

	public static void onBlockGone(net.minecraft.core.BlockPos pos) {
	}

	/** 매 틱(NeogulMineModule): 액션바/타이틀의 광산 알림 숨기기, 가끔 상태 기록. */
	public static void tick(net.minecraft.client.Minecraft mc, boolean hide) {
		long now = System.currentTimeMillis();
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
	/** 앞에 k줄이 새로 붙고 나머지는 지난 목록 앞부분과 같은 줄(흐려짐은 그대로이거나 더 흐려짐), 새 줄은 흐려짐 0인가. 둘 다 새것 쪽부터. */
	private static boolean fits(List<NeogulDungeonHud.MineRow> cur, List<NeogulDungeonHud.MineRow> old, int k) {
		int keep = cur.size() - k;
		if (keep < 0 || keep > old.size()) {
			return false;
		}
		for (int i = 0; i < k; i++) {
			if (cur.get(i).frame() != 0) {
				return false;
			}
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

	/** 49-608차 이름 그대로(예전 호출). */
	public static void onRows(List<NeogulDungeonHud.MineRow> rows) {
		onRows(rows, true);
	}

	/** NeogulDungeonHud.onBossUpdate 끝에서(이번 보스바들에서 모은 광산 줄, 위 → 아래). freshName = 서버가 광산 줄 글을 새로 보냄. */
	public static void onRows(List<NeogulDungeonHud.MineRow> rows, boolean freshName) {
		try {
			long now = System.currentTimeMillis();
			List<NeogulDungeonHud.MineRow> cur = new ArrayList<>(rows);
			if (prev == null) {
				prev = cur;   // 들어온 뒤 처음 본 것은 이미 있던 것
				births = new ArrayList<>(Collections.nCopies(cur.size(), now));
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
			boolean sameText = sigs(cur).equals(sigs(prev));
			if (sameText && !freshName) {
				return;   // 글이 안 왔다(보스바 다른 값만 바뀜)
			}
			int k = c.size();
			for (int t = Math.max(0, c.size() - o.size()); t <= c.size(); t++) {
				if (fits(c, o, t)) {
					k = t;
					break;
				}
			}
			String why = "";
			// 시간: 남은 줄 중 오래됐는데 아직 흐려짐 0인 줄 = 새로 고쳐짐 → 거기까지 새 줄
			for (int i = k; i < c.size(); i++) {
				int oi = i - k;
				long born = oi < births.size() ? births.get(oi) : now;
				if (c.get(i).frame() == 0 && now - born > FRESH_MS) {
					k = i + 1;
					why = " age";
				}
			}
			// 같은 글이 다시 왔다 = 새 줄 하나(같은 것)가 올라옴
			if (k == 0 && sameText && freshName && !c.isEmpty() && c.get(0).frame() == 0 && now - lastSameAt > 120) {
				k = 1;
				why = " same";
				lastSameAt = now;
			}
			probe(cur, k, topNew, why);
			List<Long> nb = new ArrayList<>();
			for (int i = 0; i < c.size(); i++) {
				int oi = i - k;
				nb.add(i < k ? now : (oi < births.size() ? births.get(oi) : now));
			}
			births = nb;
			prev = cur;
			BiConsumer<String, Integer> l = listener;
			if (l != null) {
				for (int i = k - 1; i >= 0; i--) {   // 오래된 것부터
					l.accept(c.get(i).item(), Math.max(1, c.get(i).count()));
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("mineFeed", t);
		}
	}

	/** 다른 서버로 가거나 다시 들어오면 처음부터. */
	public static void reset() {
		prev = null;
		births = new ArrayList<>();
	}

	private static long probeSize = -1;

	private static void probe(List<NeogulDungeonHud.MineRow> cur, int k, boolean top, String why) {
		probeLine("new=" + k + why + (top ? " top" : " bottom") + " | " + sigs(cur));
	}

	private static void probeLine(String text) {
		try {
			Path f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-mine-probe.txt");
			if (probeSize < 0) {
				probeSize = Files.exists(f) ? Files.size(f) : 0;
			}
			if (probeSize > 300_000) {
				// 49-648차: 다 차면 멈추던 것 → 옛 것을 .old로 넘기고 새로 적는다(최근 기록이 늘 남게)
				Files.move(f, f.resolveSibling("neogul-mine-probe.old.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				probeSize = 0;
			}
			String line = java.time.LocalTime.now().withNano(0) + " " + text + "\n";
			Files.createDirectories(f.getParent());
			Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			probeSize += line.getBytes(StandardCharsets.UTF_8).length;
		} catch (Throwable ignored) {
		}
	}
}
