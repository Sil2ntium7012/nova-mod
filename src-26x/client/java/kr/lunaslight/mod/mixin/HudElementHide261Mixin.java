package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.HudHideModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-122차: HUD 숨기기(자동)에서 <b>고른 바닐라 요소</b>를 안 그린다. 26.x 판 - 메서드 이름이 바뀌었다
 * (renderHotbar → extractItemHotbar, renderStatusBars → extractPlayerHealth). 경험치 바는 26.x에서 따로 그리는
 * 메서드가 없어(핫바 장식과 한 메서드) 숨기지 않는다.
 *
 * <p>{@link CrosshairHideMixin}과 같은 안전 패턴: 파라미터를 전부 생략하고 {@code require = 0}이라 이름이 다른
 * 버전에서는 조용히 미적용된다. 26.1.x: 인게임 HUD가 아직 Gui 안에 있다(메서드 이름은 26.2 Hud와 동일). 26.2에서는 대상 메서드가 없어 조용히 건너뜀.
 */
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class HudElementHide261Mixin {

	/** 49-195차: [그래픽] 비네팅 끄기 - 화면 가장자리 어두운 테두리. */
	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideVignette261(CallbackInfo ci) {
		if (kr.lunaslight.mod.module.impl.render.VignetteModule.isHidden()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideHotbar261(CallbackInfo ci) {
		if (HudHideModule.hideHotbar || kr.lunaslight.mod.util.NeogulDungeonHud.active) {
			ci.cancel();
		}
	}

	// 체력, 배고픔, 방어구, 공기는 바닐라가 한 메서드에서 같이 그린다 - 하나로 묶여 숨겨진다.
	@Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideStatus261(CallbackInfo ci) {
		if (HudHideModule.hideStatus || kr.lunaslight.mod.util.NeogulDungeonHud.active) {
			ci.cancel();
		}
	}

	// 49-538차: 너굴 던전 UI가 켜져 있으면 손에 든 아이템 이름과 액션바를 스킬 카드 위로 올린다.
	private static boolean lunaslight$liftA261, lunaslight$liftB261;

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), require = 0)
	private void lunaslight$liftNameHead261(net.minecraft.client.gui.GuiGraphicsExtractor ctx, CallbackInfo ci) {
		lunaslight$liftA261 = kr.lunaslight.mod.util.NeogulDungeonHud.liftBegin(ctx);
	}

	@Inject(method = "extractSelectedItemName", at = @At("RETURN"), require = 0)
	private void lunaslight$liftNameEnd261(net.minecraft.client.gui.GuiGraphicsExtractor ctx, CallbackInfo ci) {
		if (lunaslight$liftA261) {
			lunaslight$liftA261 = false;
			kr.lunaslight.mod.util.NeogulDungeonHud.liftEnd(ctx);
		}
	}

	@Inject(method = "extractOverlayMessage", at = @At("HEAD"), require = 0)
	private void lunaslight$liftBarHead261(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
		lunaslight$liftB261 = kr.lunaslight.mod.util.NeogulDungeonHud.liftBegin(ctx);
	}

	@Inject(method = "extractOverlayMessage", at = @At("RETURN"), require = 0)
	private void lunaslight$liftBarEnd261(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
		if (lunaslight$liftB261) {
			lunaslight$liftB261 = false;
			kr.lunaslight.mod.util.NeogulDungeonHud.liftEnd(ctx);
		}
	}
}
