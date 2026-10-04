package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HealthBarHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-34차: 하트 한 줄 고정. InGameHud#renderHealthBar(DrawContext, PlayerEntity, x, y, lines, regen,
 * maxHealth, lastHealth, health, absorption, blinking)는 1.20.1~1.21.11 시그니처가 같다(tiny 실측).
 * 최대 체력이 20을 넘어 두 줄이 될 때만 취소하고 SimpleHealthHudModule이 한 줄로 그린다.
 * 1.17~1.19는 첫 인자가 MatrixStack이라 안 맞음 → require = 0으로 조용히 건너뜀(바닐라 그대로).
 *
 * 49-35차: 갑옷 줄도 같이. renderStatusBars는 갑옷을 하트 "줄 수"만큼 위로 띄우므로 하트만 한 줄로 줄이면
 * 하트와 갑옷 사이가 비었다(사용자: "그것도 잡아줘").
 *  · 1.20.5?~1.21.11(1.21.1 야른 문서·1.21.8/9/11 tiny 실측): 갑옷이 별도 renderArmor(ctx, player, y, lines, rowHeight, x)로
 *    분리돼 있고 안에서 y − (lines−1)×rowHeight − 10을 계산 → 호출 인자 lines를 1로 바꿔 준다(ModifyArg).
 *  · 1.20~1.20.4: 갑옷이 renderStatusBars 안에 인라인(23w43a 야른 문서로 확인, renderArmor 없음). 갑옷 y
 *    지역변수(슬롯 17 - 1.20.1·1.20.2·1.20.4 javap 실측, 셋 다 같음)를 저장 직후 바꾼다. 다른 버전에서 슬롯이
 *    다를 수 있어 HealthBarHook이 **1.20.1~1.20.4일 때만** 값을 건드린다.
 *  두 곳 다 renderStatusBars(DrawContext) 시그니처로 못 박아 1.19 이하(MatrixStack)는 건드리지 않는다.
 */
// 26.2: 인게임 HUD가 Gui에서 Hud 클래스로 분리됨. 26.1.x용 쌍둥이(…Mixin261, Gui 대상)가 따로 있다.
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class HealthBarMixin {

	@Inject(method = "extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$oneRowHealth(GuiGraphicsExtractor context, Player player, int x, int y, int lines,
			int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption,
			boolean blinking, CallbackInfo ci) {
		if (HealthBarHook.dispatch(context, x, y, maxHealth, lastHealth, health, absorption)) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/Hud;extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIII)V"),
			index = 3, require = 0)
	private int lunaslight$armorRows(int lines) {
		return HealthBarHook.armorRows(lines);
	}

}
