package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-223차(사용자: "블록 테두리 움직일 때 약간씩 흔들리고 2개씩 보이는 경우가 있어"): 걸을 때 화면 흔들림(bobView)과 맞을 때
 * 기울기(bobHurt)는 월드 투영(projectionMatrix.mul(새 PoseStack))에만 들어가고 우리 HUD 투영(LunaProjection)에는 없었다.
 * 두 메서드가 끝난 직후 그 PoseStack 맨 위 행렬을 기록해 LunaProjection이 같은 변환을 적용하게 한다.
 */
@Mixin(GameRenderer.class)
public abstract class ViewBobCaptureMixin {

	@Inject(method = {
		"bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
		"bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"
	}, at = @At("TAIL"), require = 0)
	private void lunaslight$captureBob(@Coerce Object state, PoseStack poseStack, CallbackInfo ci) {
		try {
			LunaProjection.recordViewBobMatrix(poseStack.last().pose());
		} catch (Throwable ignored) {
		}
	}
}
