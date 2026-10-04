package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.lunaslight.mod.util.ShieldOffsetHook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차: 26.3 1인칭 방패 높이. 26.3은 1인칭 손 그리기가 FirstPersonHandsAndItemsRenderer#submitArmWithItem
 * (PlayerRenderState, FirstPersonHandsAndItemsRenderState, float, float, InteractionHand, float, ItemStack, float,
 * PoseStack, SubmitNodeCollector, int)로 옮겨갔다(javap). 26.2까지는 이 클래스가 없어 @Pseudo.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer")
public abstract class ShieldOffset263Mixin {

	private static final String TARGET = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
			+ "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FF"
			+ "Lnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	@Inject(method = TARGET, at = @At("HEAD"), require = 0)
	private void lunaslight$shieldHead(@Coerce Object player, @Coerce Object hands, float a, float b, InteractionHand hand,
			float swing, ItemStack item, float equip, PoseStack matrices, @Coerce Object collector, int light, CallbackInfo ci) {
		ShieldOffsetHook.push(item, matrices);
	}

	@Inject(method = TARGET, at = @At("RETURN"), require = 0)
	private void lunaslight$shieldReturn(@Coerce Object player, @Coerce Object hands, float a, float b, InteractionHand hand,
			float swing, ItemStack item, float equip, PoseStack matrices, @Coerce Object collector, int light, CallbackInfo ci) {
		ShieldOffsetHook.pop(matrices);
	}
}
