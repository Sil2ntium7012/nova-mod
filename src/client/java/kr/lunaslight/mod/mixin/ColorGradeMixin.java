package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ColorGradeHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-42차: 색보정 - HUD를 그리기 직전에 화면 전체를 블렌딩 사각형으로 덮는다(월드에만 적용, HUD는 위에 정상 색).
 *  · 1.20.1~1.20.6: render(DrawContext, float)  · 1.21+: render(DrawContext, RenderTickCounter)  (javap·tiny 실측)
 *  · 1.16~1.19.4: render(MatrixStack, float)  · 1.15.2: render(float) - 49-47차에 추가(그 전까지 이 시대는 색보정이 아예 안 됐다).
 *    셋 다 require=0이라 자기 버전에 없는 주입점은 조용히 빠진다.
 */
@Mixin(InGameHud.class)
public abstract class ColorGradeMixin {

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade120(DrawContext ctx, float tickDelta, CallbackInfo ci) {
		ColorGradeHook.render(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade121(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
		ColorGradeHook.render(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), require = 0)
	private void lunaslight$gradeLegacy(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		ColorGradeHook.renderLegacy(matrices);
	}

	/** 1.15.2는 render(float) 하나 - 행렬 인자가 아예 없다(GUI에 MatrixStack이 들어오기 전). */
	@Inject(method = "render(F)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade1152(float tickDelta, CallbackInfo ci) {
		ColorGradeHook.renderLegacy(new MatrixStack());
	}

	/** 49-176차: HudRenderCallback이 없는 1.14.4에서 Luna HUD를 그리는 자리(콜백이 있는 버전은 legacyHudRender가 바로 끝난다). */
	@Inject(method = "render(F)V", at = @At("TAIL"), require = 0)
	private void lunaslight$hudLegacy(float tickDelta, CallbackInfo ci) {
		kr.lunaslight.mod.util.LunaCompat.legacyHudRender(new MatrixStack(), tickDelta);
	}
}
