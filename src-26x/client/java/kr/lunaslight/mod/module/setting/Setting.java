package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;

/**
 * 모듈 하나가 갖는 커스텀 옵션 하나(위치/색/on-off/숫자 등)의 공통 부모.
 * LunaClientScreen(설정 GUI)이 타입에 따라 다른 위젯을 그려주고,
 * LunaClientConfig(저장/불러오기)가 toJson/fromJson으로 값을 영속화합니다.
 */
public abstract class Setting<T> {
	private final String id;
	private final String displayName;
	private final String description;
	protected T value;
	private final T defaultValue;
	private Runnable onChange;

	protected Setting(String id, String displayName, String description, T defaultValue) {
		this.id = id;
		this.displayName = displayName;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}

	public T getValue() {
		return value;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public void setValue(T value) {
		this.value = value;
		if (onChange != null) {
			onChange.run();
		}
	}

	public void resetToDefault() {
		setValue(defaultValue);
	}

	/** 49-87차(8-2): 지금 값이 기본값인가 - 설정 화면이 초기화 버튼을 보일지 정할 때 쓴다. */
	public boolean isDefault() {
		return java.util.Objects.equals(value, defaultValue);
	}

	/** 값이 바뀔 때(GUI 조작이든 config 로드든) 호출할 콜백. 모듈에서 캐시 갱신 등에 사용. */
	public Setting<T> onChange(Runnable callback) {
		this.onChange = callback;
		return this;
	}

	// 49-22차: 설정 화면의 "스타일" 그룹에 넣을 설정임을 표시(이름 규칙(_color/hud_bg)에 안 걸리는 모양 설정용).
	private boolean style;
	/**
	 * 49-76차(6-6, 사용자: "직접 시간 쓰기 고르면 위에 낮 선택 불가능하게 표시"): 다른 설정에 따라
	 * <b>지금은 의미가 없는</b> 설정을 흐리게 그리고 클릭도 막는다. 값은 그대로 남는다.
	 */
	private java.util.function.BooleanSupplier disabledWhen;
	/** 49-76차(6-17, 사용자: "시간이나 거리에 단위좀 표시좀"): 숫자 상자 뒤에 붙는 단위(초·블록·틱·px·%). */
	private String unit = "";
	/** 49-76차(6-12-3): 설정 화면에 안 보이는 설정(저장은 된다) - 전용 화면이 다루는 값(가격표 묶음 등). */
	private boolean hidden;

	public Setting<T> hidden() {
		this.hidden = true;
		return this;
	}

	/** 49-89차: 숨김을 나중에 바꿀 수 있게(단축키 칸을 하나씩 열어 보일 때). */
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public boolean isHidden() {
		return hidden;
	}

	public Setting<T> unit(String unit) {
		this.unit = unit == null ? "" : unit;
		return this;
	}

	public String getUnit() {
		return unit;
	}

	public Setting<T> disabledWhen(java.util.function.BooleanSupplier when) {
		this.disabledWhen = when;
		return this;
	}

	public boolean isDisabled() {
		try {
			return disabledWhen != null && disabledWhen.getAsBoolean();
		} catch (Throwable ignored) {
			return false;
		}
	}

	public Setting<T> style() {
		this.style = true;
		return this;
	}

	public boolean isStyle() {
		return style;
	}

	// 49-124차(사용자: "채팅이나 이런 거 지금 한 박스 안에 너무 많아 좀 나눠야 해"): 설정을 이름 붙은
	// 하위 묶음(박스)으로 나눈다. 지정하지 않으면 기본 "기능" 묶음에 들어간다. 스타일 설정은 이와 무관하게
	// 늘 "스타일" 묶음으로 간다.
	private String groupName;

	public Setting<T> group(String name) {
		this.groupName = name;
		return this;
	}

	public String getGroupName() {
		return groupName;
	}

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement element);
}
