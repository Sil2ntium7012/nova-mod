package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * 49-103차(사용자: "단축키 설정하는 부분 이상해 … 1번 --- 명령어/키 … 추가하기 … 추가된 거는 밑에 박스에 두고 삭제도"):
 * 키바인드 항목 목록을 <b>화면(LunaClientScreen)이 직접 그리는</b> 컨트롤러 설정. 항목마다 [명령어/글 입력칸 + 키 버튼 + ×]
 * 한 줄로 보이고, 머리줄의 [추가하기]로 하나씩 늘린다.
 *
 * <p>값 자체는 저장하지 않는다({@code toJson=null}). 각 항목의 키·글은 예전 그대로 pooled {@link KeybindSetting}/
 * {@link StringSetting}(keyN/textN/count)에 담겨 저장되므로 <b>저장 형식이 안 바뀌고 기존 설정이 그대로 이어진다</b>.
 * 이 설정은 그 pooled 값들을 {@link Backing}으로 들여다보고 추가/삭제를 대신 시킨다.
 */
public class KeybindListSetting extends Setting<Boolean> {

	/** 실제 데이터(pooled 설정)를 쥔 모듈이 구현한다. */
	public interface Backing {
		int size();
		int capacity();
		KeybindSetting keyAt(int i);
		StringSetting textAt(int i);
		void add();
		void delete(int i);
	}

	private final Backing backing;
	private final String buttonLabel;

	public KeybindListSetting(String id, String displayName, String description, String buttonLabel, Backing backing) {
		super(id, displayName, description, Boolean.FALSE);
		this.buttonLabel = buttonLabel;
		this.backing = backing;
	}

	public String getButtonLabel() {
		return buttonLabel;
	}

	public int size() {
		return backing.size();
	}

	public int capacity() {
		return backing.capacity();
	}

	public KeybindSetting keyAt(int i) {
		return backing.keyAt(i);
	}

	public StringSetting textAt(int i) {
		return backing.textAt(i);
	}

	public void add() {
		backing.add();
	}

	public void delete(int i) {
		backing.delete(i);
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement element) {
	}
}
