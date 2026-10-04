package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SmoothScrollState;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-24차: 부드러운 휠(목록). ≤1.21.3의 EntryListWidget#setScrollAmount(D)를 가로채 목표값만 기억하고,
 * renderList(전 버전 공통, 매 프레임)에서 SmoothScrollState가 보간해 진짜 값을 넣는다.
 * 1.21.4+는 설정자가 ScrollableWidget#setScrollY로 올라가서 ScrollableWidgetScrollMixin이 맡는다(require=0).
 */
@Mixin(AbstractSelectionList.class)
public abstract class EntryListScrollMixin {

	@Inject(method = "setScrollAmount(D)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$smoothSet(double amount, CallbackInfo ci) {
		if (SmoothScrollState.intercept(this, amount)) {
			ci.cancel();
		}
	}

	@Inject(method = "extractListItems", at = @At("HEAD"), require = 0)
	private void lunaslight$smoothFrame(CallbackInfo ci) {
		SmoothScrollState.frame(this);
	}
}
