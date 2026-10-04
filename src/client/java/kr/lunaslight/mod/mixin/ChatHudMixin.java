package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 49-21차: 채팅 무제한 히스토리 + 시각 표시.
 *
 * ① 히스토리: ChatHud는 messages/visibleMessages를 리터럴 100(MAX_MESSAGES는 static final int라
 *    javac가 바이트코드에 상수로 박아 넣음 - 1.21.11 javap로 addMessage(ChatHudLine)/
 *    addVisibleMessage(ChatHudLine) 둘 다 `bipush 100` 확인)으로 자른다. @ModifyConstant로 그 100을
 *    바꿔치기. 이름만 지정해 모든 오버로드에 걸고(1.20.x/1.16.x는 5-인자/4-인자 addMessage 하나가
 *    두 목록을 다 자름), 상수가 없는 오버로드는 그냥 0건 - require=0.
 * ② 시각: 메시지가 지나가는 길목 하나에서만 Text 인자를 바꿔치기(두 번 붙는 걸 막기 위해 위임
 *    체인의 **한 단계만** 지정): 1.19.1+ 는 addMessage(Text, MessageSignatureData, MessageIndicator)
 *    (addMessage(Text)가 여기로 위임 - 1.21.11 바이트코드 확인), ≤1.18은 addMessage(Text, int)
 *    (addMessage(Text)가 여기로 위임). 그 버전에 없는 시그니처는 조용히 무시 - require=0.
 *    혹시라도 이미 붙은 메시지가 다시 들어오면(재정렬 refresh 경로) 접두를 보고 건너뜀.
 * ③ 49-42차: 같은 길목에서 채팅 강조(ChatState.highlighter)도 먼저 적용 - 모듈 클래스를 직접 참조하지
 *    않고 ChatState의 정적 훅만 부르므로 어떤 버전에서도 믹스인이 깨지지 않는다.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

	private static final DateTimeFormatter LUNA_TIME = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * 49-53차(1-1): 발전 과제 메시지 버리기.
	 *
	 * <p>일부러 <b>인자가 Text 하나뿐인 {@code addMessage(Text)}</b>를 노린다. 여러 인자 오버로드는
	 * 버전마다 인자 타입이 달라(1.19+의 MessageSignatureData·MessageIndicator는 1.18엔 아예 없다)
	 * 공유 소스 한 벌로는 핸들러 시그니처를 맞출 수가 없다. 시스템 메시지(발전 과제가 여기 속한다)는
	 * 이 한 인자짜리 입구로 들어온다 - MessageHandler(1.19+)와 ClientPlayNetworkHandler(~1.18) 둘 다
	 * 여기를 부르는 것을 바이트코드로 확인했다.
	 *
	 * <p>26.x는 이 메서드가 쪼개져 {@code addServerSystemMessage}/{@code addClientSystemMessage}가 됐고
	 * {@code addMessage}는 인자 네 개짜리 private만 남았다(26.1·26.2 javap 실측). 그래서 세 이름을 다
	 * 적어 둔다 - 자기 버전에 없는 이름은 require = 0이라 조용히 빠진다.
	 */
	@Inject(method = {
				"addMessage(Lnet/minecraft/text/Text;)V",
				"addServerSystemMessage(Lnet/minecraft/text/Text;)V",
				"addClientSystemMessage(Lnet/minecraft/text/Text;)V"
			}, at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$dropMessage(Text message, CallbackInfo ci) {
		kr.lunaslight.mod.util.ChatEvents.fromHud(message);   // 49-216차: 옛 판(Fabric 메시지 이벤트 없음)의 채팅 수신 알림
		ChatState.observe(message);   // 49-67차(5-5): 귓속말이면 보낸 사람 기록(알림 기능이 켜져 있을 때만)
		int r = ChatState.gate(message, false);   // 49-76차(6-20): 반복이면 앞 줄에 ×N을 붙이고 이 줄은 버린다
		if (r != ChatState.PASS) {
			ci.cancel();
			if (r == ChatState.DROP_AND_RESET) {
				((ChatHud) (Object) this).reset();   // 앞 줄의 Text를 고쳤으니 다시 접게 한다(1.15.2~1.21.11 전부 public)
			}
		}
	}

	@ModifyConstant(method = {"addMessage", "addVisibleMessage"}, constant = @Constant(intValue = 100), require = 0)
	private int lunaslight$maxMessages(int original) {
		return ChatState.unlimitedHistory ? ChatState.UNLIMITED_LINES : original;
	}

	@ModifyVariable(
			method = {
				"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
				"addMessage(Lnet/minecraft/text/Text;I)V"
			},
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Text lunaslight$timestamp(Text message) {
		if (message == null) {
			return message;
		}
		Text original = message;
		// 49-42차: 채팅 강조(내 이름·키워드 색 + 알림음)는 시각 접두를 붙이기 전 원문에 적용.
		message = ChatState.applyHighlight(message);
		// 49-81차(4-48): 얼굴 띄우개는 시각 접두까지 붙인 뒤에 끼운다(49-208차부터 이름마다 그 앞).
		Text stored = kr.lunaslight.mod.util.ChatFaces.decorate(lunaslight$stamp(message));
		ChatState.replaceKept(original, stored);   // 49-208차: ×N 합치기가 실제로 들어간 쪽에 붙게
		return stored;
	}

	private Text lunaslight$stamp(Text message) {
		if (!ChatState.timestamps) {
			return message;
		}
		try {
			String plain = message.getString();
			if (plain.startsWith("[") && plain.length() > 7 && plain.charAt(3) == ':' && plain.charAt(6) == ']') {
				return message; // 이미 [HH:mm] 접두가 있음
			}
			// 49-22차: 시각 색 설정(ChatState.timestampColor) - RGB 스타일 적용, 실패 시 §7
			Text stamp = LunaCompat.coloredText("[" + LocalTime.now().format(LUNA_TIME) + "] ", ChatState.timestampColor);
			if (stamp == null) {
				stamp = LunaCompat.textLiteral("§7[" + LocalTime.now().format(LUNA_TIME) + "]§r ");
			}
			if (stamp == null) {
				return message;
			}
			// 49-32차 버그 수정(사용자: "채팅 기능 켜면 채팅이 전부 회색이 돼").
			// 예전엔 stamp.append(message)로 붙였는데, 이러면 **시각 텍스트가 부모**가 되어
			// 그 색(회색)이 자식으로 붙은 메시지 전체에 상속된다(자기 색이 없는 글자는 전부 회색).
			// 색이 없는 빈 텍스트를 부모로 두고 [시각]과 원문을 나란히 붙여야 원문 색이 그대로 남는다.
			Text root = LunaCompat.textLiteral("");
			if (root == null) {
				return message;
			}
			java.lang.reflect.Method append = LunaCompat.findMethod(root.getClass(), "append", Text.class);
			if (append == null) {
				return message;
			}
			Object joined = append.invoke(root, stamp);
			Object withMessage = append.invoke(joined instanceof Text t0 ? t0 : root, message);
			return withMessage instanceof Text t ? t : message;
		} catch (Throwable ignored) {
			return message;
		}
	}
}
