package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ClockHook;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x 전용: 시간 고정. 오버월드 시계의 총 틱을 ClockHook.override(0~24000)로 바꾼다 - 하늘·밝기만 바뀌고
 * 서버로는 아무것도 보내지 않는다(예전 버전의 setTimeOfDay와 같은 효과).
 */
@Mixin(ClientClockManager.class)
public abstract class ClockTimeMixin {

	@Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$overrideDayTime(Holder<net.minecraft.world.clock.WorldClock> clock, CallbackInfoReturnable<Long> cir) {
		long override = ClockHook.override;
		if (override < 0 || clock == null) {
			return;
		}
		try {
			if (!clock.is(WorldClocks.OVERWORLD)) {
				return;
			}
		} catch (Throwable ignored) {
			return;
		}
		long current = cir.getReturnValueJ();
		// 하루 단위(24000틱)는 유지하고 하루 안의 시각만 바꾼다(달 위상 등 다른 계산이 덜 흔들리게).
		long day = Math.floorDiv(current, 24000L) * 24000L;
		cir.setReturnValue(day + Math.floorMod(override, 24000L));
	}
}
