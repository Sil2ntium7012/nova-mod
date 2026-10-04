package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SlotDrawHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-42차: 칸 그리기 직전 훅(희귀도 테두리 등).
 *  · 1.20.1~1.21.10: drawSlot(DrawContext, Slot)  · 1.21.11: drawSlot(DrawContext, Slot, int, int)  (javap·tiny 실측)
 *  · 1.19 이하(MatrixStack)는 대상 없음 → require=0
 */
@Mixin(HandledScreen.class)
public abstract class SlotDrawMixin {

	@Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$beforeSlot(DrawContext ctx, Slot slot, CallbackInfo ci) {
		SlotDrawHook.beforeSlot(ctx, slot);
	}

	@Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("HEAD"), require = 0)
	private void lunaslight$beforeSlot12111(DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDrawHook.beforeSlot(ctx, slot);
	}

	@Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;)V", at = @At("RETURN"), require = 0)
	private void lunaslight$afterSlot(DrawContext ctx, Slot slot, CallbackInfo ci) {
		SlotDrawHook.afterSlot(ctx, slot);
	}

	@Inject(method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V", at = @At("RETURN"), require = 0)
	private void lunaslight$afterSlot12111(DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		SlotDrawHook.afterSlot(ctx, slot);
	}
}
