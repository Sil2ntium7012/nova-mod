package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.NovaCapes;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-240차: 노바 망토 - 바닐라 망토 칸의 텍스처만 바꿔치기(NovaCapes).
 *  · 1.20.2~1.21.8 getSkinTextures(), 1.21.9+ getSkin(): 스킨 묶음 레코드의 망토/겉날개 칸 교체(렌더 상태도 이걸 받아 감)
 *  · 1.20.1 이하 getCapeTexture()/getElytraTexture() + canRender…Texture() = true
 * 버전마다 없는 메서드는 require = 0으로 건너뛴다.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class CapeTextureMixin {

	@Inject(method = {"getSkinTextures", "getSkin"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$novaSkin(CallbackInfoReturnable<Object> cir) {
		Object o = cir.getReturnValue();
		Object p = NovaCapes.patchSkin(this, o);
		if (p != o) {
			cir.setReturnValue(p);
		}
	}

	@Inject(method = {"getCapeTexture", "getElytraTexture"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$novaCapeLegacy(CallbackInfoReturnable<Object> cir) {
		Object t = NovaCapes.textureFor(this);
		if (t != null) {
			cir.setReturnValue(t);
		}
	}

	@Inject(method = {"canRenderCapeTexture", "canRenderElytraTexture"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$novaCanRender(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NovaCapes.textureFor(this) != null) {
			cir.setReturnValue(true);
		}
	}
}
