package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.NametagVisibilityModule;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-21차: 몹 렌더러의 hasLabel 훅. MobEntityRenderer는 `super.hasLabel() && (커스텀 이름…)`이라
 * 기본 클래스에서 true를 줘도 이름 없는 몹은 통과 못 함 → 이 오버라이드에서 직접 결정.
 * 49-22차: RETURN + 엔티티 캡처(시야 검사) - LivingEntityRendererLabelMixin 참고.
 */
@Mixin(MobEntityRenderer.class)
public abstract class MobEntityRendererLabelMixin {

	@Inject(method = "hasLabel(Lnet/minecraft/entity/mob/MobEntity;)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$hasLabelOld(MobEntity entity, CallbackInfoReturnable<Boolean> cir) {
		apply(entity, cir);
	}

	@Inject(method = "hasLabel(Lnet/minecraft/entity/mob/MobEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$hasLabelNew(MobEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		apply(entity, cir);
	}

	private static void apply(MobEntity entity, CallbackInfoReturnable<Boolean> cir) {
		Boolean decided = NametagVisibilityModule.decide(false, entity, cir.getReturnValueZ());
		if (decided != null && decided != cir.getReturnValueZ()) {
			cir.setReturnValue(decided);
		}
	}
}
