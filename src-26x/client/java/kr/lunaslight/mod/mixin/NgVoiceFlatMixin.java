package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.gui.NovaVoiceSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-473차: 너굴마을 보이스(ng-voiceaddon 1.9) 화면을 노바 모양으로({@link NovaVoiceSkin}). 그 모드가 없으면 @Pseudo라 건너뛴다.
 * sprite = 판, 버튼, 스위치 그림 → 노바 둥근 상자. text = 글자색 → 테마 색, 그림자 없음.
 * 49-489차(사용자 사진: V 화면 글씨가 번지고 겹쳐 보임 - "글씨체 좀"): 전용 글꼴(ng_voiceaddon:ui)을 기본 글꼴로 바꾸던 것을 뺐다 - 그 모드 글꼴 그대로.
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

	// 49-489차(사용자: "글이 너무 안 보여"): 가운데 맞춤 글자(버튼, 탭 설명 …)는 Flat.text를 안 거치고 바로 그려서 원래 흰 글자 + 그림자가
	// 밝은 판 위에 그대로 나왔다(번지고 안 보임). centered, centeredClipped의 색도 테마 색으로 - 그림자는 그 모드가 색이 어두우면 끈다.
	@ModifyVariable(method = "centered(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;III)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
	private static int lunaslight$centeredColor(int color) {
		return NovaVoiceSkin.color(color);
	}

	@ModifyVariable(method = "centeredClipped(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Component;IIII)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
	private static int lunaslight$clippedColor(int color) {
		return NovaVoiceSkin.color(color);
	}
}
