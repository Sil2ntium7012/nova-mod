package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class IntSetting extends Setting<Integer> {
	private final int min;
	private final int max;
	private final int step;

	public IntSetting(String id, String displayName, String description, int defaultValue, int min, int max, int step) {
		super(id, displayName, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = Math.max(1, step);
	}

	public int get() {
		return value;
	}

	public int getMin() {
		return min;
	}

	public int getMax() {
		return max;
	}

	public int getStep() {
		return step;
	}

	@Override
	public void setValue(Integer value) {
		super.setValue(Math.max(min, Math.min(max, value)));
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
	public IntSetting style() {
		super.style();
		return this;
	}

	/** 49-76차(6-17): 단위(구체 타입을 유지하는 오버라이드). */
	@Override
	public IntSetting unit(String unit) {
		super.unit(unit);
		return this;
	}
}
