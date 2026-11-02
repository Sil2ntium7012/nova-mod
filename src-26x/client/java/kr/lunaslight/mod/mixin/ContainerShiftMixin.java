package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.inventory.ContainerTabsModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-494차(사용자: "클릭, 휠로 옮기는 거 가운데 상자도 돼야지, 다 같이 움직이고"): 인벤토리 탭 돌림판을 끄는 동안
 * 상자 창 전체(가운데 상자 그림, 칸, 옆 판, 들고 있는 것)를 끈 거리만큼 가로로 옮긴다. 뒤 어두운 막은 그대로 둔다.
 */
@Mixin(Screen.class)
public abstract class ContainerShiftMixin {

	@Unique
	private boolean lunaslight$shifted;

	@Unique
	private boolean lunaslight$bgShifted;

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
	private void lunaslight$shiftHead(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		lunaslight$shifted = false;
		int dx = ContainerTabsModule.shiftX;
		if (dx != 0 && (Object) this instanceof AbstractContainerScreen<?>) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, dx, 0);
			lunaslight$shifted = true;
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"), require = 0)
	private void lunaslight$shiftReturn(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		if (lunaslight$shifted) {
			lunaslight$shifted = false;
			LunaCompat.guiPop(ctx);
		}
	}

	// 뒤 어두운 막(화면 전체)은 되돌려서 제자리에 - 안 그러면 끈 만큼 가장자리에 밝은 띠가 생긴다
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), require = 0)
	private void lunaslight$bgHead(GuiGraphicsExtractor ctx, CallbackInfo ci) {
		lunaslight$bgShifted = false;
		if (lunaslight$shifted) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, -ContainerTabsModule.shiftX, 0);
			lunaslight$bgShifted = true;
		}
	}

	@Inject(method = "extractTransparentBackground", at = @At("RETURN"), require = 0)
	private void lunaslight$bgReturn(GuiGraphicsExtractor ctx, CallbackInfo ci) {
		if (lunaslight$bgShifted) {
			lunaslight$bgShifted = false;
			LunaCompat.guiPop(ctx);
		}
	}
}
