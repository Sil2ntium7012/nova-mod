package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.KeyHook;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-24차: Keyboard#onKey(JIIII)V(1.15.2~1.21.8 동일) HEAD에서 KeyHook에 먼저 묻고, 소비되면 바닐라 처리를
 * 취소(Alt+1~3 핫바 줄 교체 시 바닐라 슬롯 선택이 같이 일어나지 않게). 1.21.9+는 시그니처가 달라 미적용(require=0).
 */
@Mixin(Keyboard.class)
public abstract class KeyboardKeyMixin {

	@Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$keyHook(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		if (KeyHook.dispatch(key, scancode, action, modifiers)) {
			ci.cancel();
		}
	}

	/**
	 * 49-178차(전 버전 점검): 1.21.9+는 onKey(long window, int action, KeyInput input)로 바뀌어 위 훅이 안 붙었다
	 * (Alt+1~3 핫바 줄 교체 등 KeyHook 전부가 1.21.9~1.21.11에서 멈춰 있었다). KeyInput은 그 전 버전에 없는 클래스라
	 * {@code @Coerce Object}로 받고 key/scancode/modifiers는 이름으로 읽는다.
	 */
	@Inject(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$keyHookInput(long window, int action, @Coerce Object input, CallbackInfo ci) {
		int key = kr.lunaslight.mod.util.LunaCompat.callNoArgInt(input, "key", -1);
		if (key == -1) {
			return;
		}
		int scancode = kr.lunaslight.mod.util.LunaCompat.callNoArgInt(input, "scancode", 0);
		int modifiers = kr.lunaslight.mod.util.LunaCompat.callNoArgInt(input, "modifiers", 0);
		if (KeyHook.dispatch(key, scancode, action, modifiers)) {
			ci.cancel();
		}
	}
}
