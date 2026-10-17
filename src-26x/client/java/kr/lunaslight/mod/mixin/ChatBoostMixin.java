package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatState;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-387차(사용자: "채팅 GUI 비율 줄일수록 글씨가 잘 안 보이는데 GUI 비율 2~1이어도 잘 보이게"): GUI 배율 1이면 채팅 배율과 폭을
 * 같이 2배로(= 배율 2에서 보던 크기). 폭/배율 비가 그대로라 한 줄에 들어가는 글자 수와 줄 수는 안 바뀐다.
 * 그리기와 클릭 판정이 둘 다 이 두 값을 쓰므로 같이 맞는다. [채팅] 기본 [작은 배율에서 채팅 크게].
 */
@Mixin(ChatComponent.class)
public abstract class ChatBoostMixin {
	@Inject(method = "getScale()D", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$boostScale(CallbackInfoReturnable<Double> cir) {
		double f = ChatState.chatBoost();
		if (f != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * f);
		}
	}

	@Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$boostWidth(CallbackInfoReturnable<Integer> cir) {
		double f = ChatState.chatBoost();
		if (f != 1.0) {
			cir.setReturnValue((int) Math.round(cir.getReturnValue() * f));
		}
	}
}
