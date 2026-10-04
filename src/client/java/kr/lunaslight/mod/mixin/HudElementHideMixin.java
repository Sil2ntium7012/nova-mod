package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HudHideModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-122차: HUD 숨기기(자동)에서 <b>고른 바닐라 요소</b>를 안 그린다.
 *
 * <p>{@link CrosshairHideMixin}과 같은 안전 패턴: 대상 메서드의 파라미터를 전부 생략하고 {@code require = 0}
 * 이라, 렌더 메서드 이름/시그니처가 다른 버전에서는 <b>조용히 미적용</b>된다(크래시·빌드 실패 없음). 루나 정보
 * 숨김은 이것과 무관하게 {@code ModuleManager}가 {@link HudHideModule#hidden}으로 처리한다.
 */
@Mixin(InGameHud.class)
public abstract class HudElementHideMixin {

	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideHotbar(CallbackInfo ci) {
		if (HudHideModule.hideHotbar) {
			ci.cancel();
		}
	}

	// 체력·배고픔·방어구·공기는 바닐라가 한 메서드에서 같이 그린다 - 하나로 묶여 숨겨진다.
	@Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideStatus(CallbackInfo ci) {
		if (HudHideModule.hideStatus) {
			ci.cancel();
		}
	}

	/** 49-195차: [그래픽] 비네팅 끄기 - 화면 가장자리 어두운 테두리. */
	@Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideVignette(CallbackInfo ci) {
		if (kr.lunaslight.mod.module.impl.render.VignetteModule.isHidden()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideExp(CallbackInfo ci) {
		if (HudHideModule.hideExp) {
			ci.cancel();
		}
	}

	/**
	 * 49-178차(전 버전 점검): 1.21.6+는 경험치 막대가 Bar 객체(ExperienceBar)로 바뀌어 renderExperienceBar가 없다 -
	 * 막대 자체는 {@link ExperienceBarHideMixin}이 끄고, 레벨 숫자(정적 Bar.drawExperienceLevel)는 여기서 건너뛴다.
	 * 숨김이 아니면 원래 메서드를 이름으로 불러 그대로 그린다(그 클래스가 옛 버전엔 없어 직접 못 적는다).
	 */
	@Redirect(method = "renderMainHud", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"),
			require = 0)
	private void lunaslight$expLevel(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.font.TextRenderer tr, int level) {
		if (HudHideModule.hideExp) {
			return;
		}
		kr.lunaslight.mod.util.ExpLevelDraw.draw(ctx, tr, level);
	}
}
