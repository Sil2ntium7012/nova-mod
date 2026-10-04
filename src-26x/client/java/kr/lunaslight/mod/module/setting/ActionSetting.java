package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * 49-89차(8-8 "하나씩 등록"): 값이 없는 <b>버튼 설정</b> - 설정 행에 알약 버튼 하나로 뜨고 누르면 동작한다.
 * 저장할 값이 없으므로 toJson/fromJson은 아무것도 안 한다. 단축키 페이지의 [+ 추가]가 쓴다.
 */
public class ActionSetting extends Setting<Boolean> {
	private final Runnable action;
	private final String buttonLabel;

	public ActionSetting(String id, String displayName, String description, String buttonLabel, Runnable action) {
		super(id, displayName, description, Boolean.FALSE);
		this.buttonLabel = buttonLabel;
		this.action = action;
	}

	public String getButtonLabel() {
		return buttonLabel;
	}

	public void run() {
		if (action != null) {
			action.run();
		}
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
