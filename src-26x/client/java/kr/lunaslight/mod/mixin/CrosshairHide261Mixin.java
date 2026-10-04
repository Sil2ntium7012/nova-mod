package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.impl.render.CustomCrosshairModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-15차: 커스텀 크로스헤어를 쓸 때 바닐라 크로스헤어를 숨김.
 *
 * 예전에는 바닐라 위에 우리 크로스헤어를 "덧그리기"만 해서 두 개가 겹쳐 보였고, 그래서
 * 위치가 어긋나 보였음(바닐라는 15×15 스프라이트, 우리 건 선). 이제 모듈이 켜져 있고
 * "기본 크로스헤어 숨기기" 옵션이 켜져 있으면 바닐라 렌더 자체를 취소.
 *
 * 48차 방식대로 **대상 파라미터를 전부 생략**해서 버전별 시그니처 차이를 무시한다
 * (1.20.x renderCrosshair(DrawContext) / 1.21.x (DrawContext, RenderTickCounter) 등).
 * require = 0이라 이름이 다른 아주 오래된 버전에서는 조용히 미적용(크래시 없음).
 */
// 26.1.x: 인게임 HUD가 아직 Gui 안에 있다(메서드 이름은 26.2 Hud와 동일). 26.2에서는 대상 메서드가 없어 조용히 건너뜀(require=0).
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class CrosshairHide261Mixin {

	@Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideVanillaCrosshair261(CallbackInfo ci) {
		// 49-22차: F3 꾸미기에서 "일반 십자"를 고르면 바닐라 축 십자선도 취소(모듈이 십자를 그림)
		if (kr.lunaslight.mod.module.impl.render.DebugHudStyleModule.wantsNormalCrosshair()
				&& kr.lunaslight.mod.util.LunaCompat.isDebugHudShown(net.minecraft.client.Minecraft.getInstance())) {
			ci.cancel();
			return;
		}
		ModuleManager.get().find("custom_crosshair").ifPresent(module -> {
			if (module instanceof CustomCrosshairModule crosshair && crosshair.shouldHideVanilla()) {
				ci.cancel();
			}
		});
	}
}
