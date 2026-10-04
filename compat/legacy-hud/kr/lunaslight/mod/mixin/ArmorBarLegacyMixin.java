package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ArmorBarHook;
import kr.lunaslight.mod.util.HealthBarHook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * 49-216차: 1.16~1.19.4 갑옷 줄 색(ArmorBarMixin의 옛 시대 판). renderStatusBars(MatrixStack)의 처음 세 drawTexture
 * (MatrixStack, x, y, u, v, w, h)가 갑옷 가득(u=34)/반(25)/빈(16)이다(1.16·1.16.5·1.19.4 javap 실측). 1.19.4만 정적
 * 호출이라 Redirect는 시대마다 손잡이가 달라지므로, 인자만 만지는 ModifyArgs로 한 벌에 받는다 - 우리가 그렸으면
 * 폭/높이를 0으로 바꿔 바닐라 아이콘을 안 보이게 한다. 하트를 한 줄로 줄였으면 갑옷도 그 바로 위로 내린다.
 */
@Mixin(InGameHud.class)
public abstract class ArmorBarLegacyMixin {

	private static final String METHOD = "renderStatusBars(Lnet/minecraft/client/util/math/MatrixStack;)V";
	private static final String TARGET = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V";

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 0), require = 0)
	private void lunaslight$armor0(Args args) {
		lunaslight$armor(args);
	}

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 1), require = 0)
	private void lunaslight$armor1(Args args) {
		lunaslight$armor(args);
	}

	@ModifyArgs(method = METHOD, at = @At(value = "INVOKE", target = TARGET, ordinal = 2), require = 0)
	private void lunaslight$armor2(Args args) {
		lunaslight$armor(args);
	}

	private static void lunaslight$armor(Args args) {
		try {
			MatrixStack matrices = args.get(0);
			int x = args.<Integer>get(1);
			int y = args.<Integer>get(2);
			if (HealthBarHook.compressing()) {
				y = MinecraftClient.getInstance().getWindow().getScaledHeight() - 49;
				args.set(2, y);
			}
			if (ArmorBarHook.dispatchSlot(new DrawContext(matrices), x, y)) {
				args.set(5, 0);
				args.set(6, 0);
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("armorBarLegacy", t);
		}
	}
}
