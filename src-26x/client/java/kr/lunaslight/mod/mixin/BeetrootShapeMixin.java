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

/** 49-463차 → 49-476차: 사탕무 고르는 칸(외곽 모양)을 같은 자람 정도의 밀 모양으로({@link BeetHitboxModule}). 몸이 부딪히는 모양(충돌)은 그대로 없음. */
@Mixin(net.minecraft.world.level.block.BeetrootBlock.class)
public abstract class BeetrootShapeMixin {
	@Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$bigBeet(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
		if (BeetHitboxModule.on) {
			// 49-476차(사용자: "다 안 자란 벼도 너무 높으면 안 돼, 밀이랑 똑같게"): 한 칸 전체 → 같은 자람 정도의 밀 모양.
			// 사탕무(너굴 벼) 0~3단계를 밀 0~7단계에 맞춘다: 0→0(2px), 1→2(6px), 2→5(12px), 3→7(16px).
			try {
				net.minecraft.world.level.block.CropBlock beet = (net.minecraft.world.level.block.CropBlock) (Object) this;
				net.minecraft.world.level.block.CropBlock wheat = (net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
				int max = Math.max(1, beet.getMaxAge());
				int age = Math.round(beet.getAge(state) * wheat.getMaxAge() / (float) max);
				cir.setReturnValue(wheat.getStateForAge(age).getShape(level, pos, ctx));
			} catch (Throwable t) {
				cir.setReturnValue(Shapes.block());
			}
		}
	}
}
