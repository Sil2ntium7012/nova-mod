package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.TextCapture;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-215차: 패널 검색창(TextCapture) 글자 입력. 예전엔 GLFW charmods 콜백을 감쌌는데 26.3은 GLFW가 없다(SDL).
 * charTyped(JLCharacterEvent;)V는 26.1~26.3 동일(javap 실측), 26.3의 textInput(문자열)도 글자마다 이리로 온다.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardCharMixin {

	@Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$charHook(long window, net.minecraft.client.input.CharacterEvent event, CallbackInfo ci) {
		if (event != null && LunaCompat.textCaptureInstalled() && TextCapture.feedChar(event.codepoint())) {
			ci.cancel();
		}
	}
}
