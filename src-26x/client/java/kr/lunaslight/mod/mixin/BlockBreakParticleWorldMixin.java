package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ParticleHook;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-64차(3-1): 블록이 깨질 때 튀는 조각을 안 만든다 - <b>{@code ClientWorld}로 옮겨간 뒤</b> 판.
 *
 * <p>대상: {@code addBlockBreakParticles(BlockPos, BlockState)} — <b>1.18부터</b> 이 클래스에도
 * 생겼고 <b>1.21.9부터는 여기에만</b> 있다(javap 실측: 1.15.2·1.16.5에는 없고 1.18.2·1.20.4·1.21.8·
 * 1.21.10에는 있음). 1.21.9의 개편에서 {@code ParticleManager} 쪽이 사라졌다.
 *
 * <p>{@link BlockBreakParticleMixin}과 짝이다. 둘 다 {@code require = 0}이라 버전마다 맞는 쪽만 붙는다.
 *
 * @see ParticleHook
 */
@Mixin(ClientLevel.class)
public class BlockBreakParticleWorldMixin {

	@Inject(method = "addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideBlockBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (ParticleHook.hideBlockBreak) {
			ci.cancel();
		}
	}
}
