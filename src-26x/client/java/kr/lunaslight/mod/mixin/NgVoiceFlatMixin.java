package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.gui.NovaVoiceSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-473차: 너굴마을 보이스(ng-voiceaddon 1.9) 화면을 노바 모양으로({@link NovaVoiceSkin}). 그 모드가 없으면 @Pseudo라 건너뛴다.
 * sprite = 판, 버튼, 스위치 그림 → 노바 둥근 상자. text = 글자색 → 테마 색, 그림자 없음. styled = 그 모드 전용 글꼴(ng_voiceaddon:ui) 대신 기본 글꼴.
 */
@Pseudo
@Mixin(targets = "kr.mcng.voiceaddon.gui.Flat", remap = false)
public abstract class NgVoiceFlatMixin {

	@Inject(method = "sprite(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIII)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$sprite(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, CallbackInfo ci) {
		if (NovaVoiceSkin.sprite(ctx, id, x, y, w, h)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "text(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;IIIZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private static int lunaslight$color(int color) {
		return NovaVoiceSkin.color(color);
	}

	@ModifyVariable(method = "text(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;IIIZ)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private static boolean lunaslight$shadow(boolean shadow) {
		return !NovaVoiceSkin.on && shadow;
	}

	@Inject(method = "styled(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$font(Component c, CallbackInfoReturnable<MutableComponent> cir) {
		if (NovaVoiceSkin.on && c != null) {
			cir.setReturnValue(c.copy());
		}
	}
}
