package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FreeView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-349차(자유 시점): 마우스로 플레이어를 돌리는 Entity.turn(dx, dy)을 자유 시점이 켜진 동안 가로채 카메라 오프셋만 돌린다
 * (몸과 시선은 그대로 - 배그 ALT 둘러보기). {@link FreeView} 참고.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {

	@Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$freeViewTurn(double dx, double dy, CallbackInfo ci) {
		if (FreeView.active() && FreeView.consumeTurn(this, dx, dy, ((Entity) (Object) this).getXRot())) {
			ci.cancel();
		}
	}

	/**
	 * 49-354차(사용자: "자유시점 시점 돌리면 그 방향에 맞게 방향키도 다르게"): 이동 입력(moveRelative의 Vec3: x = 왼쪽, z = 앞)을 카메라 오프셋만큼
	 * 돌린다 - 바닐라 getInputVector가 몸 yaw로 돌리므로 결과는 카메라가 보는 방향 기준 WASD. 몸은 움직이는 쪽으로 알아서 돈다.
	 */
	@ModifyVariable(method = "moveRelative(FLnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true, require = 0)
	private Vec3 lunaslight$freeViewMove(Vec3 input) {
		float off = FreeView.moveYaw(this);
		if (off == 0f) {
			return input;
		}
		double r = Math.toRadians(off), c = Math.cos(r), s = Math.sin(r);
		return new Vec3(input.x * c - input.z * s, input.y, input.z * c + input.x * s);
	}
}
