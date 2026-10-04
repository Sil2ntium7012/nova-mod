package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.EntityHideHook;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-59차(3-4 · 1-6): 이름으로 엔티티 안 그리기 - <b>1.15.2 ~ 1.21.8</b>판.
 *
 * <p>바닐라가 "이걸 그릴까?"를 묻는 관문에서 false를 돌려준다({@link EntityHideHook} 주석 참고).
 * 시그니처는 1.15.2부터 1.21.8까지 <b>한 글자도 안 바뀌었다</b>(javap 실측):
 * {@code (Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z}
 *
 * <p>⚠️ <b>1.21.9부터 클래스 이름이 {@code EntityRenderManager}로 바뀌었다</b>(메서드는 그대로).
 * 클래스가 없는 버전에서는 이 파일이 import부터 깨지므로 {@link EntityHideManagerMixin}과
 * 짝을 이뤄 gradle에서 버전별로 하나씩만 컴파일한다(loom-common.gradle의 lunaEntityManagerEra).
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityHideMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideEntity(Entity entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (EntityHideHook.shouldHide(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
