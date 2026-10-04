package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.lunaslight.mod.util.ShieldOffsetHook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차: 26.1~26.2 1인칭 방패 높이(ShieldOffsetModule). 26.x엔 이 믹스인이 없어서 카드가 잠겨 있었다.
 * ItemInHandRenderer#renderArmWithItem(AbstractClientPlayer, float, float, InteractionHand, float, ItemStack, float,
 * PoseStack, SubmitNodeCollector, int) - 26.1·26.1.2, 26.2는 이름만 submitArmWithItem(인자 같음, javap). 제출은 행렬을 복사하므로 앞뒤로 밀고
 * 되돌리면 된다. 26.3은 FirstPersonHandsAndItemsRenderer로 옮겨가 ShieldOffset263Mixin이 맡는다.
 */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
public abstract class ShieldOffset26Mixin {

	private static final String ARGS = "(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
			+ "Lnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	@Inject(method = {"renderArmWithItem" + ARGS, "submitArmWithItem" + ARGS}, at = @At("HEAD"), require = 0)
	private void lunaslight$shieldHead(@Coerce Object player, float tickDelta, float pitch, InteractionHand hand,
			float swing, ItemStack item, float equip, PoseStack matrices, @Coerce Object collector, int light, CallbackInfo ci) {
		ShieldOffsetHook.push(item, matrices);
	}

	@Inject(method = {"renderArmWithItem" + ARGS, "submitArmWithItem" + ARGS}, at = @At("RETURN"), require = 0)
	private void lunaslight$shieldReturn(@Coerce Object player, float tickDelta, float pitch, InteractionHand hand,
			float swing, ItemStack item, float equip, PoseStack matrices, @Coerce Object collector, int light, CallbackInfo ci) {
		ShieldOffsetHook.pop(matrices);
	}
}
