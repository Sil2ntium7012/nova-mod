package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaChatTabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-361차: 채팅 탭 상자(사용자 사진 그대로). 채팅 줄보다 먼저 상자·탭 줄·채널 상자를 깔고(맨 앞),
 * 채널 목록은 맨 끝에 위 층으로. 상자가 보일 땐 화면 폭 전체로 깔리는 바닐라 입력칸 배경을 안 그린다.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenTabsMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), require = 0)
	private void lunaslight$tabsPanel(GuiGraphicsExtractor g, int mx, int my, float td, CallbackInfo ci) {
		LunaChatTabs.drawPanel((Screen) (Object) this, g, mx, my);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"), require = 0)
	private void lunaslight$tabsOverlay(GuiGraphicsExtractor g, int mx, int my, float td, CallbackInfo ci) {
		LunaChatTabs.drawOverlay((Screen) (Object) this, g, mx, my);
	}

	@Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), require = 0)
	private void lunaslight$tabsInputBg(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		if (!LunaChatTabs.panelShowing()) {
			g.fill(x0, y0, x1, y1, color);
		}
	}
}
