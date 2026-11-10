package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import kr.lunaslight.mod.gui.LunaDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 아이템 획득 표시(49-21차 신규).
 *
 * 사용자가 48차부터 "아이템 먹었을 때 뜨는 거 안 됨"이라고 한 건 **아이템을 주웠을 때**(게이머 은어
 * '먹다' = 줍다) 표시였다 - 그동안 음식 섭취 감지를 고치고 있었으니 당연히 계속 "안 됨". 이제 매 틱
 * 인벤토리 개수를 아이템별로 세어 **늘어난 만큼**을 "+N 이름"으로 핫바 옆에 띄운다. 서버 이벤트에
 * 기대지 않으니 어떤 서버/버전에서도 동일하게 동작.
 *
 * 오탐 방지: 화면(상자/제작대/크리에이티브)이 열려 있는 동안의 증가는 무시(옮기기/제작), 월드
 * 입장·리스폰 직후 2초는 인벤토리 동기화 패킷이 도착하는 중이라 무시.
 *
 * 49-30차: 마름모 틀을 키우고 안쪽에 다른 색 바탕을 깔았다. 틀은 가로줄로 직접 그려 네 끝이
 * 1픽셀로 뾰족하게 모인다. 바탕은 강조 색을 짙게 깐 색이 기본이고
 * (테마 색을 따라간다) [안쪽 바탕]을 끄면 고른 색을 그대로 쓴다.
 */
public class ItemPickupToastModule extends Module {
	// 49-88차: 49-41차 재디자인 뒤에 남아 있던 `모양`(마름모/둥근)·`바탕 자동`·`바탕 색` 설정을 지웠다 - 어느 코드도
	// 읽지 않는 죽은 설정이었다(마름모 그리기 함수째 dead). 없는 기능을 있는 척하던 것.

	private static final int ROW_H = 18;      // 49-109차(사용자: "크기 줄여줘"): 21 → 18(막대 15 + 줄 간격 3)
	private static final int MAX_ROWS = 5;
	/** 마름모 가운데에서 꼭짓점까지(=마름모 크기의 절반). */
	private static final int DIAMOND_R = 12;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_RIGHT, 0, 100)));
	private final IntSetting displaySeconds = register(new IntSetting(
			"display_seconds", "표시 시간", "표시가 남아 있는 시간(초)입니다.", 3, 1, 10, 1).unit("초"));
	private final BooleanSetting showTotal = register(new BooleanSetting(
			"show_total", "보유 개수", "주운 뒤의 총 보유 개수도 표시합니다.", true));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "이름 글자의 색입니다.", 0xFFFFFFFF));
	private final ColorSetting countColor = register(new ColorSetting(
			"count_color", "강조 색", "개수 글자와 틀의 색입니다.", 0xFFA9D973));


	private static class Toast {
		ItemStack icon;
		/** 49-608차: 보일 이름(없으면 아이콘 이름)과 강조(너굴 광산 보석). */
		String name;
		boolean hl;
		/** 49-612차: 다른 기능이 넣은 것(너굴 광산). */
		boolean ext;
		int amount;
		int total;
		long lastMs;
		long firstMs;
	}

	private final List<Toast> toasts = new ArrayList<>();
	/**
	 * 49-453차(사용자 사진: 너굴마을 "굴"과 다른 재료가 한 줄로 합쳐짐 - "같은 아이템 아닌데 같은 아이템으로 인식"): 서버 아이템은
	 * 바닐라 아이템 하나(종이 등)에 모델과 이름만 바꿔 쓰므로 Item만으로 세면 서로 다른 아이템이 합쳐진다. 아이템 + 보이는 이름으로 구분한다.
	 */
	private record Key(Item item, String name) {
	}

	private static Key keyOf(ItemStack s) {
		String n;
		try {
			n = s.getHoverName().getString();
		} catch (Throwable t) {
			n = "";
		}
		return new Key(s.getItem(), n);
	}

	private Map<Key, Integer> lastCounts = null;
	/**
	 * 49-455차(사용자 사진: 너굴마을에서 투명 갑옷 때문에 갑옷이 잠깐 사라졌다 돌아오면 "엘더우드 모자/갑옷/부츠/레깅스"가 획득으로 뜸):
	 * 아이템별로 최근에 줄어든 양(개수, 기억 끝 시각). 서버가 잠깐 뺐다 돌려준 것, 갑옷을 벗었다 다시 입은 것 등은 알림에서 뺀다.
	 */
	private final Map<Key, long[]> recentLoss = new HashMap<>();
	private static final long LOSS_MEMORY_MS = 10_000L;
	/**
	 * 49-457차(사용자: "자동 채우기도 이상하게 아이템 획득에 떠"): 자동 채우기, 정리처럼 아이템을 옮길 때는 받는 칸이 먼저 차고 보내는 칸이
	 * 한두 틱 늦게 비는 때가 있어 그 사이 잠깐 "늘어난" 것으로 보였다. 늘어난 양은 바로 띄우지 않고 0.4초 기다렸다가, 그동안 같은 아이템이
	 * 그만큼 줄면 지운다(아이템 → [남은 개수, 띄울 시각]).
	 */
	private final Map<Key, long[]> pendingGain = new HashMap<>();
	private static final long GAIN_DELAY_MS = 400L;
	private Object lastPlayer = null;
	private Object lastLevel = null;
	private long graceUntilMs = 0;

	public ItemPickupToastModule() {
		super("item_pickup_toast", "아이템 획득", ModuleCategory.HUD, "주운 아이템을 핫바 옆에 표시");
		instance = this;
		showTotal.withColor(countColor); // 49-23차: 1줄 통합
		// 49-179차(사용자: "아이템 획득 그리는 거 없애주고, 변경 불가능한 거는 배경 모양 설정이 따로 있으면 안되지"):
		// 이 기능은 자기 모양(화살 막대 + 회색 판)이 정해져 있어 배경/배경 모양/배경 그리기 설정을 두지 않는다.
	}

	/**
	 * 49-478차(사용자 사진: 블록을 놓아 아이템이 줄 때마다 "엘더우드 갑옷/레깅스/부츠/모자"가 또 획득으로 뜸): 너굴마을은 투명 갑옷 때문에 갑옷 칸을
	 * 오래 비워 두었다가 인벤토리를 다시 보낼 때(블록을 놓아 개수가 바뀔 때 등) 갑옷을 돌려준다 - 10초 기억으로는 못 막았다.
	 * 갑옷 칸(36~39)에 늘어난 것은 주운 게 아니다(줍는 아이템은 갑옷 칸에 바로 안 들어간다). 그만큼은 알림에서 뺀다.
	 */
	private Map<Key, Integer> lastArmor = new HashMap<>();

	private Map<Key, Integer> countArmor() {
		Map<Key, Integer> counts = new HashMap<>();
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return counts;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 36; i < Math.min(40, size); i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty()) {
				counts.merge(keyOf(s), s.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private Map<Key, Integer> countInventory() {
		Map<Key, Integer> counts = new HashMap<>();
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return counts;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 0; i < size; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty()) {
				counts.merge(keyOf(s), s.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private ItemStack findStack(Key key) {
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return ItemStack.EMPTY;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 0; i < size; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty() && keyOf(s).equals(key)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		// 만료 정리
		long life = displaySeconds.get() * 1000L;
		Iterator<Toast> it = toasts.iterator();
		while (it.hasNext()) {
			if (now - it.next().lastMs > life) {
				it.remove();
			}
		}
		if (client.player == null || client.level == null) {
			lastCounts = null;
			pendingGain.clear();
			lastPlayer = null;
			return;
		}
		if (client.player != lastPlayer || client.level != lastLevel) {
			// 49-618차: 월드 이동(레벨이 바뀜)도 - 서버가 가방을 다시 보내는 동안은 줍기로 안 띄운다
			lastPlayer = client.player;
			lastLevel = client.level;
			lastCounts = null;
			lastArmor = new HashMap<>();
			pendingGain.clear();
			graceUntilMs = now + 5000;
		}
		Map<Key, Integer> counts = countInventory();
		Map<Key, Integer> armor = countArmor();
		// 49-455차: 방금(10초 안) 사라졌던 만큼은 "주움"이 아니다 - 줄어든 양을 적어 두고, 다시 늘면 그만큼 먼저 깐다.
		recentLoss.values().removeIf(l -> now > l[1]);
		if (lastCounts != null) {
			for (Map.Entry<Key, Integer> e : lastCounts.entrySet()) {
				int lost = e.getValue() - counts.getOrDefault(e.getKey(), 0);
				if (lost > 0) {
					long[] pg = pendingGain.get(e.getKey());
					if (pg != null) {
						int back = (int) Math.min(lost, pg[0]);
						pg[0] -= back;
						lost -= back;
						if (pg[0] <= 0) {
							pendingGain.remove(e.getKey());
						}
					}
				}
				if (lost > 0) {
					long[] l = recentLoss.computeIfAbsent(e.getKey(), k -> new long[2]);
					l[0] += lost;
					l[1] = now + LOSS_MEMORY_MS;
				}
			}
		}
		if (lastCounts != null && now >= graceUntilMs && kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null) {
			for (Map.Entry<Key, Integer> e : counts.entrySet()) {
				int before = lastCounts.getOrDefault(e.getKey(), 0);
				int gained = e.getValue() - before;
				if (gained > 0) {
					int intoArmor = armor.getOrDefault(e.getKey(), 0) - lastArmor.getOrDefault(e.getKey(), 0);
					if (intoArmor > 0) {
						gained -= Math.min(gained, intoArmor);   // 49-478차: 갑옷 칸으로 돌아온 것
					}
				}
				long[] l = gained > 0 ? recentLoss.get(e.getKey()) : null;
				if (l != null) {
					int back = (int) Math.min(gained, l[0]);
					gained -= back;
					l[0] -= back;
					if (l[0] <= 0) {
						recentLoss.remove(e.getKey());
					}
				}
				if (gained > 0) {
					long[] pg = pendingGain.computeIfAbsent(e.getKey(), k -> new long[2]);
					pg[0] += gained;
					pg[1] = now + GAIN_DELAY_MS;
				}
			}
		} else if (lastCounts != null) {
			// 화면이 열려 있거나 막 들어온 때 다시 생긴 것도 상쇄만 해 둔다(나중에 알림으로 안 나오게)
			for (Map.Entry<Key, Integer> e : counts.entrySet()) {
				int gained = e.getValue() - lastCounts.getOrDefault(e.getKey(), 0);
				long[] l = gained > 0 ? recentLoss.get(e.getKey()) : null;
				if (l != null) {
					l[0] -= Math.min(gained, l[0]);
					if (l[0] <= 0) {
						recentLoss.remove(e.getKey());
					}
				}
			}
		}
		lastCounts = counts;
		lastArmor = armor;
		// 기다린 만큼 지난 늘어남만 알림으로
		java.util.Iterator<Map.Entry<Key, long[]>> pit = pendingGain.entrySet().iterator();
		while (pit.hasNext()) {
			Map.Entry<Key, long[]> pe = pit.next();
			if (now >= pe.getValue()[1]) {
				if (pe.getValue()[0] > 0) {
					addToast(pe.getKey(), (int) pe.getValue()[0], counts.getOrDefault(pe.getKey(), 0), now);
				}
				pit.remove();
			}
		}
	}

	private static ItemPickupToastModule instance;

	public static boolean on() {
		ItemPickupToastModule m = instance;
		return m != null && m.isEnabled();
	}

	/**
	 * 49-608차: 인벤토리를 거치지 않고 얻은 것(너굴 광산: 바로 광물 창고로)을 띄운다. total이 음수면 보유 개수를 안 쓴다.
	 * 켜져 있지 않으면 false.
	 */
	public static boolean addExternal(ItemStack icon, String name, int gained, int total, boolean highlight) {
		ItemPickupToastModule m = instance;
		if (m == null || !m.isEnabled() || icon == null || icon.isEmpty() || gained <= 0) {
			return false;
		}
		long now = System.currentTimeMillis();
		// 49-612차(사용자 사진: 광물 창고가 꽉 차서 인벤토리로 들어왔는데 창고 쪽 줄도 같이 뜸): 방금 인벤토리로 같은 이름이 들어왔으면 그쪽이 맞다
		for (Toast t : m.toasts) {
			if (!t.ext && now - t.lastMs < 1500 && sameName(nameOf(t), name)) {
				return false;
			}
		}
		for (Toast t : m.toasts) {
			if (name != null && name.equals(t.name)) {
				t.amount += gained;
				t.total = total;
				t.lastMs = now;
				t.hl |= highlight;
				return true;
			}
		}
		Toast t = new Toast();
		t.icon = icon.copy();
		t.icon.setCount(1);
		t.name = name;
		t.hl = highlight;
		t.ext = true;
		t.amount = gained;
		t.total = total;
		t.firstMs = now;
		t.lastMs = now;
		m.toasts.add(t);
		while (m.toasts.size() > MAX_ROWS) {
			m.toasts.remove(0);
		}
		return true;
	}

	/** 인벤토리로 들어온 것이 방금 띄운 다른 기능 줄과 같은 것이었을 때(이름, 그 줄 개수) - 그 기능이 센 것을 되돌리게. */
	public static volatile java.util.function.BiConsumer<String, Integer> externalOverflow;

	private static String plainName(String s) {
		StringBuilder sb = new StringBuilder();
		(s == null ? "" : s).codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString().replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\s+", " ").trim();
	}

	private static boolean sameName(String a, String b) {
		String x = plainName(a), y = plainName(b);
		return !x.isEmpty() && x.equals(y);
	}

	private static String nameOf(Toast t) {
		return t.name != null ? t.name : t.icon.getHoverName().getString();
	}

	private void addToast(Key key, int gained, int total, long now) {
		// 49-612차: 같은 이름을 다른 기능(너굴 광산)이 방금 띄웠으면 그건 창고가 아니라 인벤토리로 들어온 것 - 그 줄을 지우고 알린다
		for (var it = toasts.iterator(); it.hasNext(); ) {
			Toast t = it.next();
			if (t.ext && now - t.lastMs < 1500 && sameName(nameOf(t), key.name())) {
				it.remove();
				var cb = externalOverflow;
				if (cb != null) {
					try {
						cb.accept(t.name, t.amount);
					} catch (Throwable ignored) {
					}
				}
				break;
			}
		}
		for (Toast t : toasts) {
			if (t.name == null && keyOf(t.icon).equals(key)) {
				t.amount += gained;
				t.total = total;
				t.lastMs = now;
				return;
			}
		}
		ItemStack src = findStack(key);
		Toast t = new Toast();
		t.icon = src.isEmpty() ? new ItemStack(key.item()) : src.copy();
		t.icon.setCount(1);
		t.amount = gained;
		t.total = total;
		t.firstMs = now;
		t.lastMs = now;
		toasts.add(t);
		while (toasts.size() > MAX_ROWS) {
			toasts.remove(0);
		}
	}

	/** 미리보기용 예시 목록(양귀비 ×22 / 다이아몬드 ×1). */
	private List<Toast> previewToasts;

	private List<Toast> previewList() {
		if (previewToasts == null) {
			previewToasts = new ArrayList<>();
			String[][] samples = {{"minecraft:poppy", "22", "22"}, {"minecraft:diamond", "1", "5"}};
			for (String[] sp : samples) {
				Item item = LunaCompat.itemById(sp[0]);
				if (item == null) {
					continue;
				}
				Toast t = new Toast();
				t.icon = new ItemStack(item);
				t.amount = Integer.parseInt(sp[1]);
				t.total = Integer.parseInt(sp[2]);
				t.firstMs = 0;
				t.lastMs = Long.MAX_VALUE / 2;
				previewToasts.add(t);
			}
		}
		return previewToasts;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		List<Toast> list = isPreview() ? previewList() : toasts;
		if (list.isEmpty()) {
			return;
		}
		drawList(context, list, isPreview(), -1, -1);
	}

	/**
	 * 49-612차(사용자: "새 기능 알려 줄 때 인게임처럼 보이게, 그림 이상해"): 다른 기능의 미리보기가 진짜 아이템 획득 줄 모양을 그대로 쓰게.
	 * rows = {아이콘 ItemStack, 이름, 개수, 보유(음수면 안 씀), 강조}. 오른쪽 끝(right)과 위(y)에 붙여 그린다.
	 */
	public static void drawSample(GuiGraphicsExtractor ctx, int right, int y, List<Object[]> rows) {
		ItemPickupToastModule m = instance;
		if (m == null) {
			return;
		}
		List<Toast> list = new ArrayList<>();
		for (Object[] r : rows) {
			Toast t = new Toast();
			t.icon = ((ItemStack) r[0]).copy();
			t.icon.setCount(1);
			t.name = (String) r[1];
			t.amount = (Integer) r[2];
			t.total = (Integer) r[3];
			t.hl = (Boolean) r[4];
			t.firstMs = 0;
			t.lastMs = Long.MAX_VALUE / 2;
			list.add(t);
		}
		m.drawList(ctx, list, true, right, y);
	}

	private void drawList(GuiGraphicsExtractor context, List<Toast> list, boolean preview, int fixedRight, int fixedY) {
		long now = System.currentTimeMillis();
		long life = displaySeconds.get() * 1000L;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();

		// 49-41차(사용자 피드백): 사진(출석 코인)대로 다시.
		//   ◀ ×N 이름 ◀▐ [아이콘] 총개수 ▌   (오른쪽 벽에 딱 붙음)
		//  · 왼쪽 막대: 반투명 검정, 왼쪽 끝 45° 뾰족(테두리 없음), 오른쪽 끝은 회색 판 뒤로 들어감
		//  · 회색 판: **왼쪽 끝이 45°로 뾰족하게 튀어나온**(볼록) 화살 모양 - 예전엔 안쪽으로 파인(오목) 꺾쇠였음("왜 반대로")
		//    뾰족한 두 변을 따라 밝은 1px 하이라이트(사진의 "❮"), 위·아래·오른쪽은 어두운 1px
		//  · 전체를 키움: 막대 22px, 아이콘 16px(원래 크기 - 축소하면 픽셀이 고르지 않음), 최소 폭 150px ("길이가 너무 작아 / 더 커야")
		//  · 등장: 오른쪽 벽에서 왼쪽으로 미끄러져 나옴, 사라짐: 오른쪽 벽으로 다시 들어감(페이드 아웃 X)
		//  · 글자·아이콘·개수 세로 정렬은 글꼴 모드별 시각 중심(LunaCompat.textVisualCenter)으로
		int point = BAR_H / 2;

		List<String> labels = new ArrayList<>();
		List<String> totals = new ArrayList<>();
		List<Integer> widths = new ArrayList<>();
		int maxW = 0;
		for (Toast t : list) {
			String label = "×" + t.amount + " " + nameOf(t);
			String total = showTotal.get() && t.total >= 0 ? String.format("%,d", t.total) : "";
			labels.add(label);
			totals.add(total);
			int w = point + PAD_L + LunaCompat.getTextWidth(client.font, label) + PAD_L + point + plateWidth(total);
			widths.add(Math.max(MIN_W, w));
			maxW = Math.max(maxW, Math.max(MIN_W, w));
		}
		int totalH = list.size() * ROW_H - (ROW_H - BAR_H);
		HudPosition pos = position.get();
		int x = fixedRight >= 0 ? fixedRight - maxW : pos.resolveX(sw, maxW);
		int y = fixedY >= 0 ? fixedY : pos.resolveY(sh, totalH);
		// 49-498차(사용자 사진: HUD 크기 0.55에서 "미리보기가 원래 위치랑 안 맞잖아"): 예전엔 오른쪽 앵커면 오른쪽 끝을 화면 끝(sw)에
		// 붙였는데, 크기 배율은 상자 왼쪽 위(resolveX)를 축으로 걸려서 배율이 1이 아니면 줄이 상자 왼쪽 밖으로 밀렸다. 이제 늘 resolveX
		// 상자 안 - 가로 이동 0이면 resolveX가 이미 벽에 붙여 준다.
		int rightEdge = x + maxW;
		int accent = countColor.getArgb();
		int textMid = Math.round(LunaCompat.textVisualCenter());

		for (int i = 0; i < list.size(); i++) {
			Toast t = list.get(i);
			int rowW = widths.get(i);
			float in = preview ? 1f : easeOut(Math.min(1f, (now - t.firstMs) / 260f));
			float out = preview ? 1f : easeOut(Math.min(1f, Math.max(0f, (life - (now - t.lastMs)) / 300f)));
			// 벽에서 나오는 만큼(1−in) + 벽으로 들어가는 만큼(1−out) 오른쪽으로 밀어 둔다
			int slide = Math.round((1f - in) * rowW) + Math.round((1f - out) * rowW);
			if (slide >= rowW) {
				continue;
			}
			int rx = rightEdge - rowW + slide;
			int ry = y + i * ROW_H;
			int a = 255;

			String label = labels.get(i);
			String total = totals.get(i);
			int plateW = plateWidth(total);
			int plateX = rx + rowW - plateW;

			// ① 왼쪽 막대(회색 판의 뾰족한 끝 뒤까지)
			drawLeftBar(context, rx, ry, plateX + point - rx, BAR_H, point, withAlpha(0x8C000000, a));
			// ② 회색 판: 왼쪽 끝 볼록 화살 + 하이라이트
			if (t.hl) {
				// 49-608차: 너굴 광산 보석 - 막대 테두리를 금색으로
				LunaDraw.roundRectBordered(context, rx - 1, ry - 1, plateX + point - rx + 2, BAR_H + 2, 2, 0x00000000, withAlpha(0xFFFFD36A, a));
			}
			drawPlate(context, plateX, ry, plateW, BAR_H, point,
				withAlpha(t.hl ? 0xFF5A4720 : 0xFF3A3A3A, a), withAlpha(0xFF111111, a), withAlpha(t.hl ? 0xFFFFD36A : LunaTheme.mix(accent, 0xFFC9CDD2, 0.55f), a));

			// 글자: "×N 이름"
			int ty = ry + BAR_H / 2 - textMid;
			int tx = rx + point + PAD_L;
			String amountText = "×" + t.amount;
			LunaCompat.drawHudText(context, client.font, amountText, tx, ty, withAlpha(t.hl ? 0xFFFFE7A3 : 0xFFD6DADE, a));
			int nameX = tx + LunaCompat.getTextWidth(client.font, amountText + " ");
			LunaCompat.drawHudText(context, client.font, nameOf(t), nameX, ty,
				withAlpha(t.hl ? 0xFFFFD36A : textColor.getArgb(), a));

			// 아이콘: 판 안쪽, 세로 가운데
			int ix = plateX + point + 4;
			int iy = ry + (BAR_H - ICON) / 2;
			drawSmallItem(context, t.icon, ix, iy);

			// 총개수: 판 오른쪽 끝, 글자 시각 중심 = 아이콘 중심
			if (!total.isEmpty()) {
				int totalW = LunaCompat.getTextWidth(client.font, total);
				LunaCompat.drawHudText(context, client.font, total,
					plateX + plateW - PAD_R - totalW, ty, withAlpha(0xFFF2F4F6, a));
			}
		}
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t);
	}

	private static final int BAR_H = 15;      // 49-109차(사용자: "크기 줄여줘"): 18 → 15
	private static final int ICON = 13;       // 49-109차: 16 → 13(막대에 맞춰 살짝 축소)
	private static final int PAD_L = 4;
	private static final int PAD_R = 6;
	private static final int MIN_W = 92;      // 한 줄 최소 폭(49-47차 112 → 49-109차 92)

	private int plateWidth(String total) {
		int tw = total.isEmpty() ? 0 : LunaCompat.getTextWidth(client.font, total) + 6;
		return BAR_H / 2 + 4 + ICON + 6 + tw + PAD_R;
	}

	/** 왼쪽 끝이 45°로 뾰족한 반투명 막대(테두리 없음). */
	private static void drawLeftBar(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int point, int color) {
		for (int dy = 0; dy < h; dy++) {
			int inset = Math.min(point, Math.abs(dy - (h - 1) / 2));
			int l = x + inset;
			int r = x + w;
			if (r > l) {
				ctx.fill(l, y + dy, r, y + dy + 1, color);
			}
		}
	}

	/**
	 * 회색 판: 왼쪽 끝이 화살촉처럼 **튀어나온**(볼록) 상자. x는 화살 끝(가운데 줄)의 x.
	 * 뾰족한 두 변은 밝은 1px(하이라이트), 위·아래·오른쪽은 어두운 1px 테두리.
	 */
	private static void drawPlate(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int point, int fill, int dark, int light) {
		int mid = (h - 1) / 2;
		for (int dy = 0; dy < h; dy++) {
			int inset = Math.min(point, Math.abs(dy - mid));   // 가운데 줄이 가장 왼쪽(뾰족)
			int l = x + inset;
			int r = x + w;
			if (dy == 0 || dy == h - 1) {
				ctx.fill(l, y + dy, r, y + dy + 1, dark);
				continue;
			}
			ctx.fill(l, y + dy, l + 1, y + dy + 1, light);        // 화살 변 하이라이트
			ctx.fill(l + 1, y + dy, r - 1, y + dy + 1, fill);
			ctx.fill(r - 1, y + dy, r, y + dy + 1, dark);          // 오른쪽 테두리
		}
	}

	/** 아이템 아이콘. ICON이 16이면 그대로, 아니면 2D 배율(못 쓰는 버전은 원래 크기로 가운데 맞춤). */
	private void drawSmallItem(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
		if (ICON == 16) {
			ctx.item(stack, x, y);
			return;
		}
		if (LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, x, y);
			LunaCompat.guiScale(ctx, ICON / 16f, ICON / 16f);
			ctx.item(stack, 0, 0);
			LunaCompat.guiPop(ctx);
		} else {
			ctx.item(stack, x - (16 - ICON) / 2, y - (16 - ICON) / 2);
		}
	}

	private static int withAlpha(int argb, int alpha) {
		int base = (argb >>> 24) & 0xFF;
		int a = Math.round(base * (alpha / 255f));
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
