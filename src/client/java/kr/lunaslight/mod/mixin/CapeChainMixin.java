package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.CapePhysics;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-242차: 물결치는 망토(CapePhysics). 바닐라 망토 판(10x16x1) 하나를 위에서 아래로 1px 높이 마디 16개 사슬로 바꿔 만들고
 * (텍스처 UV는 바닐라 망토 그대로 한 줄씩), 매 프레임 각 마디를 윗마디 대비 조금씩 굽힌다. 모델 부품만 바뀌어서 바닐라 망토
 * 렌더/텍스처/노바 망토/흔들림 부드럽게가 다 그대로 붙는다.
 * PlayerCapeModel은 1.21.2+에만 있어 그 전 버전은 컴파일에서 뺀다(loom-common.gradle).
 */
@Mixin(PlayerCapeModel.class)
public abstract class CapeChainMixin {

	@Shadow
	@Final
	private ModelPart cape;

	@Unique
	private ModelPart[] lunaslight$segs;

	@Redirect(method = "getTexturedModelData", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/ModelPartData;addChild(Ljava/lang/String;Lnet/minecraft/client/model/ModelPartBuilder;Lnet/minecraft/client/model/ModelTransform;)Lnet/minecraft/client/model/ModelPartData;"),
			require = 0)
	private static ModelPartData lunaslight$chain(ModelPartData parent, String name, ModelPartBuilder builder, ModelTransform transform) {
		if (!"cape".equals(name)) {
			return parent.addChild(name, builder, transform);
		}
		ModelPartData first = parent.addChild(name,
				ModelPartBuilder.create().uv(0, 0).cuboid(-5f, 0f, -1f, 10f, 1f, 1f, Dilation.NONE, 1f, 0.5f), transform);
		ModelPartData seg = first;
		for (int i = 1; i < CapePhysics.SEGMENTS; i++) {
			seg = seg.addChild("s" + i,
					ModelPartBuilder.create().uv(0, i).cuboid(-5f, 0f, -1f, 10f, 1f, 1f, Dilation.NONE, 1f, 0.5f),
					ModelTransform.of(0f, 1f, 0f, 0f, 0f, 0f));
		}
		return first;
	}

	@Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("RETURN"), require = 0)
	private void lunaslight$wave(PlayerEntityRenderState state, CallbackInfo ci) {
		ModelPart[] segs = lunaslight$segs;
		if (segs == null) {
			segs = new ModelPart[CapePhysics.SEGMENTS];
			try {
				ModelPart p = cape;
				for (int i = 1; i < CapePhysics.SEGMENTS; i++) {
					p = p.getChild("s" + i);
					segs[i] = p;
				}
			} catch (Throwable t) {
				segs = new ModelPart[0];   // 사슬이 없다(다른 모드가 망토 모델을 바꿈) - 아무것도 안 한다
			}
			lunaslight$segs = segs;
		}
		if (segs.length < CapePhysics.SEGMENTS) {
			return;
		}
		float lean = state.field_53537;
		float root = 6f + lean / 2f + state.field_53536;
		float[] rel = CapePhysics.step(state.id, root, lean);
		for (int i = 1; i < CapePhysics.SEGMENTS; i++) {
			segs[i].pitch = rel[i];
		}
	}
}
