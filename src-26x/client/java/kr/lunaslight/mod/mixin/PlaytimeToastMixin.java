package kr.lunaslight.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-606차(사용자 사진: "1시간 동안 플레이했습니다 / 과도한 게임이용은 정상적인 일상생활에 지장을 줄 수 있습니다" - "이 토스트는 항상 제거"):
 * 바닐라 PeriodicNotificationManager(한국 지역 놀이 시간 알림)의 타이머 일을 아무것도 안 하게 한다. 끄는 설정 없음.
 */
@Mixin(targets = "net.minecraft.client.PeriodicNotificationManager$NotificationTask")
public abstract class PlaytimeToastMixin {

	@Inject(method = "run", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$noPlaytimeToast(CallbackInfo ci) {
		ci.cancel();
	}
}
