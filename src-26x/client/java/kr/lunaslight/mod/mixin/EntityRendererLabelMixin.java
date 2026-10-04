package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.NametagVisibilityModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 이름표 표시 제어 - 기본 클래스(EntityRenderer) 훅.
 *
 * 49-21차: hasLabel은 LivingEntityRenderer / MobEntityRenderer / PlayerEntityRenderer가 각각
 * 덮어쓰고(1.21.11 매핑: method_4055 / method_4071 / method_74935) super를 부른 뒤 자기 조건을
 * AND 하므로, 몹/플레이어는 그 클래스들의 훅(LivingEntityRendererLabelMixin 등)이 맡는다.
 * 여기서는 생명체가 아닌 엔티티(아이템·탈것 등)만 - "숨김"일 때 같이 숨김. 생명체 렌더러에서
 * super로 들어온 호출은 건드리지 않는다(아이템 이름표는 NametagVisibilityModule이 직접 투영해 그림).
 *
 * 48차: 대상 파라미터를 생략(hasLabel(T) / hasLabel(T,double) 버전 차이 흡수).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLabelMixin {

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hasLabel(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof LivingEntityRenderer) {
			return; // 몹/플레이어는 각자의 렌더러 훅이 처리
		}
		if (NametagVisibilityModule.mobMode() == NametagVisibilityModule.Mode.HIDDEN) {
			cir.setReturnValue(false);
		}
	}
}
