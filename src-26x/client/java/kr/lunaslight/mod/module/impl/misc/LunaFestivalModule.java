package kr.lunaslight.mod.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaFest;

/**
 * 49-374차(요청서 luna-key-c.md): <b>LUNA FESTIVAL</b> 서버 키. C = "LUNA 칸 C 사용"(핫바 C 칸 물약), X = "LUNA 내 능력 보기"(내 능력 창).
 * 눌렀다 뗄 때 보낸다 - 누르는 동안 숫자 키를 눌렀으면(바닐라 C/X + 숫자 = 핫바 저장/불러오기) 안 보낸다. 화면이 열려 있으면 안 보낸다.
 * 이 서버(LunaChatTabs.isActive)에서만 보낸다.
 */
public class LunaFestivalModule extends Module {

	private final KeybindSetting keyC = register(new KeybindSetting(
			"key_c", "LUNA 칸 C 사용", "핫바 C 칸(물약)을 씁니다. LUNA FESTIVAL 서버에서만.", InputConstants.KEY_C));
	private final KeybindSetting keyX = register(new KeybindSetting(
			"key_x", "LUNA 내 능력 보기", "내 능력 창을 엽니다. LUNA FESTIVAL 서버에서만.", InputConstants.KEY_X));

	private final boolean[] down = new boolean[2];
	private final boolean[] spoiled = new boolean[2];

	public LunaFestivalModule() {
		super("luna_festival", "LUNA FESTIVAL", ModuleCategory.SERVER, "C 키 물약, X 키 내 능력 보기");
		serverGroup("LUNA FESTIVAL");
		defaultEnabled(true);
	}

	@Override
	public void onTick() {
		poll(0, keyC, "C");
		poll(1, keyX, "X");
	}

	private void poll(int i, KeybindSetting key, String send) {
		boolean open = LunaCompat.screenOf(client) != null;
		boolean now = client.player != null && !open && key.isDown(client);
		if (now && !down[i]) {
			spoiled[i] = false;
		}
		if (now && digitDown()) {
			spoiled[i] = true;   // C/X + 숫자 = 바닐라 핫바 저장/불러오기 - 그건 서버로 안 보낸다
		}
		if (!now && down[i] && !spoiled[i] && !open) {
			LunaFest.sendKey(send);
		}
		if (open) {
			spoiled[i] = true;
		}
		down[i] = now;
	}

	private boolean digitDown() {
		for (int k = InputConstants.KEY_1; k <= InputConstants.KEY_9; k++) {
			if (LunaCompat.isKeyPressedAny(client, k)) {
				return true;
			}
		}
		return false;
	}
}
