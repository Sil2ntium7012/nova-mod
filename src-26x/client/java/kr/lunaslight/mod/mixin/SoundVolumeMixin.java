package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SoundFilterHook;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-68차(4-9): 끄기로 한 소리의 <b>크기를 0으로</b> 돌려준다. 그러면 마인크래프트가 스스로
 * 재생을 건너뛴다(자세한 이유는 {@link SoundFilterHook} 주석 - 요약하면 {@code SoundSystem#play}의
 * 반환형이 1.21.8에서 바뀌어 "취소"가 시대를 못 넘기는데, {@code getVolume()}은
 * <b>1.15.2부터 26.2까지 한 글자도 안 바뀌었다</b>).
 *
 * <p>여기는 소리 하나마다(움직이는 소리는 매 틱) 지나가는 자리다. 기능이 꺼져 있으면
 * {@code SoundFilterHook.off} 한 줄만 읽고 나간다.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class SoundVolumeMixin {

	@Inject(method = "getVolume()F", at = @At("RETURN"), cancellable = true)
	private void lunaslight$mute(CallbackInfoReturnable<Float> cir) {
		// (SoundInstance) (Object) this - 믹스인 클래스는 컴파일 시점엔 대상의 인터페이스를 모른다(믹스인 관용구)
		if (!SoundFilterHook.off
				&& SoundFilterHook.muted((net.minecraft.client.resources.sounds.SoundInstance) (Object) this)) {
			cir.setReturnValue(0.0f);
		}
	}
}
