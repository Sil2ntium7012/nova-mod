package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FreeView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-349차(자유 시점): 카메라가 플레이어 각도를 읽는 자리(Camera.alignWithEntity의 getViewYRot/getViewXRot - 평소 줄과 마인카트 줄
 * 둘 다)에 {@link FreeView} 오프셋을 더한다. setRotation 호출 자체를 건드리지 않아 앞에서 보는 시점(yaw+180, -pitch)과 잠잘 때 호출에는
 * 영향이 없고, 3인칭 거리 계산(clip)은 바뀐 각도로 그대로 돈다.
 */
@Mixin(Camera.class)
public abstract class CameraFreeViewMixin {

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"), require = 0)
	private float lunaslight$freeViewYaw(Entity entity, float partialTick) {
		return entity.getViewYRot(partialTick) + FreeView.yawOffset();
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"), require = 0)
	private float lunaslight$freeViewPitch(Entity entity, float partialTick) {
		return FreeView.clampPitch(entity.getViewXRot(partialTick) + FreeView.pitchOffset());
	}
}
