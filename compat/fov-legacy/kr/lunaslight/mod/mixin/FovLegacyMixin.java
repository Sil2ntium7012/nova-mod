package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.FovLockModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-216차: 시야각 효과 끄기를 1.14.4~1.16.5에서도. 그 시대엔 FOV 효과 크기 설정이 없고 GameRenderer#
 * updateMovementFovMultiplier가 플레이어의 속도 배율(getSpeed)을 그대로 시야각에 곱한다(1.16 javap). 잠겨 있으면
 * 그 값을 1로 돌려준다. compat/fov-legacy는 1.16.5 이하 판에만 얹힌다(1.21.11엔 getSpeed 자체가 없다).
 */
@Mixin(GameRenderer.class)
public abstract class FovLegacyMixin {

	@Redirect(method = "updateMovementFovMultiplier", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getSpeed()F"), require = 0)
	private float lunaslight$noSpeedFov(AbstractClientPlayerEntity player) {
		return FovLockModule.legacyLock ? 1.0f : player.getSpeed();
	}
}
