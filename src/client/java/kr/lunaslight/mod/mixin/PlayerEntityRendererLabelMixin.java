package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.NametagVisibilityModule;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-21차: 플레이어 렌더러의 hasLabel 훅(1.21.9+에서 새로 생긴 오버라이드 - method_74935; 파라미터
 * 타입 PlayerLikeEntity가 그 버전에만 있어 캡처 불가 → 파라미터 생략). 오버라이드가 없는 구버전에서는
 * 대상이 없어 조용히 미적용(require=0) - 그땐 LivingEntityRendererLabelMixin이 플레이어를 맡는다.
 *
 * 49-22차: 1.21.9+의 이 오버라이드는 `super.hasLabel(...) && (…)` 형태라(바이트코드 확인) 시야 검사는
 * super 쪽(LivingEntityRendererLabelMixin, 엔티티 캡처)에서 이미 반영된다. 여기서는 RETURN에서
 * "항상" 모드만 보정한다 - 단, super가 시야 검사로 false를 준 경우는 유지해야 하므로 super 결과를
 * 되묻는 대신 NametagVisibilityModule의 마지막 판정(lastLivingDecision)을 쓴다.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererLabelMixin {

	@Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$hasLabel(CallbackInfoReturnable<Boolean> cir) {
		NametagVisibilityModule.Mode mode = NametagVisibilityModule.playerMode();
		if (mode == NametagVisibilityModule.Mode.HIDDEN) {
			cir.setReturnValue(false);
		} else if (mode == NametagVisibilityModule.Mode.ALWAYS && !cir.getReturnValueZ()
				&& NametagVisibilityModule.lastLivingDecision()) {
			cir.setReturnValue(true);
		}
	}
}
