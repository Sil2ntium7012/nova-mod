package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FreeView;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
