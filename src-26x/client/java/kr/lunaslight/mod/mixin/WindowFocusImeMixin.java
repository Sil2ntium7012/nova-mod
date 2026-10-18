package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HangulInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-398차: 게임 창 포커스가 바뀔 때(GLFW 포커스 콜백) 바로 윈도우 한/영 상태를 넘겨주고 받는다 - 창을 떠날 땐 노바 한/영 상태대로
 * 입력기를 돌려주고(다른 창이 영어로 바뀌지 않게), 들어올 땐 윈도우 한/영 상태를 가져온다. 틱보다 빨라야 다음 창에 맞게 넘어간다.
 */
@Mixin(targets = "com.mojang.blaze3d.platform.Window")
public abstract class WindowFocusImeMixin {
	@Inject(method = "onFocus(JZ)V", at = @At("TAIL"), require = 0)
	private void lunaslight$focusIme(long handle, boolean focused, CallbackInfo ci) {
		HangulInput.onWindowFocus(focused);
	}
}
