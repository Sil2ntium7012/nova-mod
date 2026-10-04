package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ZoomState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 48차: 줌 기능의 실제 FOV 반영. GameRenderer#getFov(Camera, float, boolean)의 반환값을
 * ZoomState.applyZoom으로 줄임. 파라미터 시그니처는 1.15.2~1.21.11 전부 동일하고 반환형만
 * double(≤1.21.1) / float(1.21.2+)이라, raw CallbackInfoReturnable로 받아 boxed 타입을 보고
 * 같은 타입으로 되돌려 줌(Mixin이 생성한 코드가 정확히 그 타입으로 언박싱함).
 *
 * 49-21차: 매 호출 ZoomState.tick()으로 프레임 시각 기준 보간(틱 보간의 계단 현상 제거).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void lunaslight$applyZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable cir) {
		ZoomState.tick();
		Object v = cir.getReturnValue();
		// 49-17차 버그 수정: getFov는 changingFov=false로 불리면 **고정 70.0F**를 돌려준다
		// (1.21.11 바이트코드: `if (panorama) return 90; float f = 70; if (changingFov) f = 옵션FOV × 배율;`).
		// 손(1인칭 아이템) 렌더가 false로 부르기 때문에 프레임의 마지막 값을 그냥 기록하면 항상 70이 됐고,
		// 월드가 실제로는 사용자 FOV(예: 90)로 그려져서 아웃라인이 1.43배 크게 나왔다.
		// → 월드 투영에 쓰이는 changingFov=true 호출만 기록.
		if (v instanceof Double d) {
			double zoomed = ZoomState.applyZoom(d);
			if (changingFov) {
				ZoomState.lastFov = zoomed;
			}
			if (zoomed != d) {
				cir.setReturnValue(zoomed);
			}
		} else if (v instanceof Float f) {
			float zoomed = (float) ZoomState.applyZoom(f);
			if (changingFov) {
				ZoomState.lastFov = zoomed;
			}
			if (zoomed != f) {
				cir.setReturnValue(zoomed);
			}
		}
	}
}
