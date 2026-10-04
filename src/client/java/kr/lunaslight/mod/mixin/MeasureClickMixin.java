package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.MeasureHook;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-41차: 거리 재기 - 막대기를 들고 웅크린 채 좌/우클릭하면 그 클릭을 재기 점 찍기로 먹고 바닐라 동작은 취소.
 *  · doAttack: 1.19+ ()Z / 1.15.2~1.18 ()V (javap·tiny 실측) - 시그니처별로 하나씩, require=0
 *  · doItemUse: ()V 전 버전 동일
 *  · handleBlockBreaking(Z)V: 좌클릭을 누르고 있는 동안 매 틱 블록 깨기를 진행하는 쪽(크리에이티브는 즉시 부서짐) -
 *    doAttack만 막으면 이쪽으로 블록이 깨지므로 같이 막는다.
 */
@Mixin(MinecraftClient.class)
public abstract class MeasureClickMixin {

	@Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$measureAttack(CallbackInfoReturnable<Boolean> cir) {
		if (MeasureHook.attack()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "doAttack()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$measureAttackLegacy(CallbackInfo ci) {
		if (MeasureHook.attack()) {
			ci.cancel();
		}
	}

	@Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$measureUse(CallbackInfo ci) {
		if (MeasureHook.use()) {
			ci.cancel();
		}
	}

	/** 49-253차: 설계도 - 홀로그램 블록을 보고 가운데 클릭하면 그 블록을 인벤토리에서 손으로. */
	@Inject(method = "doItemPick()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$blueprintPick(CallbackInfo ci) {
		if (MeasureHook.pick()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleBlockBreaking(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$measureNoBreak(boolean breaking, CallbackInfo ci) {
		if (breaking && MeasureHook.active()) {
			ci.cancel();
		}
	}
}
