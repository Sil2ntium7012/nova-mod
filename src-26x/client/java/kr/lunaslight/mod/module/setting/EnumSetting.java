package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final Class<E> enumClass;
	/** 49-47차: 가로 세그먼트 대신 세로 목록으로 나열(글꼴처럼 항목 이름이 긴 경우). */
	private boolean vertical;

	public EnumSetting(String id, String displayName, String description, E defaultValue, Class<E> enumClass) {
		super(id, displayName, description, defaultValue);
		this.enumClass = enumClass;
	}

	public E get() {
		return value;
	}

	public E[] getOptions() {
		return enumClass.getEnumConstants();
	}

	/** 49-47차(사용자: "글꼴 순서 배열을 가로 말고 세로로"): 설정 화면에서 세로 목록으로 그린다. */
	public EnumSetting<E> vertical() {
		this.vertical = true;
		return this;
	}

	public boolean isVertical() {
		return vertical;
	}

	/** 세그먼트 버튼에서 특정 옵션을 바로 선택(49-18차). */
	public void setIndex(int index) {
		E[] options = getOptions();
		if (index >= 0 && index < options.length) {
			setValue(options[index]);
		}
	}

	/** GUI 화살표(◀ ▶)로 다음/이전 옵션 순환. */
	public void cycle(int direction) {
		E[] options = getOptions();
		int index = value.ordinal();
		int next = Math.floorMod(index + direction, options.length);
		setValue(options[next]);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			try {
				setValue(Enum.valueOf(enumClass, element.getAsString()));
			} catch (IllegalArgumentException ignored) {
				// 저장된 값이 코드 개편으로 사라진 경우 기본값 유지
			}
		}
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public EnumSetting<E> style() {
		super.style();
		return this;
	}
}
