package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ColorGradeHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-42차: 색보정 - HUD를 그리기 직전에 화면 전체를 블렌딩 사각형으로 덮는다(월드에만 적용, HUD는 위에 정상 색).
 *  · 1.20.1~1.20.6: render(DrawContext, float)  · 1.21+: render(DrawContext, RenderTickCounter)  (javap·tiny 실측)
 *  · 1.17~1.19(MatrixStack)·1.16 이하는 대상 없음 → require=0으로 조용히 건너뜀(그 버전은 색보정 미지원)
 */
// 26.1.x: 인게임 HUD가 아직 Gui 안에 있다(메서드 이름은 26.2 Hud와 동일). 26.2에서는 대상 메서드가 없어 조용히 건너뜀(require=0).
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class ColorGrade261Mixin {


	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$grade121261(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
		ColorGradeHook.render(ctx);
	}
}
