package net.minecraft.client.gui;

import kr.lunaslight.mod.util.WindowAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.reflect.Method;

/**
 * 37차(2026-08-29): 1.15.2 전용 컴파일 타임 shim("Era 0"). compat/legacy-era1/의 DrawContext.java
 * (1.16~1.19.4용)와 구조는 거의 같지만, 실제 위임 대상 API가 다릅니다 - Fabric 공식 Yarn 원본 매핑
 * 파일을 1.15.2/1.16.5 둘 다 직접 대조해서 확인한 결과(claude/nova-mod-todo.md 37차):
 *
 *  - MatrixStack(net.minecraft.client.util.math.MatrixStack) 클래스 자체는 1.15.2에도 이미 존재합니다
 *    ("Era 0은 MatrixStack이 없다"던 28차 추정은 틀렸음 - 실측으로 정정).
 *  - 다만 DrawableHelper.fill(...)이 MatrixStack을 받는 오버로드는 1.15.2에도 있지만(fill(Matrix4f,...)),
 *    우리가 실제로 쓰는 건 매트릭스 없이 그리는 원조 오버로드 fill(IIIII) - 그래서 이 shim은
 *    matrices 필드를 그리기에 쓰지 않고 그냥 보관만 합니다(생성자 시그니처를 LunaCompat.toDrawContext가
 *    리플렉션으로 찾는 (MatrixStack) 형태와 맞추기 위한 용도).
 *  - DrawableHelper에는 drawTextWithShadow/drawStringWithShadow 메서드 자체가 없습니다(1.16부터 신설) -
 *    대신 TextRenderer.drawWithShadow(String,float,float,int)int(method_1720)를 직접 호출합니다.
 *  - drawItem은 legacy-era1과 동일하게 후보 이름 리스트로 리플렉션 시도(renderGuiItemIcon/renderGuiItem이
 *    1.15.2에도 그대로 존재 - method_4010/method_4023 확인).
 */
public class DrawContext {
	private final MatrixStack matrices;
	private final MinecraftClient client;

	/** LunaCompat.registerHudRenderCallback이 구버전 HudRenderCallback의 MatrixStack 인자를
	 * 이 생성자로 감싸서 넘깁니다(리플렉션 호출 - 신버전 진짜 DrawContext엔 이 생성자가 없음). */
	public DrawContext(MatrixStack matrices) {
		this.matrices = matrices;
		this.client = MinecraftClient.getInstance();
	}

	public MatrixStack getMatrices() {
		return matrices;
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		// 1.15.2의 DrawableHelper.fill(IIIII)은 MatrixStack 인자가 없는 원조 형태.
		DrawableHelper.fill(x1, y1, x2, y2, color);
	}

	/**
	 * 49-22차: 신버전 DrawContext#fillGradient(x1,y1,x2,y2,colorStart,colorEnd)의 shim - 이 시대 DrawableHelper의
	 * fillGradient는 protected 인스턴스 메서드라 정적으로 못 부르므로, 두 색의 중간색 단색으로 근사(안티앨리어싱
	 * 페더 띠 등 얇은 영역에만 쓰여 시각 차이 거의 없음).
	 */
	public void fillGradient(int x1, int y1, int x2, int y2, int colorStart, int colorEnd) {
		int a = (((colorStart >>> 24) & 0xFF) + ((colorEnd >>> 24) & 0xFF)) / 2;
		int r = (((colorStart >> 16) & 0xFF) + ((colorEnd >> 16) & 0xFF)) / 2;
		int g = (((colorStart >> 8) & 0xFF) + ((colorEnd >> 8) & 0xFF)) / 2;
		int b = ((colorStart & 0xFF) + (colorEnd & 0xFF)) / 2;
		fill(x1, y1, x2, y2, (a << 24) | (r << 16) | (g << 8) | b);
	}

	public int drawTextWithShadow(TextRenderer textRenderer, String text, int x, int y, int color) {
		// DrawableHelper 쪽엔 이 메서드가 없어서 TextRenderer의 실제 그림자 텍스트 메서드를 직접 호출.
		return textRenderer.drawWithShadow(text, (float) x, (float) y, color);
	}

	// 49-36차: Text 오버로드 - 1.15.2 TextRenderer는 String만 받으므로 asFormattedString()(§ 서식 포함)으로 변환.
	public int drawTextWithShadow(TextRenderer textRenderer, Text text, int x, int y, int color) {
		return textRenderer.drawWithShadow(text == null ? "" : text.asFormattedString(), (float) x, (float) y, color);
	}

	public int drawText(TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow) {
		return drawText(textRenderer, text == null ? "" : text.asFormattedString(), x, y, color, shadow);
	}

	public int drawText(TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow) {
		return shadow ? textRenderer.drawWithShadow(text, (float) x, (float) y, color)
			: textRenderer.draw(text, (float) x, (float) y, color);
	}

	/**
	 * ItemRenderer의 "아이콘 하나 그리기" 메서드를 legacy-era1과 동일한 후보 순서로 리플렉션 시도.
	 * 전부 실패해도 게임은 죽지 않고 그냥 그 아이콘만 안 그려집니다.
	 */
	public void drawItem(ItemStack stack, int x, int y) {
		if (stack == null || stack.isEmpty() || client.getItemRenderer() == null) {
			return;
		}
		Object renderer = client.getItemRenderer();
		String[] candidates = {"renderGuiItemIcon", "renderInGuiWithOverrides", "renderInGui", "renderGuiItem"};
		for (String name : candidates) {
			if (tryRenderItem(renderer, name, stack, x, y)) {
				return;
			}
		}
	}

	private static boolean tryRenderItem(Object renderer, String methodName, ItemStack stack, int x, int y) {
		// 46차: 프로덕션에서는 메서드 이름이 intermediary(method_xxx)라 LunaCompat 이름표로 후보를 넓혀 비교
		java.util.List<String> names = kr.lunaslight.mod.util.LunaCompat.memberNameCandidates(renderer.getClass(), methodName);
		for (Method m : renderer.getClass().getMethods()) {
			if (!names.contains(m.getName())) {
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			try {
				if (params.length == 3 && params[0].isInstance(stack) && params[1] == int.class && params[2] == int.class) {
					m.setAccessible(true);
					m.invoke(renderer, stack, x, y);
					return true;
				}
			} catch (Throwable ignored) {
				// 이 오버로드는 안 맞았던 것 - 다음 후보로.
			}
		}
		return false;
	}

	// ==================== 49-45차: 설정 화면을 1.15.2에서도 켜기 위해 추가 ====================

	/** 겹쳐 쓸 수 있는 잘라내기(신버전 DrawContext#enableScissor와 같은 의미). GL로 직접 자른다. */
	private static final java.util.ArrayDeque<int[]> SCISSORS = new java.util.ArrayDeque<>();

	public void enableScissor(int x1, int y1, int x2, int y2) {
		int[] top = SCISSORS.peek();
		if (top != null) {
			x1 = Math.max(x1, top[0]);
			y1 = Math.max(y1, top[1]);
			x2 = Math.min(x2, top[2]);
			y2 = Math.min(y2, top[3]);
		}
		SCISSORS.push(new int[]{x1, y1, x2, y2});
		applyScissor(x1, y1, x2, y2);
	}

	public void disableScissor() {
		if (!SCISSORS.isEmpty()) {
			SCISSORS.pop();
		}
		int[] top = SCISSORS.peek();
		if (top != null) {
			applyScissor(top[0], top[1], top[2], top[3]);
		} else {
			org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
		}
	}

	private void applyScissor(int x1, int y1, int x2, int y2) {
		try {
			net.minecraft.client.util.Window window = WindowAccess.of(client);
			double scale = window.getScaleFactor();
			int fbHeight = window.getFramebufferHeight();
			org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
			org.lwjgl.opengl.GL11.glScissor((int) Math.floor(x1 * scale), (int) Math.floor(fbHeight - y2 * scale),
				Math.max(0, (int) Math.ceil((x2 - x1) * scale)), Math.max(0, (int) Math.ceil((y2 - y1) * scale)));
		} catch (Throwable ignored) {
		}
	}

	/**
	 * 1.15.2의 툴팁은 Screen#renderTooltip(List&lt;String&gt;,int,int)이라 Text 목록을 그대로 못 넘긴다 -
	 * 글자만 뽑아 넘긴다.
	 */
	public void drawTooltip(TextRenderer textRenderer, java.util.List<Text> lines, int x, int y) {
		Object screen = client.currentScreen;
		if (screen == null || lines == null || lines.isEmpty()) {
			return;
		}
		java.util.List<String> plain = new java.util.ArrayList<>();
		for (Text t : lines) {
			plain.add(t.getString());
		}
		for (Method m : screen.getClass().getMethods()) {
			if (!kr.lunaslight.mod.util.LunaCompat.nameMatches(screen.getClass(), "renderTooltip", m.getName())) {
				continue;
			}
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 3 && java.util.List.class.isAssignableFrom(p[0]) && p[1] == int.class && p[2] == int.class) {
				try {
					m.setAccessible(true);
					m.invoke(screen, plain, x, y);
					return;
				} catch (Throwable ignored) {
				}
			}
		}
	}

	public void drawTooltip(TextRenderer textRenderer, Text line, int x, int y) {
		drawTooltip(textRenderer, java.util.Collections.singletonList(line), x, y);
	}

	// ==================== 49-82차: 텍스처 그리기 ====================

	/**
	 * 49-82차: 신버전 {@code DrawContext#drawTexture(Identifier, x, y, w, h, u, v, regionW, regionH, texW, texH)}의
	 * 1.15.2 shim - 이 시대 정적 {@code DrawableHelper.blit(x, y, w, h, u, v, regionW, regionH, texW, texH)}에
	 * 그대로 넘긴다(javap 실측: {@code blit(IIIIFFIIII)V}). 고정 파이프라인이라 텍스처는 TextureManager에 묶고,
	 * 색은 LunaGfx가 {@code RenderSystem.color4f}로 건다. matrices는 fill과 마찬가지로 쓰지 않는다.
	 * 이걸로 1.15.2도 둥근 모서리·워드마크·로비 배경·얼굴을 얻는다(채팅 얼굴은 아니다 - 채팅 줄이 String이다).
	 */
	public void drawTexture(net.minecraft.util.Identifier texture, int x, int y, int width, int height,
			float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		client.getTextureManager().bindTexture(texture);
		DrawableHelper.blit(x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
	}

}
