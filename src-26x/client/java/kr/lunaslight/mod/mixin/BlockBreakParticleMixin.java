package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ParticleHook;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-64차(3-1): 블록이 깨질 때 튀는 조각을 안 만든다 - <b>{@code ParticleManager}에 있던 시절</b> 판.
 *
 * <p>대상: {@code addBlockBreakParticles(BlockPos, BlockState)} — <b>1.15.2 ~ 1.21.8</b>에서
 * 시그니처가 한 글자도 안 바뀌었다(javap 실측). 1.21.9부터 이 메서드가 {@code ClientWorld}로 옮겨가
 * 여기서는 사라진다 → {@link BlockBreakParticleWorldMixin}.
 *
 * <p><b>gradle 버전 분기를 안 했다</b>: 두 클래스는 모든 버전에 있고 메서드만 있다 없다 하므로
 * {@code require = 0}이면 각 버전에서 맞는 쪽만 붙는다. 1.18~1.21.8은 두 곳에 다 있어 둘 다 붙지만,
 * 먼저 걸리는 쪽에서 끝나므로 결과는 같다.
 *
 * @see ParticleHook
 */
@Mixin(ParticleEngine.class)
public class BlockBreakParticleMixin {

	@Inject(method = "addBlockBreakParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideBlockBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (ParticleHook.hideBlockBreak) {
			ci.cancel();
		}
	}
}
