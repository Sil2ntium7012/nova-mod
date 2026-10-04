package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ScrollHook;
import kr.lunaslight.mod.util.ZoomState;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-22차: 줌 중 마우스 휠로 줌 정도 조절(사용자 요청). Mouse#onMouseScroll(JDD)V는 1.15.2~1.21.11
 * 전부 같은 시그니처(tiny 매핑 실측). 줌 키를 누르고 있을 때만 휠을 가로채서(핫바 슬롯이 안 바뀌게)
 * ZoomState에 넘기고, 그 외에는 바닐라 그대로.
 * 49-23차: 그 다음 ScrollHook(Alt+휠 핫바 줄 교체, 부드러운 휠)에 묻는다.
 */
@Mixin(Mouse.class)
public abstract class MouseScrollMixin {

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$zoomWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (ZoomState.consumeScroll(vertical) || ScrollHook.dispatch(horizontal, vertical)) {
			ci.cancel();
		}
	}
}
