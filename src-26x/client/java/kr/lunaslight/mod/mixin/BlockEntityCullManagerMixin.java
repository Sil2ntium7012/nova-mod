package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.BlockEntityCullHook;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-62차(3-5): 멀리 있는 블록 엔티티는 안 그린다 - <b>1.21.9 ~ 1.21.11 · 26.x</b> 판.
 *
 * <p>1.21.9의 렌더 스테이트 개편으로 두 가지가 바뀌었다:
 * <ul>
 *   <li>클래스 이름 {@code BlockEntityRenderDispatcher} → {@code BlockEntityRenderManager}
 *       (엔티티 쪽이 {@code EntityRenderDispatcher} → {@code EntityRenderManager}가 된 것과 같은 개편)</li>
 *   <li>그리기 인자가 블록 엔티티가 아니라 <b>{@code BlockEntityRenderState}</b>가 되었다 -
 *       자리(pos)는 그 안에 들어 있어서 그대로 쓸 수 있다(javap 실측: {@code public BlockPos pos}).</li>
 * </ul>
 *
 * <p>여기서 취소하면 스테이트를 만드는 비용은 이미 치른 뒤지만(그건 재사용 객체를 채우는 정도다)
 * <b>모델을 커맨드 큐에 얹는 진짜 일</b>은 통째로 건너뛴다.
 *
 * @see BlockEntityCullHook
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullManagerMixin {

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$cullBlockEntity(BlockEntityRenderState state, PoseStack matrices,
			SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
		if (BlockEntityCullHook.isOff() || state == null) {
			return;
		}
		BlockPos pos = state.blockPos;
		if (pos != null && BlockEntityCullHook.shouldSkip(pos.getX(), pos.getY(), pos.getZ())) {
			ci.cancel();
		}
	}
}
