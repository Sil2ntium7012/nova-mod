package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ColorGradeShader;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-215차: 26.3 색 보정 후처리. 26.3의 GameRenderer#update는 매 프레임 requestedPostEffects 목록을 비우고
 * (프레임 끝 효과 + 플레이어 효과 + 관전 효과)로 다시 채운다 - 그 끝에 우리 효과를 더한다.
 * 26.2까지는 목록이 없어(getRequestedPostEffects 없음) ColorGradeShader가 이 값을 채우지 않는다.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPostMixin {

	private static java.lang.reflect.Method lunaslight$requested;
	private static boolean lunaslight$resolved;

	@Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
	private void lunaslight$addGrade(net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
		Identifier id = ColorGradeShader.requestedPostEffect;
		if (id == null) {
			return;
		}
		try {
			if (!lunaslight$resolved) {
				lunaslight$resolved = true;
				lunaslight$requested = GameRenderer.class.getMethod("getRequestedPostEffects");
			}
			if (lunaslight$requested == null) {
				return;
			}
			@SuppressWarnings("unchecked")
			java.util.List<Identifier> list = (java.util.List<Identifier>) lunaslight$requested.invoke(this);
			if (list != null && !list.contains(id)) {
				list.add(id);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("grade:request", t);
			ColorGradeShader.requestedPostEffect = null;
		}
	}
}
