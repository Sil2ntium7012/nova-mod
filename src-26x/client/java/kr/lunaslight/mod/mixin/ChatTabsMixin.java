package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaChatTabs;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * 49-360차: 채팅 채널 탭(LunaChatTabs). 채팅창 객체를 잡아 두고, 바닐라가 보이는 줄 필터(채팅 제한)를
 * 넣을 때 탭 필터로 감싼다 - 바닐라 필터 AND 지금 탭.
 * 상자가 보일 때(채팅창 열림 + 이 서버)는 채팅 줄 아래 여백 40을 줄여 줄이 입력칸 바로 위까지 오게 한다
 * (그리기와 클릭 판정이 같은 private extractRenderState를 거쳐서 둘 다 맞게 옮겨진다).
 */
@Mixin(ChatComponent.class)
public abstract class ChatTabsMixin {
	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void lunaslight$tabsInit(CallbackInfo ci) {
		LunaChatTabs.chat = (ChatComponent) (Object) this;
	}

	@ModifyVariable(method = "setVisibleMessageFilter", at = @At("HEAD"), argsOnly = true, require = 0)
	private Predicate<GuiMessage> lunaslight$tabsFilter(Predicate<GuiMessage> filter) {
		return LunaChatTabs.wrap((ChatComponent) (Object) this, filter);
	}

	@ModifyConstant(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
			constant = @Constant(intValue = 40), require = 0)
	private int lunaslight$tabsBottom(int original) {
		return LunaChatTabs.panelShowing() ? LunaChatTabs.PANEL_BOTTOM_MARGIN : original;
	}
}
