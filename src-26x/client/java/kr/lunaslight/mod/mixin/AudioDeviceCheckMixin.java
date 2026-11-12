package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.AudioDeviceGuard;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-643차: 보이스챗 마이크가 OpenAL을 잡고 있는 동안 바닐라 소리 장치 확인(1초마다)을 건너뛴다 - 서버 옮길 때 25초 멈춤(AudioDeviceGuard). */
@Mixin(SoundEngine.class)
public abstract class AudioDeviceCheckMixin {

	@Inject(method = "shouldChangeDevice", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$skipDeviceCheck(CallbackInfoReturnable<Boolean> cir) {
		if (AudioDeviceGuard.skipDeviceCheck()) {
			cir.setReturnValue(false);
		}
	}
}
