package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.BlockEntityCullHook;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-62차(3-5): 멀리 있는 블록 엔티티는 안 그린다 - <b>1.15.2 ~ 1.21.8</b> 판.
 *
 * <p>대상 시그니처는 이 구간 <b>전부에서 동일</b>하다(javap 실측):
 * {@code (Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V}
 * 같은 클래스에 인자 다섯 개짜리 {@code private static render}도 있으므로 <b>시그니처를 다 적어</b> 고른다.
 *
 * <p>1.21.9부터는 클래스 이름이 {@code BlockEntityRenderManager}로 바뀌고 렌더 스테이트 방식이 되어
 * 시그니처 자체가 달라진다 → {@link BlockEntityCullManagerMixin}. 둘 중 버전에 맞는 하나만 컴파일된다
 * (loom-common.gradle의 lunaEntityManagerEra - 49-59차에 만든 분기를 그대로 쓴다).
 *
 * @see BlockEntityCullHook
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullMixin {

	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$cullBlockEntity(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
		if (BlockEntityCullHook.isOff() || blockEntity == null) {
			return;
		}
		BlockPos pos = blockEntity.getPos();
		if (pos != null && BlockEntityCullHook.shouldSkip(pos.getX(), pos.getY(), pos.getZ())) {
			ci.cancel();
		}
	}
}
