package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-215차: CPS 클릭 계수기. 예전엔 GLFW 마우스 콜백을 감쌌는데 26.3은 GLFW가 없다(SDL).
 * onButton(JLMouseButtonInfo;I)V는 26.1~26.3 동일(javap 실측). action 1 = 누름(두 방식 모두).
 */
@Mixin(MouseHandler.class)
public abstract class MouseButtonCountMixin {

	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), require = 0)
	private void lunaslight$countClick(long window, net.minecraft.client.input.MouseButtonInfo info, int action, CallbackInfo ci) {
		if (info != null && action == 1) {
			LunaCompat.countRawClick(LunaInput.toGlfwButton(info.button()));
		}
	}
}
