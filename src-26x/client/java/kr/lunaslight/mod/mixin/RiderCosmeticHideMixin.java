package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.RiderCosmetics;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 49-406차: 1인칭이면 내 몸에 탄 서버 치장(디스플레이 엔티티)을 안 그린다({@link RiderCosmetics}). */
@Mixin(EntityRenderer.class)
public abstract class RiderCosmeticHideMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideOwnCosmetic(Entity entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (RiderCosmetics.hideInFirstPerson(entity)) {
			cir.setReturnValue(false);
		}
	}
}
