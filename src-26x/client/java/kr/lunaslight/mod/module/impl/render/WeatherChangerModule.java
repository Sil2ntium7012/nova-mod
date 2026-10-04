package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.WeatherHook;

/**
 * 49-48차: 날씨 바꾸기 - 시간 고정의 날씨판. <b>내 화면에만</b> 적용되고 서버에는 아무것도 보내지 않는다.
 *
 * 비·천둥은 {@code World#setRainGradient/setThunderGradient}를 매 틱 덮어쓰면 되고,
 * 눈은 마인크래프트가 바이옴으로 정하기 때문에 {@code BiomePrecipitationMixin}이 같이 필요하다
 * (그 믹스인이 안 붙는 버전에선 눈이 비로 내린다 - 나머지 셋은 전 버전 동작).
 */
public class WeatherChangerModule extends Module {

	public enum Weather {
		CLEAR("맑음"),
		RAIN("비"),
		SNOW("눈"),
		THUNDER("천둥");

		private final String label;

		Weather(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Weather> weather = register(new EnumSetting<>(
			"weather", "날씨", "내 화면의 날씨입니다. 서버에는 영향이 없습니다.", Weather.CLEAR, Weather.class));

	public WeatherChangerModule() {
		super("weather_changer", "날씨 고정", ModuleCategory.VIEW, "내 화면의 날씨 고정");
	}

	@Override
	protected void onDisable() {
		WeatherHook.mode = 0;
	}

	@Override
	public void onTick() {
		if (client == null || client.level == null) {
			WeatherHook.mode = 0;
			return;
		}
		Weather w = weather.get();
		WeatherHook.mode = w.ordinal() + 1;
		// 비·눈은 비 게이지 1, 천둥은 비+천둥 1, 맑음은 둘 다 0.
		float rain = w == Weather.CLEAR ? 0f : 1f;
		float thunder = w == Weather.THUNDER ? 1f : 0f;
		LunaCompat.setWeatherGradients(client.level, rain, thunder);
	}
}
