package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.RiderCosmetics;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-406차: 플레이어에 탄 디스플레이(서버 치장)는 고정 밝기 대신 그 플레이어 자리 밝기로({@link RiderCosmetics}). */
@Mixin(DisplayRenderer.class)
public abstract class RiderCosmeticLightMixin {
	@Inject(method = "getBlockLightLevel(Lnet/minecraft/world/entity/Display;Lnet/minecraft/core/BlockPos;)I",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$blockLight(Display e, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int v = RiderCosmetics.light(e, false);
		if (v >= 0) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "getSkyLightLevel(Lnet/minecraft/world/entity/Display;Lnet/minecraft/core/BlockPos;)I",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$skyLight(Display e, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int v = RiderCosmetics.light(e, true);
		if (v >= 0) {
			cir.setReturnValue(v);
		}
	}
}
