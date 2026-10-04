package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.CrosshairOutlineModule;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-22차: 엔티티 윤곽 색. 블록 테두리 모듈이 조준한 엔티티를 setGlowing(true)로 빛나게 하면
 * 바닐라가 윤곽 색을 Entity#getTeamColorValue()에서 읽는다(1.21.11 EntityRenderer 바이트코드:
 * state.outlineColor = hasOutline ? fullAlpha(entity.getTeamColorValue()) : 0). 그 엔티티에 한해
 * 우리 설정 색을 돌려준다(다른 엔티티/팀 색은 그대로).
 */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {

	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$outlineColor(CallbackInfoReturnable<Integer> cir) {
		int color = CrosshairOutlineModule.outlineColorFor(this);
		if (color >= 0) {
			cir.setReturnValue(color);
		}
	}
}
