package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HudHideModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-178차(전 버전 점검): HUD 숨기기의 [경험치 막대]가 1.21.6~1.21.11에서 안 먹던 것.
 * 1.21.6부터 경험치 막대는 InGameHud.renderExperienceBar가 아니라 ExperienceBar(Bar 구현)가 그린다.
 * 클래스는 이름 문자열로 잡아(옛 버전엔 없다) 그 전 버전에선 "대상 없음" 경고만 남고 아무 일도 안 한다.
 * 레벨 숫자는 HudElementHideMixin이 따로 처리한다.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.bar.ExperienceBar")
public abstract class ExperienceBarHideMixin {

	@Inject(method = {"renderBar", "renderAddons"}, at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideExpBar(CallbackInfo ci) {
		if (HudHideModule.hideExp) {
			ci.cancel();
		}
	}
}
