package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ToastHook;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-54차(1-3): 토스트 끄기 - 목록에 <b>넣기 전에</b> 막는다. 그리는 쪽을 막으면 자리는 그대로
 * 잡혀 있어 다음 토스트가 아래로 밀리고 소리도 난다.
 *
 * <p>{@code ToastManager.add(Toast)}는 1.15.2 ~ 1.21.11에서 이름·시그니처가 한 번도 안 바뀌었다
 * (javap 실측). 26.x에서는 {@code addToast(Toast)}로 이름이 바뀌었는데, 여기에 둘 다 적으면
 * 26.x 번역기가 둘을 <b>같은 이름으로 바꿔 중복</b>이 되므로 Yarn 이름 하나만 적는다 -
 * 번역기가 26.x용으로 바꿔 준다(포팅 결과에서 {@code addToast}로 나오는 것 확인).
 */
@Mixin(ToastManager.class)
public abstract class ToastFilterMixin {

	@Inject(method = "add(Lnet/minecraft/client/toast/Toast;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$filterToast(Toast toast, CallbackInfo ci) {
		if (ToastHook.shouldHide(toast)) {
			ci.cancel();
		}
	}
}
