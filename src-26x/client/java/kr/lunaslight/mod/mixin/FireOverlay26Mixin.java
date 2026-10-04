package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.lunaslight.mod.util.FireOverlayHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차: 26.x 불 화면 높이(FireOverlayModule). 26.x엔 이 믹스인이 없어서 카드가 잠겨 있었다.
 * 26.1~26.1.2 renderFire(PoseStack, MultiBufferSource, Sprite) - 바로 그린다.
 * 26.2~26.3 submitFire(PoseStack, SubmitNodeCollector, Sprite) - 제출할 때 행렬을 복사하므로(SubmitNodeCollection
 * #submitCustomGeometry가 pose.copy(), javap 실측) 앞뒤로 밀고 되돌려도 안전하다.
 */
@Mixin(targets = "net.minecraft.client.renderer.ScreenEffectRenderer")
public abstract class FireOverlay26Mixin {

	@Inject(method = "renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
			at = @At("HEAD"), require = 0)
	private static void lunaslight$fireHead(PoseStack matrices, @Coerce Object buffers, @Coerce Object sprite, CallbackInfo ci) {
		FireOverlayHook.push(matrices);
	}

	@Inject(method = "renderFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
			at = @At("RETURN"), require = 0)
	private static void lunaslight$fireReturn(PoseStack matrices, @Coerce Object buffers, @Coerce Object sprite, CallbackInfo ci) {
		FireOverlayHook.pop(matrices);
	}

	@Inject(method = "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
			at = @At("HEAD"), require = 0)
	private static void lunaslight$fireHead2(PoseStack matrices, @Coerce Object collector, @Coerce Object sprite, CallbackInfo ci) {
		FireOverlayHook.push(matrices);
	}

	@Inject(method = "submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V",
			at = @At("RETURN"), require = 0)
	private static void lunaslight$fireReturn2(PoseStack matrices, @Coerce Object collector, @Coerce Object sprite, CallbackInfo ci) {
		FireOverlayHook.pop(matrices);
	}
}
