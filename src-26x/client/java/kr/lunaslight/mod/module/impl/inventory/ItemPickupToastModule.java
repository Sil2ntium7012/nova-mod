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
		int amount;
		int total;
		long lastMs;
		long firstMs;
	}

	private final List<Toast> toasts = new ArrayList<>();
	private Map<Item, Integer> lastCounts = null;
	private Object lastPlayer = null;
	private long graceUntilMs = 0;

	public ItemPickupToastModule() {
		super("item_pickup_toast", "아이템 획득", ModuleCategory.HUD, "주운 아이템을 핫바 옆에 표시");
		showTotal.withColor(countColor); // 49-23차: 1줄 통합
		// 49-179차(사용자: "아이템 획득 그리는 거 없애주고, 변경 불가능한 거는 배경 모양 설정이 따로 있으면 안되지"):
		// 이 기능은 자기 모양(화살 막대 + 회색 판)이 정해져 있어 배경/배경 모양/배경 그리기 설정을 두지 않는다.
	}

	private Map<Item, Integer> countInventory() {
		Map<Item, Integer> counts = new HashMap<>();
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return counts;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 0; i < size; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty()) {
				counts.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private ItemStack findStack(Item item) {
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return ItemStack.EMPTY;
		}
		int size = LunaCompat.invSize(inv);
		for (int i = 0; i < size; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, i);
			if (s != null && !s.isEmpty() && s.getItem() == item) {
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
			lastPlayer = null;
			return;
		}
		if (client.player != lastPlayer) {
			lastPlayer = client.player;
			lastCounts = null;
			graceUntilMs = now + 2000;
		}
		Map<Item, Integer> counts = countInventory();
		if (lastCounts != null && now >= graceUntilMs && kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null) {
			for (Map.Entry<Item, Integer> e : counts.entrySet()) {
				int before = lastCounts.getOrDefault(e.getKey(), 0);
				int gained = e.getValue() - before;
				if (gained > 0) {
					addToast(e.getKey(), gained, e.getValue(), now);
				}
			}
		}
		lastCounts = counts;
	}

	private void addToast(Item item, int gained, int total, long now) {
		for (Toast t : toasts) {
			if (t.icon.getItem() == item) {
				t.amount += gained;
				t.total = total;
				t.lastMs = now;
				return;
			}
		}
		ItemStack src = findStack(item);
		Toast t = new Toast();
		t.icon = src.isEmpty() ? new ItemStack(item) : src.copy();
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
			String label = "×" + t.amount + " " + t.icon.getHoverName().getString();
			String total = showTotal.get() ? Integer.toString(t.total) : "";
			labels.add(label);
			totals.add(total);
			int w = point + PAD_L + LunaCompat.getTextWidth(client.font, label) + PAD_L + point + plateWidth(total);
			widths.add(Math.max(MIN_W, w));
			maxW = Math.max(maxW, Math.max(MIN_W, w));
		}
		int totalH = list.size() * ROW_H - (ROW_H - BAR_H);
		HudPosition pos = position.get();
		boolean flushRight = pos.anchor == HudPosition.Anchor.TOP_RIGHT || pos.anchor == HudPosition.Anchor.BOTTOM_RIGHT
			|| pos.anchor == HudPosition.Anchor.RIGHT_CENTER;
		int x = pos.resolveX(sw, maxW);
		int y = pos.resolveY(sh, totalH);
		int rightEdge = flushRight ? sw : x + maxW;   // 오른쪽 앵커면 벽에 딱 붙임(가로 오프셋 무시)
		int accent = countColor.getArgb();
		int textMid = Math.round(LunaCompat.textVisualCenter());

		for (int i = 0; i < list.size(); i++) {
			Toast t = list.get(i);
			int rowW = widths.get(i);
			float in = isPreview() ? 1f : easeOut(Math.min(1f, (now - t.firstMs) / 260f));
			float out = isPreview() ? 1f : easeOut(Math.min(1f, Math.max(0f, (life - (now - t.lastMs)) / 300f)));
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
			drawPlate(context, plateX, ry, plateW, BAR_H, point,
				withAlpha(0xFF3A3A3A, a), withAlpha(0xFF111111, a), withAlpha(LunaTheme.mix(accent, 0xFFC9CDD2, 0.55f), a));

			// 글자: "×N 이름"
			int ty = ry + BAR_H / 2 - textMid;
			int tx = rx + point + PAD_L;
			String amountText = "×" + t.amount;
			LunaCompat.drawHudText(context, client.font, amountText, tx, ty, withAlpha(0xFFD6DADE, a));
			int nameX = tx + LunaCompat.getTextWidth(client.font, amountText + " ");
			LunaCompat.drawHudText(context, client.font, t.icon.getHoverName().getString(), nameX, ty,
				withAlpha(textColor.getArgb(), a));

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
