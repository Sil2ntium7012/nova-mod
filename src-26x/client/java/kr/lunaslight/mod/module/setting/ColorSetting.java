package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * ARGB(투명도 포함) 색상 하나. 저장은 0xAARRGGBB 정수를 그대로 JSON 숫자로.
 * 크로스헤어 색, HUD 텍스트 색, 테두리 색 등 "색 커스텀"이 필요한 모든 곳에서 재사용.
 */
public class ColorSetting extends Setting<Integer> {

	public ColorSetting(String id, String displayName, String description, int defaultArgb) {
		super(id, displayName, description, defaultArgb);
	}

	/**
	 * 0xAARRGGBB. 49-29차: **기본값 그대로**(처음의 Luna 연두)인 색은 지금 테마 색으로 바꿔 돌려준다 -
	 * 클라이언트에서 장착한 색이 HUD에도 그대로 반영되고, 사용자가 직접 고른 색은 건드리지 않는다.
	 */
	public int getArgb() {
		if (value != null && value.equals(getDefaultValue())) {
			return kr.lunaslight.mod.util.LunaTheme.themed(value);
		}
		return value;
	}

	/** 테마를 무시한 실제 저장값(색 편집기에서 씀). */
	public int getRawArgb() {
		return value;
	}

	public int getRgb() {
		return value & 0x00FFFFFF;
	}

	public float getAlpha01() {
		return ((value >>> 24) & 0xFF) / 255f;
	}

	public static int of(int a, int r, int g, int b) {
		return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			setValue(element.getAsInt());
		}
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public ColorSetting style() {
		super.style();
		return this;
	}
}
