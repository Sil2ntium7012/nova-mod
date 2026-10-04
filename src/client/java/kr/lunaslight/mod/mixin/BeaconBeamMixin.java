package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.BeaconBeamModule;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-55차(3-3): 신호기 빛기둥 안 그리기.
 *
 * <p>이 렌더러의 render 시그니처는 <b>시대마다 통째로 다르다</b>(javap 실측):
 *
 * <pre>
 *  1.15.2 ~ 1.21.3   render(BeaconBlockEntity, float, MatrixStack, VertexConsumerProvider, int, int)
 *  1.21.4 ~ 1.21.8   render(T extends BlockEntity, float, MatrixStack, VertexConsumerProvider, int, int, Vec3d)
 *  1.21.9 ~ 1.21.11  render(BeaconBlockEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)
 * </pre>
 *
 * 인자 타입을 핸들러에 적으면 그 타입이 없는 버전에서 <b>컴파일이 깨지므로</b>, 인자를 하나도 안 받는
 * 형태({@code (CallbackInfo)})로 만든다. Mixin은 핸들러가 대상 인자를 전부 받거나, 아니면 하나도 안 받는
 * 두 가지를 모두 허용한다(CallbackInjector.checkDescriptor의 getSimpleCallbackDescriptor 분기 - 소스 확인).
 * 어차피 "그릴지 말지"만 정하면 되니 인자가 필요 없다.
 *
 * <p>없는 시그니처는 require = 0이라 조용히 빠진다.
 */
@Mixin(BeaconBlockEntityRenderer.class)
public abstract class BeaconBeamMixin {

	@Inject(method = {
				"render(Lnet/minecraft/block/entity/BeaconBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
				"render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
				"render(Lnet/minecraft/client/render/block/entity/state/BeaconBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"
			}, at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideBeam(CallbackInfo ci) {
		if (BeaconBeamModule.isHidden()) {
			ci.cancel();
		}
	}
}
