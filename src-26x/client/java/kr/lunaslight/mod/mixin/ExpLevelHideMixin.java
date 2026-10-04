package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HudHideModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-216차: 26.x [HUD 숨기기] 경험치 레벨 숫자. 막대(ExperienceBarHideMixin)와 따로 HUD가 정적 메서드로 그린다:
 * 26.2+ Hud#extractHotbarAndDecorations 안의 ContextualBar.extractExperienceLevel, 26.1.x는 Gui의 같은 자리
 * ContextualBarRenderer.extractExperienceLevel(javap 실측). 숨김이 아니면 원래 메서드를 이름으로 불러 그대로 그린다
 * (두 클래스가 판마다 하나씩만 있어 직접 못 적는다).
 */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Hud", "net.minecraft.client.gui.Gui"})
public abstract class ExpLevelHideMixin {

	private static java.lang.reflect.Method lunaslight$levelDraw;
	private static boolean lunaslight$levelResolved;

	@Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"),
			require = 0)
	private void lunaslight$expLevel(GuiGraphicsExtractor ctx, Font font, int level) {
		lunaslight$draw(ctx, font, level);
	}

	@Redirect(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"),
			require = 0)
	private void lunaslight$expLevel261(GuiGraphicsExtractor ctx, Font font, int level) {
		lunaslight$draw(ctx, font, level);
	}

	private static void lunaslight$draw(GuiGraphicsExtractor ctx, Font font, int level) {
		if (HudHideModule.hideExp) {
			return;
		}
		try {
			if (!lunaslight$levelResolved) {
				lunaslight$levelResolved = true;
				Class<?> c = LunaCompat.resolveClass("net.minecraft.client.gui.contextualbar.ContextualBar",
						"net.minecraft.client.gui.contextualbar.ContextualBarRenderer");
				lunaslight$levelDraw = c == null ? null
						: c.getMethod("extractExperienceLevel", GuiGraphicsExtractor.class, Font.class, int.class);
			}
			if (lunaslight$levelDraw != null) {
				lunaslight$levelDraw.invoke(null, ctx, font, level);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("expLevel", t);
		}
	}
}
