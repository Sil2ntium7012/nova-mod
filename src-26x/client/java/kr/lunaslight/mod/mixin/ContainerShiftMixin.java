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
 * 상자 창(가운데 상자 그림, 칸, 옆 판, 들고 있는 것)을 끈 거리만큼 가로로 옮긴다. 뒤 어두운 막과 옆 목록은 그대로 둔다.
 */
@Mixin(Screen.class)
public abstract class ContainerShiftMixin {

	// 49-502차(사용자: "드래그하는 거 전체가 아니라 인벤이랑 상자들만 드래그돼야지"): 화면 전체(옆 너굴 목록, 버튼, 툴팁) 대신
	// 상자 창 그림(extractBackground)과 칸/옆 판(AbstractContainerScreen.extractContents, 들고 있는 것)만 옮긴다.
	// extractBackground 안의 뒤 어두운 막은 되돌려 제자리에.

	@Unique
	private boolean lunaslight$bgShifted;

	@Unique
	private boolean lunaslight$dimBack;

	// 상자 그림은 하위 화면(ContainerScreen 등)이 extractBackground를 덮어써서 그린다 - 부르는 자리(extractRenderStateWithTooltipAndSubtitles)를 감싼다
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"), require = 0)
	private void lunaslight$bgHead(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		lunaslight$bgShifted = false;
		int dx = ContainerTabsModule.shiftX;
		if (dx != 0 && (Object) this instanceof AbstractContainerScreen<?>) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, dx, 0);
			lunaslight$bgShifted = true;
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
		shift = At.Shift.AFTER), require = 0)
	private void lunaslight$bgReturn(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
		if (lunaslight$bgShifted) {
			lunaslight$bgShifted = false;
			LunaCompat.guiPop(ctx);
		}
	}

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), require = 0)
	private void lunaslight$dimHead(GuiGraphicsExtractor ctx, CallbackInfo ci) {
		lunaslight$dimBack = false;
		if (lunaslight$bgShifted) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, -ContainerTabsModule.shiftX, 0);
			lunaslight$dimBack = true;
		}
	}

	@Inject(method = "extractTransparentBackground", at = @At("RETURN"), require = 0)
	private void lunaslight$dimReturn(GuiGraphicsExtractor ctx, CallbackInfo ci) {
		if (lunaslight$dimBack) {
			lunaslight$dimBack = false;
			LunaCompat.guiPop(ctx);
		}
	}
}
