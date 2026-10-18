package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.NeogulJei;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-399차(사용자: "아이템 설명이 JEI 위쪽으로 올라가게"): 너굴 JEI 목록을 화면 이벤트(afterExtract)에서 그리면 아이템 설명(툴팁)보다
 * 나중이라 목록이 설명을 덮었다. 이제 화면을 다 그리고 툴팁을 그리기 바로 전에 목록을 그린다 - 설명이 늘 목록 위에 뜬다.
 */
@Mixin(Screen.class)
public abstract class ScreenJeiLayerMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
	private void lunaslight$jeiBeforeTooltip(GuiGraphicsExtractor g, int mx, int my, float d, CallbackInfo ci) {
		NeogulJei.beforeTooltip((Screen) (Object) this, g, mx, my);
	}
}
