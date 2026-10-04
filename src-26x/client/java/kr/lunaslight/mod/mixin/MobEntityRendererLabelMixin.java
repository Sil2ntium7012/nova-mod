package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.NametagVisibilityModule;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-21차: 몹 렌더러의 hasLabel 훅. MobEntityRenderer는 `super.hasLabel() && (커스텀 이름…)`이라
 * 기본 클래스에서 true를 줘도 이름 없는 몹은 통과 못 함 → 이 오버라이드에서 직접 결정.
 * 49-22차: RETURN + 엔티티 캡처(시야 검사) - LivingEntityRendererLabelMixin 참고.
 */
@Mixin(MobRenderer.class)
public abstract class MobEntityRendererLabelMixin {


	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Mob;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$hasLabelNew(Mob entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		apply(entity, cir);
	}

	private static void apply(Mob entity, CallbackInfoReturnable<Boolean> cir) {
		Boolean decided = NametagVisibilityModule.decide(false, entity, cir.getReturnValueZ());
		if (decided != null && decided != cir.getReturnValueZ()) {
			cir.setReturnValue(decided);
		}
	}
}
