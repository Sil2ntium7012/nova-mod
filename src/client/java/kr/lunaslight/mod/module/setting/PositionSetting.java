package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PositionSetting extends Setting<HudPosition> {

	public PositionSetting(String id, String displayName, String description, HudPosition defaultValue) {
		super(id, displayName, description, defaultValue);
	}

	public HudPosition get() {
		return value;
	}

	/**
	 * 47차: HUD 편집기가 값 객체를 제자리에서(moveTo) 고치므로, 기본값 객체를 그대로 넘겨주면
	 * 기본값 자체가 같이 움직여 버림 - 항상 복사본으로 되돌림.
	 */
	@Override
	public void resetToDefault() {
		setValue(getDefaultValue().copy());
	}

	@Override
	public JsonElement toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("anchor", value.anchor.name());
		obj.addProperty("x", value.offsetX);
		obj.addProperty("y", value.offsetY);
		obj.addProperty("scale", value.scale);
		return obj;
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return;
		}
		JsonObject obj = element.getAsJsonObject();
		try {
			HudPosition.Anchor anchor = obj.has("anchor")
				? HudPosition.Anchor.valueOf(obj.get("anchor").getAsString())
				: value.anchor;
			float x = obj.has("x") ? obj.get("x").getAsFloat() : value.offsetX;
			float y = obj.has("y") ? obj.get("y").getAsFloat() : value.offsetY;
			float scale = obj.has("scale") ? obj.get("scale").getAsFloat() : value.scale;
			setValue(new HudPosition(anchor, x, y, scale));
		} catch (IllegalArgumentException ignored) {
			// 앵커 이름이 바뀐 경우 기본값 유지
		}
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public PositionSetting style() {
		super.style();
		return this;
	}
}
