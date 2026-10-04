package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class FloatSetting extends Setting<Float> {
	private final float min;
	private final float max;
	private final float step;

	public FloatSetting(String id, String displayName, String description, float defaultValue, float min, float max, float step) {
		super(id, displayName, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public float get() {
		return value;
	}

	public float getMin() {
		return min;
	}

	public float getMax() {
		return max;
	}

	public float getStep() {
		return step;
	}

	@Override
	public void setValue(Float value) {
		super.setValue(Math.max(min, Math.min(max, value)));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			setValue(element.getAsFloat());
		}
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public FloatSetting style() {
		super.style();
		return this;
	}

	/** 49-76차(6-17): 단위(구체 타입을 유지하는 오버라이드). */
	@Override
	public FloatSetting unit(String unit) {
		super.unit(unit);
		return this;
	}
}
