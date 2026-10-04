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
// 49-216차: 1.16~1.16.4는 같은 클래스가 options(복수형) 패키지에 있다(1.16.5부터 option, merged jar 실측) - 둘 다 적는다.
@Mixin(targets = {"net.minecraft.client.option.KeyBinding", "net.minecraft.client.options.KeyBinding"})
public abstract class KeyBindingAltMixin {

	/** 눌림 상태(걷기·점프처럼 누르고 있는 동안). */
	@Inject(method = "setKeyPressed(Lnet/minecraft/client/util/InputUtil$Key;Z)V", at = @At("HEAD"), require = 0)
	private static void lunaslight$altSet(@Coerce Object key, boolean pressed, CallbackInfo ci) {
		Object real = AltKeys.realFor(key);
		if (real != null) {
			AltKeys.forward(() -> LunaCompat.keyBindingSetPressed(real, pressed));
		}
	}

	/** 눌린 횟수(인벤토리 열기처럼 한 번씩 세는 것). */
	@Inject(method = "onKeyPressed(Lnet/minecraft/client/util/InputUtil$Key;)V", at = @At("HEAD"), require = 0)
	private static void lunaslight$altClick(@Coerce Object key, CallbackInfo ci) {
		Object real = AltKeys.realFor(key);
		if (real != null) {
			AltKeys.forward(() -> LunaCompat.keyBindingOnPressed(real));
		}
	}

	/** 화면이 열린 채 "이 키가 그 키냐"를 묻는 곳(인벤토리 안에서 E로 닫기 등) - ≤1.21.8 시그니처. */
	@Inject(method = "matchesKey(II)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$altMatchesKey(int keyCode, int scanCode, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && !AltKeys.isEmpty()) {
			Object alt = LunaCompat.inputKeyFromLunaCode(keyCode);
			Object real = AltKeys.realFor(alt);
			if (real != null && real.equals(LunaCompat.boundKeyOfBinding(this))) {
				cir.setReturnValue(true);
			}
		}
	}

	/** 49-178차: 1.21.9+ 시그니처 matchesKey(KeyInput) - KeyInput은 옛 버전에 없어 {@code @Coerce Object}. */
	@Inject(method = "matchesKey(Lnet/minecraft/client/input/KeyInput;)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$altMatchesKeyInput(@Coerce Object input, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && !AltKeys.isEmpty()) {
			int keyCode = LunaCompat.callNoArgInt(input, "key", -1);
			if (keyCode == -1) {
				return;
			}
			Object real = AltKeys.realFor(LunaCompat.inputKeyFromLunaCode(keyCode));
			if (real != null && real.equals(LunaCompat.boundKeyOfBinding(this))) {
				cir.setReturnValue(true);
			}
		}
	}

	/** 49-178차: 1.21.9+ 시그니처 matchesMouse(Click). */
	@Inject(method = "matchesMouse(Lnet/minecraft/client/gui/Click;)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$altMatchesClick(@Coerce Object click, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && !AltKeys.isEmpty()) {
			int button = LunaCompat.callNoArgInt(click, "button", -1);
			if (button == -1) {
				return;
			}
			Object real = AltKeys.realFor(LunaCompat.inputKeyFromLunaCode(LunaCompat.mouseKeyCode(button)));
			if (real != null && real.equals(LunaCompat.boundKeyOfBinding(this))) {
				cir.setReturnValue(true);
			}
		}
	}

	@Inject(method = "matchesMouse(I)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$altMatchesMouse(int button, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue() && !AltKeys.isEmpty()) {
			Object alt = LunaCompat.inputKeyFromLunaCode(LunaCompat.mouseKeyCode(button));
			Object real = AltKeys.realFor(alt);
			if (real != null && real.equals(LunaCompat.boundKeyOfBinding(this))) {
				cir.setReturnValue(true);
			}
		}
	}
}
