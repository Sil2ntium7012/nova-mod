package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatFaceLines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-81차(4-48): 채팅 얼굴 - 1.20 ~ 1.21.10. 채팅 줄은 결국 {@code DrawContext#drawTextWithShadow(TextRenderer,
 * OrderedText, int, int, int)}로 그려진다(1.20.x는 ChatHud#render 본문, 1.21.6+는 그 안의 람다 - 어느 쪽이든
 * 이 메서드를 거친다, javap 실측). 그래서 ChatHud가 아니라 <b>DrawContext 쪽</b>에 건다 - 람다 이름을 좇지 않아도 된다.
 * 반환형이 1.21.5까지 int, 1.21.6부터 void라 둘 다 적고 require = 0.
 *
 * <p>1.21.11+는 이 메서드를 안 거치고 {@code ChatHud$Hud#text}로 간다 → {@link ChatFaceHudMixin}.
 * 1.16~1.19.4는 DrawContext 자체가 우리 shim이라 이 파일이 빌드에서 빠지고, 대신 ChatHud#render의 drawWithShadow를
 * Redirect하는 compat/chatface-legacy(16)/ChatFaceLegacyMixin이 들어간다(49-82차, loom-common.gradle). 1.15.2는 없음.
 */
@Mixin(DrawContext.class)
public abstract class ChatFaceMixin {

	@Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I",
			at = @At("HEAD"), require = 0)
	private void lunaslight$faceBeforeLineI(TextRenderer tr, OrderedText text, int x, int y, int color,
			CallbackInfoReturnable<Integer> cir) {
		ChatFaceLines.beforeLine((DrawContext) (Object) this, text, x, y, color);
	}

	@Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V",
			at = @At("HEAD"), require = 0)
	private void lunaslight$faceBeforeLineV(TextRenderer tr, OrderedText text, int x, int y, int color, CallbackInfo ci) {
		ChatFaceLines.beforeLine((DrawContext) (Object) this, text, x, y, color);
	}
}
