package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FreeView;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-349차(자유 시점): 카메라가 플레이어 각도를 읽는 자리(Camera.update의 focusedEntity.getYaw(tickDelta)/getPitch(tickDelta) -
 * 1.21.x는 마인카트 줄까지 둘)에 {@link FreeView} 오프셋을 더한다. setRotation 호출 자체를 건드리지 않아 앞에서 보는 시점
 * (yaw+180, -pitch)과 잠잘 때 호출에는 영향이 없고, 3인칭 거리 계산(clipToSpace)은 바뀐 각도로 그대로 돈다. 1.14.4~1.21.11 같은 이름.
 */
@Mixin(Camera.class)
public abstract class CameraFreeViewMixin {

	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"), require = 0)
	private float lunaslight$freeViewYaw(Entity entity, float tickDelta) {
		return entity.getYaw(tickDelta) + FreeView.yawOffset();
	}

	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"), require = 0)
	private float lunaslight$freeViewPitch(Entity entity, float tickDelta) {
		return FreeView.clampPitch(entity.getPitch(tickDelta) + FreeView.pitchOffset());
	}
}
