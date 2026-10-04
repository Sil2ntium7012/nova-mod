package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.AltKeys;
import kr.lunaslight.mod.util.LunaCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-89차(8-17): 마인크래프트 키마다 보조 키 - {@link AltKeys} 주석 참고.
 * 클래스는 이름 문자열로 잡는다(1.15.2·1.16·1.16.1은 패키지가 options라 그 셋은 빌드에서 뺀다).
 */
@Mixin(targets = "net.minecraft.client.KeyMapping")
public abstract class KeyBindingAltMixin {

	/** 눌림 상태(걷기·점프처럼 누르고 있는 동안). */
	@Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V", at = @At("HEAD"), require = 0)
	private static void lunaslight$altSet(@Coerce Object key, boolean pressed, CallbackInfo ci) {
		Object real = AltKeys.realFor(key);
		if (real != null) {
			AltKeys.forward(() -> LunaCompat.keyBindingSetPressed(real, pressed));
		}
	}

	/** 눌린 횟수(인벤토리 열기처럼 한 번씩 세는 것). */
	@Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V", at = @At("HEAD"), require = 0)
	private static void lunaslight$altClick(@Coerce Object key, CallbackInfo ci) {
		Object real = AltKeys.realFor(key);
		if (real != null) {
			AltKeys.forward(() -> LunaCompat.keyBindingOnPressed(real));
		}
	}

	/** 26.x: 화면이 열린 채 "이 키가 그 키냐"를 묻는 곳 - matches(Key) 한 곳으로 모인다(KeyEvent 판이 여기로 온다). */
	@Inject(method = "matches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$altMatches(@Coerce Object key, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && !AltKeys.isEmpty()) {
			Object real = AltKeys.realFor(key);
			if (real != null && real.equals(LunaCompat.boundKeyOfBinding(this))) {
				cir.setReturnValue(true);
			}
		}
	}
}
