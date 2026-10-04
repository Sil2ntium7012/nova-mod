package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ParticleHook;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-53차(3-2 · 3-7): 입자를 <b>만들기 전에</b> 막는다.
 *
 * <p>대상 시그니처 {@code addParticle(ParticleEffect, double×6)}는 1.15.2~1.21.11에서 동일하다
 * (javap 실측). 취소하면 null을 돌려주는데, 바닐라 호출부는 이 반환값을 쓰지 않는다.
 * 판단 자체는 {@link ParticleHook}가 하고, 아무것도 막지 않는 평소에는 {@code active()} 한 번만 본다.
 */
@Mixin(ParticleEngine.class)
public class ParticleFilterMixin {

	@Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$filterParticle(ParticleOptions parameters, double x, double y, double z,
			double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
		if (!ParticleHook.active() || parameters == null) {
			return;
		}
		try {
			if (ParticleHook.shouldHide(parameters.getType())) {
				cir.setReturnValue(null);
			}
		} catch (Throwable ignored) {
			// 종류를 못 읽으면 그냥 통과시킨다(입자가 안 보이는 것보다 낫다)
		}
	}
}
