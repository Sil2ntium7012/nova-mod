package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * 49-271차: 값을 <b>보여 주기만</b> 하는 설정 줄(이름 + 오른쪽 글자). 바꿀 수 없고 저장도 안 한다.
 * [코스메틱] 페이지가 런처에서 착용한 망토, 화면 스킨, 날개 등을 보여 줄 때 쓴다(사용자: "교체는 클라이언트에서 할 거니까 따로 교체는 못 하게").
 */
public class InfoSetting extends Setting<String> {
	private final java.util.function.Supplier<String> text;

	public InfoSetting(String id, String displayName, java.util.function.Supplier<String> text) {
		super(id, displayName, "", "");
		this.text = text;
	}

	/** 지금 보여 줄 글자(없으면 "없음"). */
	public String text() {
		try {
			String s = text == null ? null : text.get();
			return s == null || s.isEmpty() ? "없음" : s;
		} catch (Throwable t) {
			return "없음";
		}
	}

	/** "없음"이면 흐리게 그린다. */
	public boolean empty() {
		return "없음".equals(text());
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Override
	public void resetToDefault() {
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
