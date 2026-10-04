package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatAnim;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 49-251차: 채팅 애니메이션 - 새 줄이 들어오면 채팅창을 아래에서 부드럽게 올린다(util.ChatAnim). */
@Mixin(ChatComponent.class)
public abstract class ChatAnimMixin {

	@Unique
	private boolean lunaslight$animPushed;

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), require = 0)
	private void lunaslight$animBefore(CallbackInfo ci) {
		ChatAnim.beforeAdd(this);
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"), require = 0)
	private void lunaslight$animAfter(CallbackInfo ci) {
		ChatAnim.afterAdd(this);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("HEAD"), require = 0)
	private void lunaslight$animPush(GuiGraphicsExtractor ctx, Font font, int a, int b, int c,
			ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
		float dy = ChatAnim.offset();
		lunaslight$animPushed = dy > 0.05f && LunaCompat.guiTransformSupported(ctx);
		if (lunaslight$animPushed) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, 0f, dy);
		}
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
			at = @At("RETURN"), require = 0)
	private void lunaslight$animPop(GuiGraphicsExtractor ctx, Font font, int a, int b, int c,
			ChatComponent.DisplayMode mode, boolean d, CallbackInfo ci) {
		if (lunaslight$animPushed) {
			lunaslight$animPushed = false;
			LunaCompat.guiPop(ctx);
		}
	}
}
