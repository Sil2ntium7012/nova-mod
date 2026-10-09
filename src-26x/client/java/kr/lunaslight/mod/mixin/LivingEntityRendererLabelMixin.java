package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.NametagVisibilityModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-21차: 생명체 렌더러의 hasLabel 훅. 플레이어(≤1.21.8에선 PlayerEntityRenderer가 따로 덮어쓰지
 * 않음)와 몹(MobEntityRenderer가 super로 들어옴)이 함께 지나가므로 렌더러 종류로 갈라 각자의 모드를 적용.
 *
 * 49-22차: "이름표는 벽 너머 볼 수 없게" - 엔티티가 필요해서 HEAD(파라미터 생략) 대신 **RETURN**에서
 * 바닐라 결과를 받아 최종 판정(NametagVisibilityModule.decide: 모드 적용 + 시야 검사)으로 바꾼다.
 * hasLabel 시그니처가 두 시대(≤1.21.1 (T)Z / 1.21.2+ (T,D)Z - tiny 매핑 실측)라 디스크립터를 명시한
 * 핸들러 2개를 두고, 없는 쪽은 require=0으로 조용히 건너뛴다.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLabelMixin {


	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$hasLabelNew(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
		apply(entity, cir);
	}

	private void apply(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		boolean player = (Object) this instanceof AvatarRenderer;
		Boolean decided = NametagVisibilityModule.decide(player, entity, cir.getReturnValueZ());
		if (decided != null && decided != cir.getReturnValueZ()) {
			cir.setReturnValue(decided);
		}
		// 49-312차: 내가 AFK면 3인칭에서 내 머리 위에도 이름표(+ AFK)를 띄운다(바닐라는 내 이름표를 안 그린다)
		if (!cir.getReturnValueZ() && kr.lunaslight.mod.util.AfkWatch.selfLabel(entity)) {
			cir.setReturnValue(true);
		}
	}
}
