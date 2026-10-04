package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting<Boolean> {

	public BooleanSetting(String id, String displayName, String description, boolean defaultValue) {
		super(id, displayName, description, defaultValue);
	}

	public boolean get() {
		return value;
	}

	// 49-23차: "색상 변경하는 거 껐다 켰다 기능 옆에다 둬서 1줄로 통합" - 이 스위치와 짝인 색 설정.
	// 짝이 있으면 설정 화면이 색 설정을 따로 한 줄 만들지 않고 스위치 왼쪽에 색 견본으로 붙여 그린다.
	private ColorSetting linkedColor;

	/** 이 스위치 줄에 같이 보여줄 색 설정을 짝지음(색 설정도 모듈에 register돼 있어야 저장됨). */
	public BooleanSetting withColor(ColorSetting color) {
		this.linkedColor = color;
		return this;
	}

	/** 짝지어진 색 설정(없으면 null). */
	public ColorSetting getLinkedColor() {
		return linkedColor;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			setValue(element.getAsBoolean());
		}
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public BooleanSetting style() {
		super.style();
		return this;
	}
}
