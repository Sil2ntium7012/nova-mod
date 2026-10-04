package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.WeatherHook;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-48차: 날씨 바꾸기의 "눈" 전용. 마인크래프트는 비/눈을 <b>바이옴</b>으로 정하므로(추운 곳이면 눈),
 * 눈으로 고정하려면 이 값을 가로채는 수밖에 없다. 날씨 바꾸기가 [눈]일 때만 SNOW를 돌려주고,
 * 그 외에는 손대지 않는다(서버에는 아무 영향 없음 - 클라이언트 렌더링만 본다).
 *
 * 시그니처가 세 시대로 갈린다(실측): 1.16.5 {@code ()} · 1.17~1.21.10 {@code (BlockPos)} ·
 * 1.21.11+ {@code (BlockPos, int)}. 셋 다 require = 0이라 자기 버전에 없는 건 조용히 빠진다.
 */
@Mixin(Biome.class)
public abstract class BiomePrecipitationMixin {

	@Inject(method = "getPrecipitation()Lnet/minecraft/world/biome/Biome$Precipitation;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$snowLegacy(CallbackInfoReturnable<Object> cir) {
		lunaslight$snow(cir);
	}

	@Inject(method = "getPrecipitation(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/biome/Biome$Precipitation;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$snowPos(net.minecraft.util.math.BlockPos pos, CallbackInfoReturnable<Object> cir) {
		lunaslight$snow(cir);
	}

	@Inject(method = "getPrecipitation(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/world/biome/Biome$Precipitation;",
			at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$snowPosInt(net.minecraft.util.math.BlockPos pos, int sea, CallbackInfoReturnable<Object> cir) {
		lunaslight$snow(cir);
	}

	private static void lunaslight$snow(CallbackInfoReturnable<Object> cir) {
		if (!WeatherHook.forceSnow()) {
			return;
		}
		Object snow = WeatherHook.snowConstant();
		if (snow != null) {
			cir.setReturnValue(snow);
		}
	}
}
