package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HarvestModeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-488차: 작물 캐기 모드 - 작물이 아닌 블록은 부수기 시작부터 막는다({@link HarvestModeModule}). 공격(attack)은 안 건드린다. */
@Mixin(MultiPlayerGameMode.class)
public abstract class HarvestModeMixin {

	@Unique
	private static boolean lunaslight$blocked(BlockPos pos) {
		if (!HarvestModeModule.on || pos == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && !HarvestModeModule.breakable(mc.level.getBlockState(pos));
	}

	@Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$harvestStart(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (lunaslight$blocked(pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$harvestContinue(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
		if (lunaslight$blocked(pos)) {
			cir.setReturnValue(false);
		}
	}
}
