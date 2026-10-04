package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ColorGradeHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.PoseStack;
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
// 26.2: 인게임 HUD가 Gui에서 Hud 클래스로 분리됨. 26.1.x용 쌍둥이(…Mixin261, Gui 대상)가 따로 있다.
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class ColorGradeMixin {


	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade121(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
		ColorGradeHook.render(ctx);
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), require = 0)
	private void lunaslight$gradeLegacy(PoseStack matrices, float tickDelta, CallbackInfo ci) {
		ColorGradeHook.renderLegacy(matrices);
	}

	/** 1.15.2는 render(float) 하나 - 행렬 인자가 아예 없다(GUI에 MatrixStack이 들어오기 전). */
	@Inject(method = "render(F)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade1152(float tickDelta, CallbackInfo ci) {
		ColorGradeHook.renderLegacy(new PoseStack());
	}
}
