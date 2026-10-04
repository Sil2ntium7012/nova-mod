package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.BackgroundSoundModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-216차: 백그라운드 음소거를 1.21.9 · 1.21.10에서도. 그 두 버전은 "카테고리 볼륨을 이 값으로" 메서드가 없고
 * 소리마다 SoundSystem#getAdjustedVolume(float, SoundCategory)로 옵션 값을 곱해 다시 계산한다(1.21.10 javap).
 * 그 결과를 0으로 바꾸고 updateSoundVolume(카테고리)로 지금 나는 소리도 다시 계산하게 한다 - 옵션 값은 건드리지 않아
 * 음소거 중에 설정이 저장돼도 원래 볼륨이 남는다. 다른 버전은 이 메서드가 없거나 플래그가 안 켜져 그대로다.
 */
@Mixin(targets = "net.minecraft.client.sound.SoundSystem")
public abstract class BackgroundSoundMixin {

	@Inject(method = "getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$backgroundMute(float volume, net.minecraft.sound.SoundCategory category, CallbackInfoReturnable<Float> cir) {
		if (BackgroundSoundModule.mutedByFallback) {
			cir.setReturnValue(0.0f);
		}
	}
}
