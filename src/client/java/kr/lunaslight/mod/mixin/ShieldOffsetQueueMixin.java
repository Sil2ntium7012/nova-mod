package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ShieldOffsetHook;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-75차(2-3 뒤 절반): <b>1.21.9 ~ 1.21.11</b>용 방패 높이.
 * {@link ShieldOffsetMixin}과 <b>내용이 같고</b>, 인자 한 칸만 다르다
 * ({@code VertexConsumerProvider} → {@code OrderedRenderCommandQueue}).
 * 왜 두 벌인지는 그쪽 주석에 적어 뒀다.
 */
@Mixin(HeldItemRenderer.class)
public abstract class ShieldOffsetQueueMixin {

	private static final String TARGET =
		"renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF"
			+ "Lnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;F"
			+ "Lnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V";

	@Inject(method = TARGET, at = @At("HEAD"), require = 0)
	private void lunaslight$shieldHead(AbstractClientPlayerEntity player, float tickDelta, float pitch,
			Hand hand, float swingProgress, ItemStack item, float equipProgress,
			MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		ShieldOffsetHook.push(item, matrices);
	}

	@Inject(method = TARGET, at = @At("RETURN"), require = 0)
	private void lunaslight$shieldReturn(AbstractClientPlayerEntity player, float tickDelta, float pitch,
			Hand hand, float swingProgress, ItemStack item, float equipProgress,
			MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
		ShieldOffsetHook.pop(matrices);
	}
}
