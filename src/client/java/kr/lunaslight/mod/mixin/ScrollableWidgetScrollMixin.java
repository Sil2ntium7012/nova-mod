package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SmoothScrollState;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-24차: 부드러운 휠 - 1.21.4+에서 목록의 스크롤 설정자가 ScrollableWidget#setScrollY(D)로 올라감.
 * 목록(EntryListWidget)일 때만 가로챈다(글상자 등 다른 스크롤 위젯은 프레임 훅이 없어 바닐라 그대로).
 * ScrollableWidget이 없는 구버전(≤1.19.3)은 required=false 설정으로 조용히 건너뜀.
 * 49-36차: 클래스 리터럴 대신 이름 문자열(targets) - 그 클래스가 없는 1.15.2~1.19.3에서도 컴파일되게.
 */
@Mixin(targets = "net.minecraft.client.gui.widget.ScrollableWidget")
public abstract class ScrollableWidgetScrollMixin {

	@Inject(method = "setScrollY(D)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$smoothSet(double y, CallbackInfo ci) {
		if ((Object) this instanceof EntryListWidget && SmoothScrollState.intercept(this, y)) {
			ci.cancel();
		}
	}
}
