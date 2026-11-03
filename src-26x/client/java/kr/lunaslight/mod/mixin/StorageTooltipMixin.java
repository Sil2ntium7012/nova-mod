package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.NeogulStorageModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 49-508차: 창고 미리보기 판(과 가린 서버 창) 위에서는 그 아래 서버 칸 설명을 띄우지 않는다. */
@Mixin(AbstractContainerScreen.class)
public abstract class StorageTooltipMixin {

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideUnderStorage(GuiGraphicsExtractor ctx, int mx, int my, CallbackInfo ci) {
		if (NeogulStorageModule.blocks(mx, my)) {
			ci.cancel();
		}
	}
}
