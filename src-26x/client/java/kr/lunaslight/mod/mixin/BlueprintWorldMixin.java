package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.util.BlueprintWorld;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-267차: 26.x 설계도 홀로그램을 월드 안에 진짜 블록으로. 26.x엔 우리가 쓰던 Fabric 월드 렌더 이벤트가 없어서, 엔티티를 그릴 거리를
 * 모으는 LevelRenderer#submitEntities(26.1~26.3 공통) 끝에서 설계도 모듈의 onWorldRender를 부른다. 같은 PoseStack(카메라 기준)과
 * SubmitNodeCollector를 넘겨 엔티티와 같은 단계에서 그려지게 한다.
 */
@Mixin(LevelRenderer.class)
public abstract class BlueprintWorldMixin {

	private static Module lunaslight$blueprint, lunaslight$wings;

	@Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
			at = @At("TAIL"), require = 0)
	private void lunaslight$blueprintHolo(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
		if (state == null || state.cameraRenderState == null || !kr.lunaslight.mod.LunaClientMod.launchOk()) {
			return;
		}
		BlueprintWorld.Ctx ctx = new BlueprintWorld.Ctx(pose, state.cameraRenderState.pos, collector);
		try {
			if (BlueprintWorld.usable()) {
				if (lunaslight$blueprint == null) {
					lunaslight$blueprint = ModuleManager.get().find("blueprint").orElse(null);
				}
				if (lunaslight$blueprint != null && lunaslight$blueprint.isEnabled()) {
					lunaslight$blueprint.onWorldRender(ctx);
				}
			}
		} catch (Throwable t) {
			BlueprintWorld.fail(t);
			LunaCompat.warnOnce("blueprint:mixin", t);
		}
		// 49-270차: 노바 날개도 같은 자리에서(26.x엔 우리가 쓰던 월드 그리기 이벤트가 없다)
		try {
			if (lunaslight$wings == null) {
				lunaslight$wings = ModuleManager.get().find("wings").orElse(null);
			}
			if (lunaslight$wings != null && lunaslight$wings.isEnabled()) {
				lunaslight$wings.onWorldRender(ctx);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("wings:mixin", t);
		}
	}
}
