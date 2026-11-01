package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.BeetHitboxModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-463차: 사탕무 고르는 칸(외곽 모양)을 한 칸 전체로({@link BeetHitboxModule}). 몸이 부딪히는 모양(충돌)은 그대로 없음. */
@Mixin(net.minecraft.world.level.block.BeetrootBlock.class)
public abstract class BeetrootShapeMixin {
	@Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$bigBeet(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
		if (BeetHitboxModule.on) {
			cir.setReturnValue(Shapes.block());
		}
	}
}
