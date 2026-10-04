package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SlotDrawHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-42차: 칸 그리기 직전 훅(희귀도 테두리 등).
 *  · 1.20.1~1.21.10: drawSlot(DrawContext, Slot)  · 1.21.11: drawSlot(DrawContext, Slot, int, int)  (javap·tiny 실측)
 *  · 1.19 이하(MatrixStack)는 대상 없음 → require=0
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotDrawMixin {


	@Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V", at = @At("HEAD"), require = 0)
	private void lunaslight$beforeSlot12111(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDrawHook.beforeSlot(ctx, slot);
	}


	@Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V", at = @At("RETURN"), require = 0)
	private void lunaslight$afterSlot12111(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDrawHook.afterSlot(ctx, slot);
	}
}
