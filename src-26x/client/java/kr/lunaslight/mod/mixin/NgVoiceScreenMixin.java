package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.gui.NovaVoiceSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 49-473차: 너굴마을 보이스 V 화면의 구분선 색을 노바 테마 선 색으로({@link NovaVoiceSkin#color}). */
@Pseudo
@Mixin(targets = "kr.mcng.voiceaddon.gui.NgVoiceScreen", remap = false)
public abstract class NgVoiceScreenMixin {

	@ModifyArg(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4, require = 0)
	private int lunaslight$line(int color) {
		return NovaVoiceSkin.color(color);
	}
}
