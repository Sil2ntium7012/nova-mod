package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.inventory.ContainerTabsModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 49-502차: 돌림판을 끄는 동안 칸, 글자, 옆 판(칸 그리기 훅), 들고 있는 아이템만 같이 옮긴다({@link ContainerShiftMixin}). */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerContentShiftMixin {

	@Unique
	private boolean lunaslight$contentShifted;

	@Unique
	private boolean lunaslight$carriedShifted;

	@Inject(method = "extractContents", at = @At("HEAD"), require = 0)
	private void lunaslight$contentHead(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		lunaslight$contentShifted = ContainerTabsModule.shiftX != 0;
		if (lunaslight$contentShifted) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, ContainerTabsModule.shiftX, 0);
		}
	}

	@Inject(method = "extractContents", at = @At("RETURN"), require = 0)
	private void lunaslight$contentReturn(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		if (lunaslight$contentShifted) {
			lunaslight$contentShifted = false;
			LunaCompat.guiPop(ctx);
		}
	}

	@Inject(method = "extractCarriedItem", at = @At("HEAD"), require = 0)
	private void lunaslight$carriedHead(GuiGraphicsExtractor ctx, int mx, int my, CallbackInfo ci) {
		lunaslight$carriedShifted = ContainerTabsModule.shiftX != 0;
		if (lunaslight$carriedShifted) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, ContainerTabsModule.shiftX, 0);
		}
	}

	@Inject(method = "extractCarriedItem", at = @At("RETURN"), require = 0)
	private void lunaslight$carriedReturn(GuiGraphicsExtractor ctx, int mx, int my, CallbackInfo ci) {
		if (lunaslight$carriedShifted) {
			lunaslight$carriedShifted = false;
			LunaCompat.guiPop(ctx);
		}
	}
}
