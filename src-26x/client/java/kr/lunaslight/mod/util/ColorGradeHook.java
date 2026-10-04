package kr.lunaslight.mod.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-42차: 색보정(ColorGradingModule)이 HUD가 그려지기 직전(월드만 프레임버퍼에 있는 순간)에 화면 전체를
 * 몇 겹 덮어 밝기·색온도를 바꾼다. ColorGradeMixin(InGameHud.render HEAD)이 부른다.
 *
 * <p>⚠️ 49-66차 정정: 이 주석은 원래 "곱하기/더하기/반전 블렌딩"이라고 적혀 있었지만 <b>사실이 아니다</b>.
 * 실제 구현은 처음부터 {@code ctx.fill}의 <b>평범한 알파 덮기</b>뿐이다. 블렌딩 모드를 바꾸는 통로는
 * 버전마다 세 갈래로 갈려 있어(≤1.16 GlStateManager · 1.17~1.21.5 RenderSystem · 1.21.6+ 파이프라인)
 * 40개 버전 공용으로는 못 쓴다 - 49-49차에 Custom Fog를 포기한 것과 같은 이유다.
 * 그래서 색보정은 "알파 덮기로 표현되는 것"(색온도·헤이즈·비네트)에 집중한다.
 */
public final class ColorGradeHook {
	private ColorGradeHook() {
	}

	public interface Handler {
		void grade(GuiGraphicsExtractor ctx, int width, int height);
	}

	private static volatile Handler handler;

	public static void set(Handler h) {
		handler = h;
	}

	/**
	 * 49-47차: 1.19.4 이하는 InGameHud.render의 첫 인자가 MatrixStack이라 DrawContext가 아예 없다.
	 * 그 시대 서브프로젝트에는 compat shim DrawContext(MatrixStack) 생성자가 있으므로 감싸서 넘긴다
	 * (1.20+에서는 그런 생성자가 없어 null → 조용히 아무것도 안 함. 어차피 그 주입점도 안 붙는다).
	 */
	public static void renderLegacy(Object matrices) {
		render(LunaCompat.toDrawContext(matrices));
	}

	public static void render(GuiGraphicsExtractor ctx) {
		Handler h = handler;
		if (h == null || ctx == null) {
			return;
		}
		try {
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			if (client == null || client.level == null || client.getWindow() == null) {
				return;
			}
			h.grade(ctx, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
		} catch (Throwable t) {
			LunaCompat.warnOnce("colorGrade", t);
		}
	}
}
