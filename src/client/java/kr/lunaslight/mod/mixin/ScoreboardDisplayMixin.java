package kr.lunaslight.mod.mixin;

// ⚠️ 컴파일 확인 필요: net.minecraft.client.gui.hud.InGameHud#renderScoreboardSidebar 대상.
// Yarn 1.21.1+build.3 기준 정확한 메서드명/파라미터 타입(예: (DrawContext, ScoreboardObjective)
// 인지, private인지 protected인지)을 100% 확신할 수 없습니다. 컴파일 에러가 나면
// fabric-loom genSources로 InGameHud의 실제 스코어보드 사이드바 렌더 메서드 시그니처를
// 확인해서 method/파라미터 타입을 교체해야 합니다. 메서드 이름 자체가 다르면(예:
// renderScoreboard, drawScoreboard 등) @Inject의 method 값도 함께 바꿔야 합니다.

import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.impl.chat.ScoreboardTweaksModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ScoreboardDisplayMixin {

	@Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
	// 48차: 대상 파라미터 생략 - (DrawContext, ScoreboardObjective)/(DrawContext, RenderTickCounter) 등 버전별 차이 무시.
	// 49-24차: 기능이 켜져 있으면 항상 취소하고 ScoreboardTweaksModule이 HUD 패스에서 직접 그림(숨김이면 안 그림).
	private void lunaslight$renderScoreboardSidebar(CallbackInfo ci) {
		ModuleManager.get().find("scoreboard_tweaks").ifPresent(module -> {
			if (module instanceof ScoreboardTweaksModule tweaks && tweaks.replacesVanilla()) {
				ci.cancel();
			}
		});
	}
}
