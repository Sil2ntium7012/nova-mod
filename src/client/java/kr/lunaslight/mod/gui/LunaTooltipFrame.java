package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * 49-23차: "레전더리 툴팁처럼 툴팁 예쁘게" - 바닐라 툴팁 배경(TooltipBackgroundRenderer.render)을
 * TooltipBackgroundMixin이 취소하고 여기서 대신 그린다.
 *
 *  - 어두운 세로 그라데이션 배경 + 모서리 2px 라운드(모서리 픽셀 생략) + 바깥 2px 부드러운 그림자
 *  - 1px 테두리를 아이템 **등급 색**(일반 회색 / 고급 노랑 / 희귀 하늘 / 영웅 보라)으로, 아래로 갈수록 어둡게
 *  - 테두리 안쪽 윗줄에 옅은 하이라이트
 *  - 첫 줄(아이템 이름) 밑에 등급 색으로 오른쪽으로 사라지는 구분선
 *
 * 어떤 아이템의 툴팁인지는 배경 렌더 호출에 안 실려 오므로, ItemTooltipCallback(툴팁 줄을 만드는 시점 =
 * 같은 프레임 바로 직전)에서 noteStack()으로 적어 두고 여기서 한 번 소비한다. 아이템이 아닌 툴팁(버튼 등)은
 * 중립 회색 테두리.
 */
public final class LunaTooltipFrame {
	private LunaTooltipFrame() {
	}

	public static volatile boolean enabled;
	public static volatile boolean rarityBorder = true;
	public static volatile boolean nameSeparator = true;
	public static volatile boolean shadow = true;
	/**
	 * 49-70차(4-32): 상자 <b>아래 테두리 자리에 내구도 막대</b>를 깐다.
	 *
	 * <p>여기서 그리는 이유: 배경 렌더는 <b>이미 어떤 아이템인지 알고 있고</b>(noteStack) 상자의 정확한
	 * 네 변을 안다. 툴팁 <b>줄</b>로 넣으면 줄 하나가 통째로 늘어나 툴팁이 길어지는데, 테두리에 얹으면
	 * <b>높이가 1px도 안 는다</b>. 내구도는 "숫자"보다 "얼마나 남았나"가 먼저 읽혀야 하는 값이라
	 * 막대가 맞다(숫자 줄은 따로 켤 수 있다).
	 */
	public static volatile boolean durabilityBar = true;

	private static ItemStack lastStack;
	private static long lastStackNanos;

	private static final int NEUTRAL = 0xFF9AA3AD;
	private static final int BG_TOP = 0xF4151A22;
	private static final int BG_BOTTOM = 0xF40B0E13;

	/** 툴팁 줄을 만드는 시점에 어떤 아이템인지 기록(ItemTooltipInfoModule의 콜백에서). */
	public static void noteStack(ItemStack stack) {
		lastStack = stack;
		lastStackNanos = System.nanoTime();
	}

	/** 등급 색(ARGB, 불투명). */
	public static int rarityColor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return NEUTRAL;
		}
		return switch (LunaCompat.rarityName(stack)) {
			case "UNCOMMON" -> 0xFFFFDD66;
			case "RARE" -> 0xFF62D9FF;
			case "EPIC" -> 0xFFD48CFF;
			default -> 0xFFB4BCC6;
		};
	}

	private static int darken(int argb, float k) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.round(((argb >> 16) & 0xFF) * k);
		int g = Math.round(((argb >> 8) & 0xFF) * k);
		int b = Math.round((argb & 0xFF) * k);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int alpha(int argb, int a) {
		return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
	}

	/**
	 * 바닐라와 같은 좌표 규약: (x,y)는 텍스트 좌상단, (w,h)는 텍스트 영역. 실제 상자는 사방 4px 바깥.
	 * 꺼져 있으면 false(바닐라가 그림).
	 */
	public static boolean render(DrawContext ctx, int x, int y, int w, int h) {
		if (!enabled || ctx == null) {
			return false;
		}
		ItemStack stack = null;
		if (lastStack != null && System.nanoTime() - lastStackNanos < 120_000_000L) {
			stack = lastStack;
		}
		lastStack = null;
		int color = rarityBorder ? rarityColor(stack) : LunaTheme.ACCENT;

		int x0 = x - 4, y0 = y - 4, x1 = x + w + 4, y1 = y + h + 4;

		// 그림자(바깥 2px, 아래·오른쪽으로 살짝)
		if (shadow) {
			ctx.fill(x0 + 1, y1, x1 + 1, y1 + 1, 0x40000000);
			ctx.fill(x0 + 2, y1 + 1, x1 + 2, y1 + 2, 0x22000000);
			ctx.fill(x1, y0 + 1, x1 + 1, y1, 0x40000000);
			ctx.fill(x1 + 1, y0 + 2, x1 + 2, y1 + 1, 0x22000000);
		}

		// 배경(모서리 2px 라운드)
		ctx.fill(x0 + 2, y0, x1 - 2, y0 + 1, BG_TOP);
		ctx.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, BG_TOP);
		ctx.fillGradient(x0, y0 + 2, x1, y1 - 2, BG_TOP, BG_BOTTOM);
		ctx.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, BG_BOTTOM);
		ctx.fill(x0 + 2, y1 - 1, x1 - 2, y1, BG_BOTTOM);

		// 테두리(등급 색 → 아래로 어둡게)
		int top = alpha(color, 0xE6);
		int bottom = alpha(darken(color, 0.55f), 0xC8);
		ctx.fill(x0 + 2, y0, x1 - 2, y0 + 1, top);
		ctx.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, top);
		ctx.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, top);
		ctx.fillGradient(x0, y0 + 2, x0 + 1, y1 - 2, top, bottom);
		ctx.fillGradient(x1 - 1, y0 + 2, x1, y1 - 2, top, bottom);
		ctx.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, bottom);
		ctx.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, bottom);
		ctx.fill(x0 + 2, y1 - 1, x1 - 2, y1, bottom);
		// 안쪽 윗줄 하이라이트
		ctx.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, 0x14FFFFFF);

		// 아래 테두리 위에 내구도 막대(높이를 안 늘리고 얹는다)
		drawDurability(ctx, stack, x0, y1, x1 - x0);

		// 이름 밑 구분선(두 줄 이상일 때, 이름 줄 10px + 간격 2px 사이)
		if (nameSeparator && h > 12) {
			int sy = y + 10;
			int segs = 8;
			for (int i = 0; i < segs; i++) {
				int sx0 = x + w * i / segs;
				int sx1 = x + w * (i + 1) / segs;
				int a = Math.round(0xA0 - (0xA0 - 0x14) * (i / (float) (segs - 1)));
				ctx.fill(sx0, sy, sx1, sy + 1, alpha(color, a));
			}
		}
		return true;
	}

	/**
	 * 아래 테두리(1px) 바로 위에 2px 막대. 남은 양에 따라 초록 → 노랑 → 빨강.
	 *
	 * <p>바닐라 아이템 칸의 내구도 막대와 <b>같은 색 규칙</b>을 쓰지 않는다 - 바닐라는 색상환을 돌려
	 * 초록에서 빨강까지 연속으로 가는데, 툴팁에서는 <b>"아직 괜찮다 / 슬슬 고쳐라 / 곧 부러진다"</b>
	 * 세 단계로 읽히는 쪽이 빠르다.
	 */
	private static void drawDurability(DrawContext ctx, ItemStack stack, int x0, int y1, int boxW) {
		if (!durabilityBar || stack == null || stack.isEmpty() || boxW < 8) {
			return;
		}
		try {
			if (!stack.isDamageable()) {
				return;
			}
			int max = stack.getMaxDamage();
			if (max <= 0) {
				return;
			}
			int left = Math.max(0, max - stack.getDamage());
			if (left >= max) {
				return;           // 새것이면 안 그린다 - 아무 정보가 없는 막대는 장식일 뿐이다
			}
			float ratio = Math.min(1f, left / (float) max);
			int bar = ratio > 0.5f ? 0xFF5AD86E : (ratio > 0.25f ? 0xFFFFD24A : 0xFFEF4444);
			int by = y1 - 3;
			int bx0 = x0 + 2;
			int bx1 = x0 + boxW - 2;
			int fill = bx0 + Math.max(1, Math.round((bx1 - bx0) * ratio));
			ctx.fill(bx0, by, bx1, by + 2, 0x60000000);
			ctx.fill(bx0, by, fill, by + 2, bar);
		} catch (Throwable ignored) {
			// 이 버전에 없는 모양의 아이템 - 막대만 건너뛴다(툴팁 자체는 그대로)
		}
	}
}
