package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.CapePhysics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-242차(26.x 판): 물결치는 망토(CapePhysics). 망토 판 하나를 1px 마디 16개 사슬로 바꿔 만들고 매 프레임 마디마다 굽힌다.
 */
@Mixin(PlayerCapeModel.class)
public abstract class CapeChainMixin {

	@Shadow
	@Final
	private ModelPart cape;

	@Unique
	private ModelPart[] lunaslight$segs;

	@Redirect(method = "createCapeLayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/geom/builders/PartDefinition;addOrReplaceChild(Ljava/lang/String;Lnet/minecraft/client/model/geom/builders/CubeListBuilder;Lnet/minecraft/client/model/geom/PartPose;)Lnet/minecraft/client/model/geom/builders/PartDefinition;"),
			require = 0)
	private static PartDefinition lunaslight$chain(PartDefinition parent, String name, CubeListBuilder builder, PartPose pose) {
		if (!"cape".equals(name)) {
			return parent.addOrReplaceChild(name, builder, pose);
		}
		PartDefinition first = parent.addOrReplaceChild(name,
				CubeListBuilder.create().texOffs(0, 0).addBox(-5f, 0f, -1f, 10f, 1f, 1f, CubeDeformation.NONE, 1f, 0.5f), pose);
		PartDefinition seg = first;
		for (int i = 1; i < CapePhysics.SEGMENTS; i++) {
			// 49-272차: 접히는 축을 두께 가운데(z -0.5)로 - 굽을 때 마디 사이 틈/겹침이 반으로 줄어 이어져 보인다
			seg = seg.addOrReplaceChild("s" + i,
					CubeListBuilder.create().texOffs(0, i).addBox(-5f, 0f, -0.5f, 10f, 1f, 1f, CubeDeformation.NONE, 1f, 0.5f),
					PartPose.offset(0f, 1f, i == 1 ? -0.5f : 0f));
		}
		return first;
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"), require = 0)
	private void lunaslight$wave(AvatarRenderState state, CallbackInfo ci) {
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
				segs = new ModelPart[0];
			}
			lunaslight$segs = segs;
		}
		if (segs.length < CapePhysics.SEGMENTS) {
			return;
		}
		float lean = state.capeLean;
		float root = 6f + lean / 2f + state.capeFlap;
		float[] rel = CapePhysics.step(state.id, root, lean);
		for (int i = 1; i < CapePhysics.SEGMENTS; i++) {
			segs[i].xRot = -rel[i];   // 49-272차: 망토 판은 Y로 180도 돌아 있어 부품 x회전 +가 몸 쪽(처짐)이다
		}
	}
}
