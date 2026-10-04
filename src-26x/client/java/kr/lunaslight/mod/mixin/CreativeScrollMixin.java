package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.CreativeScrollState;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-32차: 부드러운 휠 - 크리에이티브 인벤토리. 이 화면은 목록 위젯을 안 써서
 * EntryListScrollMixin이 안 걸린다. 휠을 통째로 받아 두고(취소) 매 프레임 조금씩
 * 진짜 mouseScrolled로 흘려보낸다(CreativeScrollState 설명 참고).
 * mouseScrolled 인자 개수가 1.20.2에서 바뀌어 둘 다 걸어 두고 require = 0.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScrollMixin {


	@Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$smooth(double mouseX, double mouseY, double horizontal, double vertical,
			CallbackInfoReturnable<Boolean> cir) {
		if (CreativeScrollState.intercept(this, mouseX, mouseY, vertical)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void lunaslight$smoothFrame(CallbackInfo ci) {
		CreativeScrollState.frame(this);
	}
}
