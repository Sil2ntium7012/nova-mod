package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.PlayerStateHook;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-201차(26.x 판): 플레이어 렌더 상태를 다 채운 직후 - 망토 흔들림 부드럽게 + 자리 비움 Zzz({@link PlayerStateHook}).
 * 상속 다리 메서드(LivingEntity/Entity 인자)는 빼고 진짜 메서드 하나만.
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRenderStateMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void lunaslight$playerState(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		PlayerStateHook.after(entity, state);
	}
}
