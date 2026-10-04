package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.PlayerStateHook;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-201차: 플레이어 렌더 상태를 다 채운 직후 - 망토 흔들림 부드럽게 + 자리 비움 Zzz({@link PlayerStateHook}).
 * 렌더 상태는 1.21.2+에만 있다. 그 전 버전엔 이 메서드가 없어 require = 0으로 조용히 빠진다.
 * 인자 타입은 버전마다 달라(1.21.2~1.21.8 AbstractClientPlayerEntity, 1.21.9+ PlayerLikeEntity) {@code @Coerce Object}로 받는다.
 * 상속 다리 메서드(LivingEntity/Entity 인자)는 적지 않는다 - 한 프레임에 두 번 불리지 않게.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerRenderStateMixin {

	@Inject(method = {
				"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
				"updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"
			}, at = @At("RETURN"), require = 0)
	private void lunaslight$playerState(@Coerce Object entity, @Coerce Object state, float tickDelta, CallbackInfo ci) {
		PlayerStateHook.after(entity, state);
	}
}
