package net.minecraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.reflect.Method;

/**
 * 28차(2026-08-28): 1.16~1.19.4 전용 컴파일 타임 shim. 진짜 마인크래프트에는 이 클래스가
 * 없습니다(net.minecraft.client.gui.DrawContext는 1.20에서 신설) — kr.lunaslight.mod 쪽 공유
 * 소스는 최신 DrawContext API 그대로(예: context.fill(...), context.drawTextWithShadow(...))를
 * 쓰기 때문에, 이 시대(1.16~1.19.4)의 서브프로젝트에서만 이 클래스를 별도 소스 폴더
 * (rootProject의 compat/legacy-era1/, loom-common.gradle이 gradle.properties의
 * legacy_compat_dir 값을 보고 조건부로 붙임)로 끼워 넣어서, 공유 소스는 단 한 줄도 고치지
 * 않고도 컴파일되게 합니다.
 *
 * 신버전(1.20+) 서브프로젝트는 이미 이 이름의 진짜 마인크래프트 클래스가 클래스패스에 있으므로
 * 이 파일은 그쪽 sourceSets에는 아예 추가되지 않습니다(이름 충돌 없음 - LibGui 등 실제 멀티버전
 * 모드가 쓰는 것과 같은 패턴, claude/nova-mod-todo.md 28차 조사 참고).
 *
 * 내부적으로는 이 시대의 진짜 렌더링 API(DrawableHelper 정적 메서드 + MatrixStack)로 위임합니다.
 * 공유 소스가 실제로 쓰는 메서드는 fill / drawTextWithShadow / drawItem 셋뿐입니다(37개 파일
 * 전수조사 결과 - drawText/drawCenteredTextWithShadow/drawTooltip/enableScissor/disableScissor는
 * LunaClientScreen/ChatSearchScreen에서만 쓰이는데 그 두 화면은 이 시대 서브프로젝트 컴파일에서
 * 통째로 제외되어 있어 여기 구현할 필요가 없습니다).
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
		DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
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
		return textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	// 49-36차: gui 그리기 헬퍼(LunaDraw/LunaGfx/LunaIcons/LunaTooltipFrame)와 스코어보드가 이 시대에도
	// 컴파일되도록 Text 오버로드 추가 - 1.16~1.19.4 TextRenderer#draw/drawWithShadow(MatrixStack, Text, float, float, int).
	public int drawTextWithShadow(TextRenderer textRenderer, Text text, int x, int y, int color) {
		return textRenderer.drawWithShadow(matrices, text, x, y, color);
	}

	public int drawText(TextRenderer textRenderer, Text text, int x, int y, int color, boolean shadow) {
		return shadow ? textRenderer.drawWithShadow(matrices, text, x, y, color)
			: textRenderer.draw(matrices, text, x, y, color);
	}

	public int drawText(TextRenderer textRenderer, String text, int x, int y, int color, boolean shadow) {
		return shadow ? textRenderer.drawWithShadow(matrices, text, x, y, color)
			: textRenderer.draw(matrices, text, x, y, color);
	}

	/**
	 * ItemRenderer의 정확한 "아이콘 하나 그리기" 메서드 이름이 1.16~1.19.4 사이에서도 미세하게
	 * 다를 수 있어(예: renderGuiItemIcon / renderInGuiWithOverrides 등), LunaCompat과 같은 방식으로
	 * 후보 이름을 순서대로 시도합니다. 전부 실패해도 게임은 죽지 않고 그냥 그 아이콘만 안 그려집니다.
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

	// ==================== 49-45차: 설정 화면을 이 시대에도 켜기 위해 추가한 것들 ====================

	/**
	 * 49-45차: 신버전 DrawContext#enableScissor(x1,y1,x2,y2)의 shim. 이 시대엔 DrawableHelper에
	 * 같은 이름이 없거나(≤1.19.3) 시그니처가 달라서 GL로 직접 자른다. 화면 좌표 → 프레임버퍼
	 * 좌표(위아래 뒤집힘 + 확대 배율)로 바꿔야 한다.
	 * 신버전과 마찬가지로 <b>겹쳐 쓸 수 있게</b> 스택으로 관리한다(안쪽은 바깥쪽과 교집합).
	 */
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
			net.minecraft.client.util.Window window = client.getWindow();
			double scale = window.getScaleFactor();
			int fbHeight = window.getFramebufferHeight();
			int sx = (int) Math.floor(x1 * scale);
			int sy = (int) Math.floor(fbHeight - y2 * scale);
			int sw = (int) Math.ceil((x2 - x1) * scale);
			int sh = (int) Math.ceil((y2 - y1) * scale);
			org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
			org.lwjgl.opengl.GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
		} catch (Throwable ignored) {
			// 자르기에 실패해도 그리기 자체는 계속된다(살짝 넘쳐 보일 뿐).
		}
	}

	/**
	 * 49-45차: 신버전 DrawContext#drawTooltip(TextRenderer, List&lt;Text&gt;, int, int)의 shim.
	 * 이 시대엔 툴팁 그리기가 Screen의 인스턴스 메서드(renderTooltip)라서, 지금 떠 있는 화면에
	 * 리플렉션으로 넘긴다(우리 화면도 Screen이므로 항상 있다).
	 */
	/** 한 줄짜리 툴팁(신버전 DrawContext에도 같은 이름의 오버로드가 있다). */
	public void drawTooltip(TextRenderer textRenderer, Text line, int x, int y) {
		drawTooltip(textRenderer, java.util.Collections.singletonList(line), x, y);
	}

	public void drawTooltip(TextRenderer textRenderer, java.util.List<Text> lines, int x, int y) {
		Object screen = client.currentScreen;
		if (screen == null || lines == null || lines.isEmpty()) {
			return;
		}
		for (Method m : screen.getClass().getMethods()) {
			if (!kr.lunaslight.mod.util.LunaCompat.nameMatches(screen.getClass(), "renderTooltip", m.getName())) {
				continue;
			}
			Class<?>[] p = m.getParameterTypes();
			if (p.length == 4 && p[0].isInstance(matrices) && java.util.List.class.isAssignableFrom(p[1])
					&& p[2] == int.class && p[3] == int.class) {
				try {
					m.setAccessible(true);
					m.invoke(screen, matrices, lines, x, y);
					return;
				} catch (Throwable ignored) {
					// 다음 후보로
				}
			}
		}
	}

	// ==================== 49-82차: 텍스처 그리기(채팅 얼굴 → 이 시대 UI 전체가 그림을 얻는다) ====================

	/**
	 * 49-82차(4-48 후속): 신버전 {@code DrawContext#drawTexture(Identifier, x, y, w, h, u, v, regionW, regionH, texW, texH)}
	 * (1.20.x 시그니처)의 shim. 그동안 이 시대엔 이 메서드가 없어서 {@code LunaGfx.texturesUsable}가 false였고,
	 * 둥근 모서리·워드마크·로비 배경·얼굴·하트 아이콘이 전부 각진 네모/글자로 내려앉아 있었다.
	 * 실은 {@code DrawableHelper.drawTexture(MatrixStack, …)} 11인자 정적 메서드가 1.16~1.19.4에 그대로 있다
	 * (javap 실측). 다른 건 <b>텍스처를 어디에 묶느냐</b>뿐이다:
	 * <ul>
	 *   <li>1.17+ : 셰이더 시대 - {@code RenderSystem.setShaderTexture(0, id)}(blaze3d, 난독화 없음 → 리플렉션 안전).
	 *       {@code drawTexturedQuad}가 POSITION_TEXTURE 셰이더를 스스로 건다.</li>
	 *   <li>1.16.x: 고정 파이프라인 - {@code TextureManager#bindTexture(id)}.</li>
	 * </ul>
	 * 색(알파 포함)은 LunaGfx가 {@code setShaderColor}(1.17+) / {@code color4f}(1.16)로 건다.
	 */
	public void drawTexture(net.minecraft.util.Identifier texture, int x, int y, int width, int height,
			float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
		bindTexture(texture);
		DrawableHelper.drawTexture(matrices, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
	}

	private static Method setShaderTexture;
	private static boolean setShaderTextureResolved;

	private void bindTexture(net.minecraft.util.Identifier texture) {
		if (!setShaderTextureResolved) {
			setShaderTextureResolved = true;
			try {
				setShaderTexture = com.mojang.blaze3d.systems.RenderSystem.class.getMethod("setShaderTexture", int.class, net.minecraft.util.Identifier.class);
			} catch (Throwable ignored) {
				setShaderTexture = null;   // 1.16.x
			}
		}
		if (setShaderTexture != null) {
			try {
				setShaderTexture.invoke(null, 0, texture);
				return;
			} catch (Throwable ignored) {
				// 아래 bindTexture로
			}
		}
		client.getTextureManager().bindTexture(texture);
	}

}
