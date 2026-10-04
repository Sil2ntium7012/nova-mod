package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatState;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-58차(1-6): <b>1.19.1부터 생긴 "서명된 플레이어 채팅"</b> 입구에서 버리기.
 *
 * <p>왜 파일을 따로 뺐나: {@link ChatHudMixin}이 잡는 {@code addMessage(Text)} 한 인자짜리에는
 * 시스템 메시지(발전 과제·귓속말·서버가 보낸 줄)만 들어온다. 1.19.1부터 <b>플레이어가 친 채팅</b>은
 * {@code addMessage(Text, MessageSignatureData, MessageIndicator)} 로 따로 들어오는데, 그 두 타입이
 * 1.19 이하에는 <b>아예 없어서</b> 핸들러를 공유 소스 한 벌로는 못 만든다.
 * → 이 파일만 1.19.1 미만에서 컴파일·믹스인 목록에서 뺀다(loom-common.gradle의 lunaNoSignedChat).
 *
 * <p>경계는 실측이다(merged jar javap):
 * <pre>
 *   1.15.2 ~ 1.19    addMessage(Text) public 하나 + private 오버로드   → 모든 채팅이 ChatHudMixin으로
 *   1.19.1 ~ 1.21.11 addMessage(Text, MessageSignatureData, MessageIndicator) public 추가
 *   26.1 · 26.2      addPlayerMessage(Component, MessageSignature, GuiMessageTag)
 * </pre>
 *
 * <p>인자를 다 받는 형태로 쓴다 - 취소 여부를 정하려면 Text가 필요하고, Mixin이 허용하는 "인자 없는
 * 핸들러"로는 Text를 못 받는다(BeaconBeamMixin 주석 참고).
 */
@Mixin(ChatHud.class)
public abstract class ChatSignedMixin {

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$dropSigned(Text message, MessageSignatureData signature, MessageIndicator indicator,
			CallbackInfo ci) {
		kr.lunaslight.mod.util.ChatEvents.fromHud(message);   // 49-216차: 1.19.1~1.19.2 플레이어 채팅 수신 알림
		// 49-76차(6-20): 이 입구로 바로 오는 건 플레이어 채팅이다(시스템 메시지는 한 인자 입구를 거쳐 온다 - 그건
		// gate가 같은 객체인지 보고 두 번 판단하지 않는다). 플레이어 채팅은 ×N 합치기 대상이 아니다.
		int r = ChatState.gate(message, true);
		if (r != ChatState.PASS) {
			ci.cancel();
			if (r == ChatState.DROP_AND_RESET) {
				((ChatHud) (Object) this).reset();
			}
		}
	}
}
