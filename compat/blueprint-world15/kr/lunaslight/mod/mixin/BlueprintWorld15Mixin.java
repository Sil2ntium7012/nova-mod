package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.util.BlueprintWorld;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-267차: 1.15.2 설계도 홀로그램을 월드 안에 진짜 블록으로. 이 버전의 Fabric API(0.28.5)엔 월드 렌더 이벤트가 없어
 * WorldRenderer#render 끝에서 설계도 모듈의 onWorldRender를 직접 부른다(카메라 회전이 걸린 MatrixStack + 엔티티 버퍼).
 * 이벤트 밖이라 BlueprintWorld.end()가 반투명 레이어를 바로 그려 비운다. 1.15.2에서만 컴파일된다(compat/blueprint-world15).
 */
@Mixin(WorldRenderer.class)
public abstract class BlueprintWorld15Mixin {

	private static Module lunaslight$blueprint, lunaslight$wings;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/util/math/Matrix4f;)V",
			at = @At("TAIL"), require = 0)
	private void lunaslight$blueprintHolo(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
			GameRenderer gameRenderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
		if (!kr.lunaslight.mod.LunaClientMod.launchOk()) {
			return;
		}
		Object consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		BlueprintWorld.Ctx15 ctx = new BlueprintWorld.Ctx15(matrices, camera, consumers);
		try {
			BlueprintWorld.markHooked();
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
			LunaCompat.warnOnce("blueprint:mixin15", t);
		}
		// 49-270차: 노바 날개도 같은 자리에서(WingsModule이 Ctx15면 바로 그려 비운다)
		try {
			if (lunaslight$wings == null) {
				lunaslight$wings = ModuleManager.get().find("wings").orElse(null);
			}
			if (lunaslight$wings != null && lunaslight$wings.isEnabled()) {
				lunaslight$wings.onWorldRender(ctx);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("wings:mixin15", t);
		}
	}
}
