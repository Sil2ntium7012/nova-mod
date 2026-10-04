package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ShieldOffsetHook;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-75차(2-3 뒤 절반): <b>1.15.2 ~ 1.21.8</b>용 방패 높이. 판단과 계산은 전부
 * {@link ShieldOffsetHook}에 있다 - 거기 주석에 <b>왜 이 자리가 안전한지</b>를 적어 뒀다.
 *
 * <h3>시그니처가 딱 둘뿐이었다(javap 실측)</h3>
 * {@code renderFirstPersonItem}은 <b>1.15.2부터 1.21.11까지 인자가 한 칸만 바뀐다</b>:
 *
 * <pre>
 * (AbstractClientPlayerEntity, float, float, Hand, float, ItemStack, float, MatrixStack, ?, int)
 *                                                                                       ↑
 *   1.15.2 ~ 1.21.8   VertexConsumerProvider        ← 이 파일
 *   1.21.9 ~ 1.21.11  OrderedRenderCommandQueue     ← ShieldOffsetQueueMixin
 * </pre>
 *
 * 그 한 칸이 <b>옛 버전에 없는 클래스</b>라 한 파일로는 못 적는다(import부터 깨진다).
 * 그래서 내용이 같은 두 벌을 두고 버전에 맞는 하나만 컴파일한다 - 엔티티 가리기 믹스인
 * (EntityHideMixin / EntityHideManagerMixin)과 똑같은 방식이고, 가르는 곳도 같은 자리다
 * ({@code loom-common.gradle}의 {@code lunaEntityManagerEra}).
 *
 * <p><b>26.x에서는 이 믹스인이 빠진다.</b> 거기서는 메서드 이름 자체가
 * {@code submitArmWithItem}으로 바뀌고 인자도 {@code SubmitNodeCollector}다(javap 실측).
 * 포팅 번역기는 이름이 같은 메서드만 옮길 수 있어서, {@code port26/sync.sh}가 두 파일을 다 뺀다.
 * 모듈도 그 버전에서 카드를 잠근다.
 */
@Mixin(HeldItemRenderer.class)
public abstract class ShieldOffsetMixin {

	private static final String TARGET =
		"renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FF"
			+ "Lnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;F"
			+ "Lnet/minecraft/client/util/math/MatrixStack;"
			+ "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

	@Inject(method = TARGET, at = @At("HEAD"), require = 0)
	private void lunaslight$shieldHead(AbstractClientPlayerEntity player, float tickDelta, float pitch,
			Hand hand, float swingProgress, ItemStack item, float equipProgress,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		ShieldOffsetHook.push(item, matrices);
	}

	@Inject(method = TARGET, at = @At("RETURN"), require = 0)
	private void lunaslight$shieldReturn(AbstractClientPlayerEntity player, float tickDelta, float pitch,
			Hand hand, float swingProgress, ItemStack item, float equipProgress,
			MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		ShieldOffsetHook.pop(matrices);
	}
}
