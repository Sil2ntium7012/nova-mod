package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.render.DebugHudStyleModule;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-22차: F3 꾸미기. DebugHudStyleModule이 켜져 있으면 바닐라 F3 렌더(DebugHud#render - 1.20+
 * (DrawContext), ≤1.19 (MatrixStack); 파라미터 생략으로 흡수)를 통째로 취소하고 모듈이 HUD 패스에서
 * 골라 그린다. 차트(F3+1/2/3)와 하단 안내문도 같이 사라진다.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$replaceDebugHud(CallbackInfo ci) {
		if (DebugHudStyleModule.replacesVanilla()) {
			ci.cancel();
		}
	}

	/**
	 * 49-34차(사용자: "F3 일반 십자를 켜면 기존에 그 축이 안보여야지"): 1.21.9+는 F3 축 십자선이
	 * InGameHud#renderCrosshair 안이 아니라 DebugHud#renderDebugCrosshair(Camera)로 따로 빠졌다.
	 * 그래서 CrosshairHideMixin만으로는 축이 그대로 남았음 - 여기서도 같이 취소한다.
	 * (그 이전 버전엔 이 메서드가 없어 require = 0으로 조용히 건너뜀)
	 */
	@Inject(method = "renderDebugCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideDebugAxis(CallbackInfo ci) {
		if (DebugHudStyleModule.wantsNormalCrosshair()) {
			ci.cancel();
		}
	}
}
