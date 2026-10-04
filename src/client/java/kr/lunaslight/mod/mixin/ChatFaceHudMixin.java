package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatFaceLines;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-81차(4-48): 채팅 얼굴 - <b>1.21.11+</b>. 채팅이 {@code ChatHud$Backend} 개편으로 DrawContext#drawTextWithShadow를
 * 안 거치고 {@code ChatHud$Hud#text(int y, float opacity, OrderedText)}로 그려진다(javap 실측: x는 항상 0,
 * 자리는 updatePose가 context.getMatrices()에 넣어 둔 변환). 그래서 여기서 같은 context로 (0, y)에 얼굴을 그린다.
 *
 * <p>context 필드는 @Shadow 대신 리플렉션(LunaCompat.findField - 26.x 이름까지 yarnmap으로 푼다)으로 읽는다.
 * 이 내부 클래스는 1.21.11부터 있어서 그 전 버전 빌드에서는 파일째 빠진다(loom-common.gradle lunaChatBackendEra).
 *
 * <p>49-160차(사용자: "채팅 머리 기능 채팅을 열면 안보이고"): 채팅창을 열면 줄을 {@code Hud}가 아니라
 * 마우스를 받는 쪽 backend(1.21.11 {@code ChatHud$Interactable}, 26.x {@code DrawingFocusedGraphicsAccess})가 그린다
 * (javap 실측: ChatHud#render가 focused면 그쪽을 만든다, text/handleMessage 모양과 context/graphics 필드는 같다).
 * 그래서 두 클래스에 같이 건다.
 */
@Mixin(targets = {"net.minecraft.client.gui.hud.ChatHud$Hud", "net.minecraft.client.gui.hud.ChatHud$Interactable"})
public abstract class ChatFaceHudMixin {

	@Inject(method = "text(IFLnet/minecraft/text/OrderedText;)Z", at = @At("HEAD"), require = 0)
	private void lunaslight$faceBeforeLine(int y, float opacity, OrderedText text, CallbackInfoReturnable<Boolean> cir) {
		try {
			java.lang.reflect.Field f = LunaCompat.findField(getClass(), "context");
			if (f == null) {
				f = LunaCompat.findField(getClass(), "graphics");
			}
			Object ctx = f == null ? null : f.get(this);
			if (ctx instanceof DrawContext dc) {
				ChatFaceLines.beforeLine(dc, text, y, opacity);
			}
		} catch (Throwable ignored) {
		}
	}
}
