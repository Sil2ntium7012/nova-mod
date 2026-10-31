package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * GLFW 키코드 하나(명령어 단축키, 3인칭 유지 등에 사용). -1 = 미설정.
 * 49-22차: 마우스 버튼도 지정 가능 - LunaCompat.MOUSE_KEY_BASE + 버튼 번호로 인코딩(옆버튼 = 3/4).
 */
public class KeybindSetting extends Setting<Integer> {

	/** 49-451차: 만든 키 지정 전부 - 조합키 지정이 눌렸을 때 같은 키의 단독 지정을 쉬게 하려고. */
	private static final java.util.List<KeybindSetting> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();

	public KeybindSetting(String id, String displayName, String description, int defaultGlfwKey) {
		super(id, displayName, description, defaultGlfwKey);
		ALL.add(this);
	}

	/** 저장된 값 그대로(49-24차: 보조키 비트 포함 - 실제 GLFW 키는 getBaseKey()). */
	public int getKeyCode() {
		return value;
	}

	/** 보조키 비트를 뗀 실제 키 코드(겹침 검사·마크 키 비교용). */
	public int getBaseKey() {
		return LunaCompat.baseKey(value);
	}

	public boolean isBound() {
		return value >= 0;
	}

	/** 마우스 버튼이 지정돼 있는지. */
	public boolean isMouse() {
		return LunaCompat.isMouseKeyCode(LunaCompat.baseKey(value));
	}

	/** 지정된 키(또는 마우스 버튼, 조합키 포함)가 지금 눌려 있는지. 모듈은 getKeyCode() 폴링 대신 이걸 쓴다. */
	public boolean isDown(Object client) {
		if (!isBound() || !LunaCompat.isKeyPressedAny(client, value)) {
			return false;
		}
		// 49-451차: 단독 지정(예: R)은 보조키가 눌려 있고 그 조합(예: Ctrl + R)이 다른 칸에 지정돼 있으면 쉰다 - 둘이 같이 안 켜지게.
		if (LunaCompat.keyModifiers(value) == 0) {
			int held = LunaCompat.heldModifiersAny(client);
			if (held != 0) {
				int base = getBaseKey();
				for (KeybindSetting o : ALL) {
					int v = o.value;
					int m = LunaCompat.keyModifiers(v);
					if (o != this && v >= 0 && m != 0 && (m & held) == m && LunaCompat.baseKey(v) == base) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public String getKeyName() {
		if (!isBound()) {
			return "미설정";
		}
		int base = LunaCompat.baseKey(value);
		String baseName = LunaCompat.isMouseKeyCode(base)
				? LunaCompat.keyDisplayName(base)
				: LunaCompat.keySymName(base);
		int mods = LunaCompat.keyModifiers(value);
		if (mods == 0) {
			return baseName;
		}
		StringBuilder sb = new StringBuilder();
		if ((mods & LunaCompat.MOD_CTRL) != 0) {
			sb.append("Ctrl + ");
		}
		if ((mods & LunaCompat.MOD_SHIFT) != 0) {
			sb.append("Shift + ");
		}
		if ((mods & LunaCompat.MOD_ALT) != 0) {
			sb.append("Alt + ");
		}
		return sb + baseName;
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
	public KeybindSetting style() {
		super.style();
		return this;
	}
}
