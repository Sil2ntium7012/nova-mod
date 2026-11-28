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
	/**
	 * 49-678차(사용자: "2개씩 캤는데 3번째 뒤로 갑자기 1개만 찍힘"): 기록을 보면 서버는 알림 줄을 바꿀 때마다 같은 글을 한 번 더 보낸다
	 * (줄이 덜 찼을 때도 "같은 글 다시 옴"이 바뀐 글 바로 뒤에 늘 따라온다). 그래서
	 * <ul>
	 *   <li>줄이 덜 찼거나 줄이 서로 다르면 같은 글이 다시 온 것은 새 줄이 아니다(새 줄이면 줄 수나 순서가 바뀐다).</li>
	 *   <li>3줄이 꽉 차고 모두 같은 줄(돌 1개 x3)이면 바뀐 걸 볼 수 없다 - 같은 글 두 번(바뀜 + 다시 보냄)을 한 번으로 보고,
	 *       그 아이템이 한 번에 몇 줄씩 올라왔는지(덜 찼을 때 배운 값, 돌은 2줄) 만큼 센다.</li>
	 * </ul>
	 */
	private static boolean expectEcho;
	/**
	 * 49-815차(기록 재생으로 확인한 두 가지 겹쳐 세기):
	 * <ul>
	 *   <li>시간 규칙("오래됐는데 아직 흐려짐 0이면 새 줄")은 줄이 흐려지는 곳(광산)에서만 맞다. 섬에서는 줄이 전혀 흐려지지 않아
	 *       3.5초 넘게 쉬었다 캐면 남아 있던 줄을 또 셌다(사암 27% 더 셈). 최근 5분 안에 흐려진 줄을 본 적이 있을 때만 쓴다.</li>
	 *   <li>요리용 석탄, 보석 줄은 먼저 맨 위에 올라왔다가(2개) 0.6초 안에 같은 줄이 더 큰 개수(3개)로 다시 온다 - 따로 2 + 3으로 세지 않고 늘어난 1만 센다.</li>
	 * </ul>
	 */
	private static long fadeSeenAt;
	private static final java.util.Map<String, long[]> LAST_NG = new java.util.HashMap<>();   // 아이템 → {개수, 때}
	/**
	 * 49-715차(사용자 사진: 2칸씩 캐는데 광물 창고에 1개씩 쌓임, 기록: 돌만 3줄 꽉 찬 동안 "same x1"): 한 번에 올라오는 줄 수를 마지막 값 하나로,
	 * 그것도 거의 못 배워서(새 줄이 모두 같은 개수일 때만) 1로 셌다. 이제 줄 수가 하나로 정해질 때마다 최근 16번을 적어 두고
	 * 가장 많이 나온 값을 쓴다(같으면 큰 값). 곡괭이를 바꾸면 최근 값을 따라간다.
	 */
	private static final java.util.Map<String, java.util.ArrayDeque<Integer>> BATCH = new java.util.HashMap<>();
	private static final java.util.ArrayDeque<Integer> BATCH_ALL = new java.util.ArrayDeque<>();
	private static final int BATCH_KEEP = 16;
	private static final int MAX_ROWS = 3;

	private static void learnBatch(String item, int k, boolean sameItem) {
		push(BATCH_ALL, k);
		if (sameItem) {
			push(BATCH.computeIfAbsent(item, x -> new java.util.ArrayDeque<>()), k);
		}
	}

	private static void push(java.util.ArrayDeque<Integer> q, int k) {
		q.addLast(k);
		while (q.size() > BATCH_KEEP) {
			q.pollFirst();
		}
	}

	/** 이 아이템이 한 번에 보통 몇 줄씩 올라오는지(배운 게 3번 미만이면 모든 아이템 기준, 그것도 없으면 null). */
	private static Integer batchOf(String item) {
		// 줄 수는 곡괭이(한 번에 몇 칸 캐는지)가 정한다 - 아이템마다 따로 보면 광맥 가장자리(옆 칸이 빔)에서 배운 1이 많아 틀렸다(기록 재생 실측).
		// 그래서 모든 아이템의 최근 값으로 본다. 아이템 값은 모든 아이템 기록이 아직 없을 때만.
		java.util.ArrayDeque<Integer> q = BATCH_ALL;
		if (q.size() < 3) {
			java.util.ArrayDeque<Integer> own = BATCH.get(item);
			if (own != null && own.size() > q.size()) {
				q = own;
			}
		}
		int[] n = new int[4];
		for (int k : q) {
			n[Math.min(3, Math.max(1, k))]++;
		}
		int best = 0;
		for (int k = 1; k <= 3; k++) {
			if (n[k] > 0 && n[k] >= n[best]) {
				best = k;   // 같은 횟수면 큰 값
			}
		}
		return best == 0 ? null : best;
	}

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
			// 49-678차: 꽉 찬 3줄에서는 줄이 잘려 나가 몇 줄이 올라왔는지 가장 작은 값으로만 보인다(돌 2줄 → 1줄) -
			// 이 아이템이 한 번에 올라오는 줄 수(배운 값)도 맞으면 그 값으로
			if (k > 0 && c.size() >= MAX_ROWS) {
				Integer b = batchOf(c.get(0).item());
				if (b != null && b > k && b <= c.size() && fits(c, o, b)) {
					boolean one = true;
					for (int i = 1; i < b; i++) {
						one &= c.get(i).sig().equals(c.get(0).sig());
					}
					if (one) {
						k = b;
						why = " batch";
					}
				}
			}
			for (var r : c) {
				if (r.frame() > 0) {
					fadeSeenAt = now;
				}
			}
			// 시간: 남은 줄 중 오래됐는데 아직 흐려짐 0인 줄 = 새로 고쳐짐 → 거기까지 새 줄(줄이 흐려지는 곳에서만 - 49-815차)
			for (int i = k; i < c.size() && now - fadeSeenAt < 300_000; i++) {
				int oi = i - k;
				long born = oi < births.size() ? births.get(oi) : now;
				if (c.get(i).frame() == 0 && now - born > FRESH_MS) {
					k = i + 1;
					why = " age";
				}
			}
			// 49-678차: 같은 글이 다시 옴 - 꽉 찬 같은 줄일 때만 새 줄(바뀜 + 다시 보냄 두 번을 한 번으로, 한 번에 올라오는 줄 수만큼)
			if (k == 0 && sameText && freshName && !c.isEmpty()) {
				boolean allSame = c.size() >= MAX_ROWS;
				for (var r : c) {
					allSame &= r.sig().equals(c.get(0).sig()) && r.frame() == 0;
				}
				if (!allSame || expectEcho) {
					expectEcho = false;
					why = " echo";
				} else {
					Integer b = batchOf(c.get(0).item());
					k = Math.min(c.size(), b == null ? 1 : b);
					why = " same x" + k;
					lastSameAt = now;
					expectEcho = true;
				}
			} else if (k > 0 || !sameText) {
				expectEcho = true;   // 바뀐 글 - 곧 같은 글이 한 번 더 온다
				// 한 번에 올라온 줄 수 배우기(49-715차): 몇 줄이 새로 왔는지 하나로 정해질 때만 -
				//  (가) 덜 찬 목록이 잘리지 않고 늘었거나 (나) 남은 지난 줄이 있고 한 줄 더 새로 봐서는 안 맞을 때(돌 2개, 철 1 + 돌 1 등).
				// 아이템마다(이름 기준 - 개수가 다른 구리 21개, 15개도 같은 구리)와 모든 아이템 합쳐 센다.
				boolean grew = cur.size() < MAX_ROWS && cur.size() - k == prev.size();
				// 남은 지난 줄이 하나라도 맞았고, 한 줄 더 새로 봐도 남는 줄이 있는데 안 맞을 때(전부 새 줄로 보는 경우는 늘 맞으니 빼고 본다)
				boolean unique = k < c.size() && why.isEmpty() && (k + 1 >= c.size() || !fits(c, o, k + 1));
				if (k > 0 && (grew || unique)) {
					boolean one = true;
					for (int i = 1; i < k; i++) {
						one &= c.get(i).item().equals(c.get(0).item());
					}
					learnBatch(c.get(0).item(), Math.min(k, MAX_ROWS), one);
				}
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
					String item = c.get(i).item();
					int n = Math.max(1, c.get(i).count());
					if (item != null && item.startsWith("ng")) {
						long[] last = LAST_NG.get(item);
						int full = n;
						if (last != null && now - last[1] < 600 && n > last[0]) {
							n -= (int) last[0];   // 같은 줄이 더 큰 개수로 다시 옴 - 늘어난 만큼만
						}
						LAST_NG.put(item, new long[]{full, now});
					}
					l.accept(item, n);
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
		fadeSeenAt = 0;
		LAST_NG.clear();
	}

	private static long probeSize = -1;

	private static void probe(List<NeogulDungeonHud.MineRow> cur, int k, boolean top, String why) {
		probeLine("new=" + k + why + (top ? " top" : " bottom") + " | " + sigs(cur));   // 49-678차: 시각은 밀리초까지
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
			String line = java.time.LocalTime.now().withNano(java.time.LocalTime.now().getNano() / 1_000_000 * 1_000_000) + " " + text + "\n";
			Files.createDirectories(f.getParent());
			Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			probeSize += line.getBytes(StandardCharsets.UTF_8).length;
		} catch (Throwable ignored) {
		}
	}
}
