package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HangulInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 49-390차: 26.1~26.2 바닐라 TextInputManager.setIMEInputMode(boolean)(GLFW_IME 켜고 끄기). 노바 한글 입력이 켜져 있으면
 * 입력칸에서도 윈도우 입력기를 켜지 않는다 - 키는 영문 그대로 오고 한글은 HangulInput이 조합한다(입력기와 둘이 같이 조합하면
 * 자모가 따로 들어가고, 한글 상태에서 키가 안 먹었다). 26.3(SDL)에는 이 메서드가 없어 조용히 건너뛴다(require = 0).
 */
@Mixin(targets = "com.mojang.blaze3d.platform.TextInputManager")
public abstract class TextInputImeMixin {
	@ModifyVariable(method = "setIMEInputMode(Z)V", at = @At("HEAD"), argsOnly = true, require = 0)
	private boolean lunaslight$noOsIme(boolean on) {
		return on && !HangulInput.blockOsIme();
	}

	/** 49-395차: 입력기 상태가 바뀌었다(한/영 키를 윈도우 입력기가 먹고 자기를 켰을 때 포함) - 한/영 누름으로 본다. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "notifyIMEChanged()V", at = @At("TAIL"), require = 0)
	private void lunaslight$imeChanged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		HangulInput.onOsImeChanged();
	}
}
