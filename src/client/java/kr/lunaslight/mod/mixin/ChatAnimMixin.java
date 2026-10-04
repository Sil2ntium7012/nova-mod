package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatAnim;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-251차: 채팅 애니메이션 - 새 줄이 들어오면 채팅창을 아래에서 부드럽게 올린다(util.ChatAnim).
 *
 * <p>줄 추가: 1.20.5+는 addVisibleMessage(ChatHudLine), 1.20~1.20.4는 private addMessage(5인자)에서 보이는 줄 수가 는다.
 * 그리기: render(DrawContext, int, int, int)(1.20~1.20.4) / (…, boolean)(1.20.5~1.21.10) / (DrawContext, TextRenderer, …)(1.21.11).
 * 그 전 버전(MatrixStack 시대)은 어느 것도 안 맞아 조용히 빠진다(require = 0) - 애니메이션 없이 예전 그대로.
 */
@Mixin(ChatHud.class)
public abstract class ChatAnimMixin {

	@Unique
	private boolean lunaslight$animPushed;

	@Inject(method = {
				"addVisibleMessage",
				"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V"
			}, at = @At("HEAD"), require = 0)
	private void lunaslight$animBefore(CallbackInfo ci) {
		ChatAnim.beforeAdd(this);
	}

	@Inject(method = {
				"addVisibleMessage",
				"addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;ILnet/minecraft/client/gui/hud/MessageIndicator;Z)V"
			}, at = @At("RETURN"), require = 0)
	private void lunaslight$animAfter(CallbackInfo ci) {
		ChatAnim.afterAdd(this);
	}

	@Unique
	private void lunaslight$push(DrawContext ctx) {
		float dy = ChatAnim.offset();
		lunaslight$animPushed = dy > 0.05f && LunaCompat.guiTransformSupported(ctx);
		if (lunaslight$animPushed) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, 0f, dy);
		}
	}

	@Unique
	private void lunaslight$pop(DrawContext ctx) {
		if (lunaslight$animPushed) {
			lunaslight$animPushed = false;
			LunaCompat.guiPop(ctx);
		}
	}

	// 1.20 ~ 1.20.4
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;III)V", at = @At("HEAD"), require = 0)
	private void lunaslight$animPush4(DrawContext ctx, int a, int b, int c, CallbackInfo ci) {
		lunaslight$push(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;III)V", at = @At("RETURN"), require = 0)
	private void lunaslight$animPop4(DrawContext ctx, int a, int b, int c, CallbackInfo ci) {
		lunaslight$pop(ctx);
	}

	// 1.20.5 ~ 1.21.10
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V", at = @At("HEAD"), require = 0)
	private void lunaslight$animPush5(DrawContext ctx, int a, int b, int c, boolean d, CallbackInfo ci) {
		lunaslight$push(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V", at = @At("RETURN"), require = 0)
	private void lunaslight$animPop5(DrawContext ctx, int a, int b, int c, boolean d, CallbackInfo ci) {
		lunaslight$pop(ctx);
	}

	// 1.21.11
	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("HEAD"), require = 0)
	private void lunaslight$animPush7(DrawContext ctx, TextRenderer tr, int a, int b, int c, boolean d, boolean e, CallbackInfo ci) {
		lunaslight$push(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V", at = @At("RETURN"), require = 0)
	private void lunaslight$animPop7(DrawContext ctx, TextRenderer tr, int a, int b, int c, boolean d, boolean e, CallbackInfo ci) {
		lunaslight$pop(ctx);
	}
}
