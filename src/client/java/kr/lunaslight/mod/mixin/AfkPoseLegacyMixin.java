package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.AfkWatch;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-312차: 1.21.1 이하(렌더 상태가 없는 버전)에서 AFK 플레이어를 그리는 동안만 - 잠듦이면 고개(pitch)를 아래로 바꿔 두고
 * 다 그린 뒤 되돌린다(시점 카메라는 이미 정해진 뒤라 안 흔들린다). 이름표의 AFK도 이 구간에서만 붙는다(AfkPlayerMixin).
 * 1.21.2+는 render(…)에 yaw 인자가 없어 여기 안 걸리고, 렌더 상태(PlayerStateHook)가 맡는다. 1.21.9+는 클래스 이름도 달라
 * 글자로 대상을 적었다(@Pseudo - 없으면 조용히 빠진다).
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.render.entity.EntityRenderDispatcher")
public abstract class AfkPoseLegacyMixin {

	// 1.15 ~ 1.21.1
	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), require = 0)
	private void lunaslight$afkBegin(Entity entity, double x, double y, double z, float yaw, float tickDelta,
			@Coerce Object matrices, @Coerce Object consumers, int light, CallbackInfo ci) {
		AfkWatch.beginRender(entity);
	}

	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("RETURN"), require = 0)
	private void lunaslight$afkEnd(Entity entity, double x, double y, double z, float yaw, float tickDelta,
			@Coerce Object matrices, @Coerce Object consumers, int light, CallbackInfo ci) {
		AfkWatch.endRender(entity);
	}

	// 1.14.4
	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("HEAD"), require = 0)
	private void lunaslight$afkBegin14(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hideHitbox,
			CallbackInfo ci) {
		AfkWatch.beginRender(entity);
	}

	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("RETURN"), require = 0)
	private void lunaslight$afkEnd14(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hideHitbox,
			CallbackInfo ci) {
		AfkWatch.endRender(entity);
	}
}
