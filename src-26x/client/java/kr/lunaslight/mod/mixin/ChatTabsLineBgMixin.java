package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaChatTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-361차: 채팅 탭 상자가 보일 때(채팅창 열림 + LUNA FESTIVAL) 줄마다 깔리는 바닐라 검은 배경(ARGB.black)을 끈다 -
 * 상자 한 장이 배경이라 줄 배경이 겹치면 얼룩진다. 색이 있는 칠(스크롤 막대, 서명 표시줄)은 그대로.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatTabsLineBgMixin {
	@Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$noLineBg(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
		if ((color & 0xFFFFFF) == 0 && LunaChatTabs.panelShowing()) {
			ci.cancel();
		}
	}
}
