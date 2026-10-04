package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ClockHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-215차: 26.3 시간 고정 - 오버월드 시계(ClockInstanceTrackMixin이 기억한 것)의 totalTicks()만 하루 안의 시각을
 * ClockHook.override로 바꾼다(26.2까지의 ClockTimeMixin과 같은 계산). 이 클래스는 26.3에만 있어서 @Pseudo.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClockInstanceTimeMixin {

	@Inject(method = "totalTicks()J", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$overrideDayTime(CallbackInfoReturnable<Long> cir) {
		long override = ClockHook.override;
		if (override < 0 || ClockHook.overworldInstance != (Object) this) {
			return;
		}
		long current = cir.getReturnValueJ();
		long day = Math.floorDiv(current, 24000L) * 24000L;
		cir.setReturnValue(day + Math.floorMod(override, 24000L));
	}
}
