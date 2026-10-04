package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FireOverlayHook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-69차(2-3의 앞 절반): <b>불에 탈 때 화면을 덮는 불꽃</b>을 위아래로 옮긴다.
 *
 * <p>사용자 요청 2-3 "방패·불 높이 조절" 중 <b>불</b>. 방패 쪽은 49-75차에
 * {@code ShieldOffsetMixin}으로 따로 나왔다. 그때 여기 적어 뒀던 "{@code renderFirstPersonItem}이
 * 거대한 메서드라 위험하다"는 걱정은 <b>틀렸다</b> - 그건 메서드 <b>안쪽</b>에 끼어들 때의 이야기고,
 * 실제로 재 보니 인자가 1.15.2~1.21.11에 걸쳐 <b>한 칸만</b> 바뀌어서 전체를 감싸는 건 안전했다.
 *
 * <p><b>거는 법</b>: 그리기 직전에 행렬을 밀어 두고, 끝나면 되돌린다. 바닐라 메서드 자체는 손대지
 * 않으므로 값이 0이면 예전과 <b>완전히 같은 그림</b>이 나온다.
 *
 * <p><b>시그니처가 셋</b>(javap 실측): ~1.21.5 {@code (MinecraftClient, MatrixStack)} ·
 * 1.21.8 {@code (MatrixStack, VertexConsumerProvider)} · 1.21.11 {@code (MatrixStack,
 * VertexConsumerProvider, Sprite)}. 셋 다 {@code require = 0}이라 자기 버전의 것만 붙는다.
 * 세 시그니처에 나오는 클래스는 <b>전 버전에 다 있어서</b> 공유 소스 한 벌로 적을 수 있다.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.InGameOverlayRenderer")
public abstract class FireOverlayMixin {

	// ---- ~1.21.5 ----
	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;)V",
			at = @At("HEAD"), require = 0)
	private static void lunaslight$fireHead(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		FireOverlayHook.push(matrices);
	}

	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;)V",
			at = @At("RETURN"), require = 0)
	private static void lunaslight$fireReturn(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
		FireOverlayHook.pop(matrices);
	}

	// ---- 1.21.8 ----
	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
			at = @At("HEAD"), require = 0)
	private static void lunaslight$fireHead2(MatrixStack matrices, VertexConsumerProvider vertices, CallbackInfo ci) {
		FireOverlayHook.push(matrices);
	}

	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
			at = @At("RETURN"), require = 0)
	private static void lunaslight$fireReturn2(MatrixStack matrices, VertexConsumerProvider vertices, CallbackInfo ci) {
		FireOverlayHook.pop(matrices);
	}

	// ---- 1.21.11+ ----
	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V",
			at = @At("HEAD"), require = 0)
	private static void lunaslight$fireHead3(MatrixStack matrices, VertexConsumerProvider vertices, Sprite sprite, CallbackInfo ci) {
		FireOverlayHook.push(matrices);
	}

	@Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V",
			at = @At("RETURN"), require = 0)
	private static void lunaslight$fireReturn3(MatrixStack matrices, VertexConsumerProvider vertices, Sprite sprite, CallbackInfo ci) {
		FireOverlayHook.pop(matrices);
	}
}
