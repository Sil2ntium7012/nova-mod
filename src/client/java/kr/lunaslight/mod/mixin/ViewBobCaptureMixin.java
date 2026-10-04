package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-223차(사용자: "블록 테두리 움직일 때 약간씩 흔들리고 2개씩 보이는 경우가 있어"): 걸을 때 화면 흔들림(bobView)과 맞을 때
 * 기울기(tiltViewWhenHurt, 1.19.3까지 bobViewWhenHurt)는 월드 투영에만 들어가고 우리 HUD 투영(LunaProjection)에는 없었다.
 * 그래서 걷는 동안 우리 테두리가 바닐라 윤곽과 어긋나 흔들리고 두 겹으로 보였다. 두 메서드가 끝난 직후 그 행렬 스택의 맨 위
 * (= 흔들림 + 기울기만 담긴 새 스택)를 기록해 LunaProjection이 같은 변환을 적용하게 한다.
 * 1.14.4는 MatrixStack이 없어(GL 직접) 대상이 없다 - 전부 require = 0, 스택은 @Coerce Object로 받는다.
 */
@Mixin(GameRenderer.class)
public abstract class ViewBobCaptureMixin {

	@Inject(method = {
		"tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
		"bobViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V",
		"bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V"
	}, at = @At("TAIL"), require = 0)
	private void lunaslight$captureBob(@Coerce Object matrices, float tickDelta, CallbackInfo ci) {
		LunaProjection.recordViewBob(matrices);
	}
}
