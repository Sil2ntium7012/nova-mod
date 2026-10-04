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
 * 49-215차: 26.3 시간 고정. 26.3은 ClientClockManager#getTotalTicks가 없어지고 시계마다 ClientClockInstance가
 * totalTicks()를 직접 준다(javap 실측). 여기서 오버월드 시계의 인스턴스를 기억해 두면 ClockInstanceTimeMixin이
 * 그 인스턴스의 값만 바꾼다. 26.2까지는 이 메서드(반환형 ClientClockInstance)가 없어 그냥 넘어간다(require=0).
 */
@Mixin(ClientClockManager.class)
public abstract class ClockInstanceTrackMixin {

	@Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;",
			at = @At("RETURN"), require = 0)
	private void lunaslight$trackOverworld(Holder<net.minecraft.world.clock.WorldClock> clock, CallbackInfoReturnable<Object> cir) {
		try {
			if (clock != null && cir.getReturnValue() != null && clock.is(WorldClocks.OVERWORLD)) {
				ClockHook.overworldInstance = cir.getReturnValue();
			}
		} catch (Throwable ignored) {
		}
	}
}
