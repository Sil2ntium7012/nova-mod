package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

/**
 * 49-81차(4-48): 채팅 줄 훅이 부르는 쪽. 1.20+ 전용(OrderedText·Style#getInsertion) - 옛 버전 빌드에서는
 * 믹스인과 같이 빠진다(loom-common.gradle legacy_compat_dir). 판단은 {@link ChatFaces}, 여기는 OrderedText만 읽는다.
 *
 * <p>49-208차: 띄우개가 줄 안 어디에나(플레이어 이름마다) 있을 수 있어 줄 전체를 훑는다. 띄우개를 찾으면 그 앞까지의
 * 글자 폭(TextRenderer#getWidth - 굵게 등 Style까지 바닐라 계산 그대로)만큼 옮겨 얼굴을 그린다.
 *
 * <p>비용: 띄우개가 한 번도 안 붙었으면({@link ChatFaces#anyDecorated}) 첫 줄에서 빠져나간다. 붙은 뒤에는 그려지는
 * 글자를 한 번 훑고, 띄우개가 있는 줄만 폭을 잰다.
 */
public final class ChatFaceLines {
	private ChatFaceLines() {
	}

	private static final int[] COUNT = new int[1];
	private static final java.util.List<Integer> HIT_AT = new java.util.ArrayList<>();
	private static final java.util.List<String> HIT_NAME = new java.util.ArrayList<>();

	/** 줄을 그리기 직전. 띄우개마다 그 자리에 얼굴을 그린다. color는 그 줄의 ARGB(알파 = 페이드). */
	public static void beforeLine(DrawContext ctx, OrderedText text, int x, int y, int color) {
		drawFaces(ctx, text, x, y, (color >>> 24) & 0xFF);
	}

	/** 1.21.11+ Backend 경로: 투명도가 0~1 실수로 온다. x는 그 좌표계의 0. */
	public static void beforeLine(DrawContext ctx, OrderedText text, int y, float opacity) {
		drawFaces(ctx, text, 0, y, Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f));
	}

	private static void drawFaces(DrawContext ctx, OrderedText text, int x, int y, int alpha) {
		if (!ChatFaces.enabled || !ChatFaces.anyDecorated || ctx == null || text == null) {
			return;
		}
		try {
			COUNT[0] = 0;
			HIT_AT.clear();
			HIT_NAME.clear();
			text.accept((index, style, codePoint) -> {
				if (codePoint == ChatFaces.SPACER && style != null) {
					String name = ChatFaces.nameFromInsertion(style.getInsertion());
					if (name != null) {
						HIT_AT.add(COUNT[0]);
						HIT_NAME.add(name);
					}
				}
				COUNT[0]++;
				return true;
			});
			if (HIT_AT.isEmpty()) {
				return;
			}
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			for (int i = 0; i < HIT_AT.size(); i++) {
				int k = HIT_AT.get(i);
				int dx = k == 0 || tr == null ? 0 : tr.getWidth(prefix(text, k));
				ChatFaces.draw(ctx, HIT_NAME.get(i), x + dx, y, alpha);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("chatFace:line", t);
		}
	}

	/** text의 앞 count글자만 보여 주는 OrderedText(폭 재기용). */
	private static OrderedText prefix(OrderedText text, int count) {
		return visitor -> {
			int[] n = {0};
			return text.accept((index, style, codePoint) -> n[0]++ < count && visitor.accept(index, style, codePoint));
		};
	}
}
