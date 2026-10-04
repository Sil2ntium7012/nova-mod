package kr.lunaslight.mod.util;

/**
 * 49-48차: 날씨 바꾸기 - 내 화면에만 적용되는 날씨(서버에는 아무것도 안 보낸다).
 *
 * 비/천둥은 {@code World#setRainGradient/setThunderGradient}(1.15.2~26.2 이름 동일)를 매 틱 덮어쓰면 된다.
 * <b>눈은 다르다</b> - 마인크래프트는 "비냐 눈이냐"를 날씨가 아니라 <b>바이옴</b>으로 정한다
 * (추운 바이옴이면 눈). 그래서 눈만은 {@code Biome#getPrecipitation}을 가로채야 하고,
 * 그 역할이 {@link kr.lunaslight.mod.mixin.BiomePrecipitationMixin}이다. 이 훅이 그 다리.
 */
public final class WeatherHook {
	private WeatherHook() {
	}

	/** 0 = 끔(바닐라 그대로), 1 = 맑음, 2 = 비, 3 = 눈, 4 = 천둥. */
	public static volatile int mode;

	public static boolean forceSnow() {
		return mode == 3;
	}

	/** 눈으로 고정할 때 바이옴 강수 종류 대신 돌려줄 값(Biome.Precipitation.SNOW). 못 찾으면 null. */
	public static Object snowConstant() {
		Object cached = snow;
		if (cached != null || resolved) {
			return cached;
		}
		resolved = true;
		try {
			Class<?> cls = LunaCompat.classForName("net.minecraft.world.biome.Biome$Precipitation");
			for (Object c : cls.getEnumConstants()) {
				if (LunaCompat.enumNameIs(c, "SNOW")) {
					snow = c;
					break;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("weather:snow", t);
		}
		return snow;
	}

	private static volatile Object snow;
	private static volatile boolean resolved;
}
