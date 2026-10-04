package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.util.BlueprintGl14;
import kr.lunaslight.mod.util.BlueprintWorld;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-267차: 1.14.4 설계도 홀로그램을 월드 안에 진짜 블록으로. 이 버전엔 월드 렌더 이벤트도 MatrixStack도 없다.
 * GameRenderer#renderCenter에서 반투명 지형(네 번째 renderLayer)을 그린 바로 뒤 - 손을 그리려고 깊이를 지우기 전 -
 * 에 설계도 모듈의 onWorldRender를 부르고, 쿼드는 BlueprintGl14(Tessellator 직접 그리기)가 받는다. 1.14.4에서만 컴파일된다.
 */
@Mixin(GameRenderer.class)
public abstract class BlueprintWorld14Mixin {

	private static Module lunaslight$blueprint;
	private static final BlueprintGl14 lunaslight$sink = new BlueprintGl14();

	@Inject(method = "renderCenter(FJ)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/Camera;)I",
					ordinal = 3, shift = At.Shift.AFTER),
			require = 0)
	private void lunaslight$blueprintHolo(float tickDelta, long endTime, CallbackInfo ci) {
		try {
			BlueprintWorld.markHooked();
			if (!BlueprintWorld.usable()) {
				return;
			}
			if (lunaslight$blueprint == null) {
				lunaslight$blueprint = ModuleManager.get().find("blueprint").orElse(null);
				if (lunaslight$blueprint == null) {
					return;
				}
			}
			if (!lunaslight$blueprint.isEnabled() || !kr.lunaslight.mod.LunaClientMod.launchOk()) {
				return;
			}
			lunaslight$blueprint.onWorldRender(lunaslight$sink);
		} catch (Throwable t) {
			BlueprintWorld.fail(t);
			LunaCompat.warnOnce("blueprint:mixin14", t);
		}
	}
}
