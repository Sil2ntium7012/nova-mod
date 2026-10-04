package kr.lunaslight.mod.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * GLFW 키코드 하나(명령어 단축키, 3인칭 유지 등에 사용). -1 = 미설정.
 * 49-22차: 마우스 버튼도 지정 가능 - LunaCompat.MOUSE_KEY_BASE + 버튼 번호로 인코딩(옆버튼 = 3/4).
 */
public class KeybindSetting extends Setting<Integer> {

	public KeybindSetting(String id, String displayName, String description, int defaultGlfwKey) {
		super(id, displayName, description, defaultGlfwKey);
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
		return isBound() && LunaCompat.isKeyPressedAny(client, value);
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
			int v = element.getAsInt();
			String from = loadScheme;
			if (from != null) {
				v = kr.lunaslight.mod.util.LunaInput.migrateKey(v, "sdl".equals(from));
			}
			// 49-278차: 26.3(SDL)에서 GLFW 숫자 기본값이 그대로 저장된 것(설계도 O = 79 → 오른쪽 화살표 등)을 기본값으로 되돌린다.
			// 고친 뒤 처음 읽는 파일 한 번만(LunaClientConfig가 _input_fix 표시가 없을 때 켠다).
			if (legacyFix && legacyRaw != Integer.MIN_VALUE && "sdl".equals(from) && kr.lunaslight.mod.util.LunaInput.SDL && v == legacyRaw) {
				v = getDefaultValue();
			}
			setValue(v);
		}
	}

	/** 49-215차: 설정 파일을 읽는 동안만 - 그 파일의 키 방식("glfw"/"sdl"). null이면 옮기지 않음. */
	private static volatile String loadScheme;

	public static void migrateFrom(String scheme) {
		loadScheme = scheme;
	}

	/** 49-278차: 예전 판이 26.3에서 잘못 저장했을 수 있는 값(그 설정의 GLFW 숫자 기본값). */
	private int legacyRaw = Integer.MIN_VALUE;

	public KeybindSetting legacyRaw(int raw) {
		this.legacyRaw = raw;
		return this;
	}

	private static volatile boolean legacyFix;

	public static void legacyFix(boolean on) {
		legacyFix = on;
	}
	/** 49-22차: 스타일 그룹 표시(구체 타입을 유지하는 오버라이드). */
	@Override
	public KeybindSetting style() {
		super.style();
		return this;
	}
}
