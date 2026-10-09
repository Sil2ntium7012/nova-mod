package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.KeyHook;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaInput;
import kr.lunaslight.mod.util.TextCapture;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-24차: Keyboard#onKey(JIIII)V(1.15.2~1.21.8 동일) HEAD에서 KeyHook에 먼저 묻고, 소비되면 바닐라 처리를
 * 취소(Alt+1~3 핫바 줄 교체 시 바닐라 슬롯 선택이 같이 일어나지 않게). 1.21.9+는 시그니처가 달라 미적용(require=0).
 * 49-215차: 26.x keyPress(JILKeyEvent;)V는 26.1~26.3 동일(javap 실측). 패널 검색창(TextCapture)도 GLFW 콜백 대신
 * 여기서 먼저 받는다(26.3은 GLFW가 없음). KeyEvent의 두 번째 값은 26.2까지 scancode, 26.3 keycode라 이름 없이 읽는다.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardKeyMixin {

	@Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$keyHook(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
		if (event == null) {
			return;
		}
		// 49-313차: 내장 한글 입력(한/영 전환, 조합 중 백스페이스)
		if (kr.lunaslight.mod.util.HangulInput.onKey(window, event.key(), LunaInput.secondCode(event), action, event.modifiers())) {
			ci.cancel();
			return;
		}
		if (LunaCompat.textCaptureInstalled() && TextCapture.feedKey(event.key(), action, event.modifiers())) {
			ci.cancel();
			return;
		}
		if (KeyHook.dispatch(event.key(), LunaInput.secondCode(event), action, event.modifiers())) {
			ci.cancel();
		}
	}
}
