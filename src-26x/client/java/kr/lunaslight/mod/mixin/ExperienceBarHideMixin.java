package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HudHideModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차: 26.x [HUD 숨기기] 경험치 막대. 26.x 트리엔 이 믹스인이 없어 경험치만 안 숨겨졌다.
 * 26.2+: ExperienceBar(막대 바탕 extractBackground + 채움 extractRenderState, javap 실측). 다른 판엔 없는 클래스라 @Pseudo.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.contextualbar.ExperienceBar")
public abstract class ExperienceBarHideMixin {

	@Inject(method = {"extractBackground", "extractRenderState"}, at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideExpBar(CallbackInfo ci) {
		if (HudHideModule.hideExp) {
			ci.cancel();
		}
	}
}
