package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.EntityHideHook;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-59차(3-4 · 1-6): 이름으로 엔티티 안 그리기 - <b>1.21.9 이상</b>판({@link EntityHideMixin}의 짝).
 *
 * <p>1.21.9에서 {@code EntityRenderDispatcher}가 {@code EntityRenderManager}로 <b>이름만</b> 바뀌었다.
 * {@code shouldRender(Entity, Frustum, double, double, double)}는 그대로다(javap 실측) - 그래서 내용은
 * 짝 파일과 똑같고, 가리키는 클래스만 다르다. 두 파일 중 하나만 버전별로 컴파일된다.
 *
 * <p>26.x는 Mojang 이름이 다시 {@code EntityRenderDispatcher}라, 포팅 번역기가 이 파일을 그쪽으로
 * 바꿔 준다(그래서 26.x용 트리에는 이 파일만 들어간다 - port26/sync.sh).
 */
@Mixin(EntityRenderManager.class)
public abstract class EntityHideManagerMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideEntity(Entity entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (EntityHideHook.shouldHide(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
