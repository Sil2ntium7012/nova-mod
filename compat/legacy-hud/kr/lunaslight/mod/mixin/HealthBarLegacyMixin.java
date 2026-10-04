package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HealthBarHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차: 1.17~1.19.4 하트 한 줄(HealthBarMixin의 옛 시대 판). renderHealthBar(MatrixStack, PlayerEntity, x, y,
 * lines, regen, maxHealth, lastHealth, health, absorption, blinking)이 1.17~1.19.4 그대로다(javap). 1.16은 하트를
 * renderStatusBars 안에서 바로 그려 붙을 곳이 없다(require=0, 바닐라 그대로).
 */
@Mixin(InGameHud.class)
public abstract class HealthBarLegacyMixin {

	@Inject(method = "renderHealthBar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/entity/player/PlayerEntity;IIIIFIIIZ)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$oneRowHealthLegacy(MatrixStack matrices, PlayerEntity player, int x, int y, int lines,
			int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption,
			boolean blinking, CallbackInfo ci) {
		if (HealthBarHook.dispatch(new DrawContext(matrices), x, y, maxHealth, lastHealth, health, absorption)) {
			ci.cancel();
		}
	}
}
