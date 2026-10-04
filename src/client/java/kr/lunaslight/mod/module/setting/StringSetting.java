package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;

public class StringSetting extends Setting<String> {

	/**
	 * 49-76차(6-8, 사용자: "엔티티 가리기 이름 쓰고 추가하는 식으로 해줘 여러개 가능하게"):
	 * <b>목록형</b>. 값은 여전히 쉼표로 이어 붙인 한 줄이라(저장 형식·{@code ChatState.parseWords}와 호환)
	 * 모듈 쪽 코드는 그대로인데, 설정 화면이 <b>입력 칸 + [추가] + 항목마다 [×]</b>로 그린다.
	 * 쉼표를 직접 치지 않아도 되고, 뭐가 들어 있는지 한눈에 보인다.
	 */
	private boolean list;
	/** 목록형에서 <b>아직 추가하지 않은</b> 입력 중인 글자. 값과 분리해 둬야 타이핑 도중에 저장되지 않는다. */
	private String draft = "";

	public StringSetting(String id, String displayName, String description, String defaultValue) {
		super(id, displayName, description, defaultValue);
	}

	public String get() {
		return value;
	}

	public StringSetting list() {
		this.list = true;
		return this;
	}

	public boolean isList() {
		return list;
	}

	public String draft() {
		return draft;
	}

	public void setDraft(String d) {
		draft = d == null ? "" : d;
	}

	/** 쉼표로 나눈 항목들(앞뒤 공백 제거, 빈 것 제외). */
	public List<String> items() {
		List<String> out = new ArrayList<>();
		if (value == null) {
			return out;
		}
		for (String part : value.split(",")) {
			String t = part.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	/** 입력 중인 글자를 항목으로 넣는다. 이미 있으면(대소문자 무시) 안 넣는다. 넣었으면 true. */
	public boolean commitDraft() {
		String t = draft.trim().replace(",", " ").trim();
		draft = "";
		if (t.isEmpty()) {
			return false;
		}
		List<String> cur = items();
		for (String c : cur) {
			if (c.equalsIgnoreCase(t)) {
				return false;
			}
		}
		cur.add(t);
		setValue(String.join(", ", cur));
		return true;
	}

	public void removeItem(String item) {
		List<String> cur = items();
		cur.removeIf(c -> c.equalsIgnoreCase(item));
		setValue(String.join(", ", cur));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value == null ? "" : value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			setValue(element.getAsString());
		}
	}
	@Override
	public StringSetting hidden() {
		super.hidden();
		return this;
	}

	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public StringSetting style() {
		super.style();
		return this;
	}
}
