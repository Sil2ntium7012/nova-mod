package kr.lunaslight.mod.module.impl.chat;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.HangulInput;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-313차(사용자: "한글챗 내장 만들어봐야 할 거 같아" → 모든 입력칸, 한/영 키, 윈도우 입력기는 모드가 알아서 끔): <b>한글 입력</b>.
 * 모드가 두벌식으로 직접 조합한다(조합 중인 글자가 바로 보이고, 한글 상태에서도 키 설정이 먹는다). 자세한 건 util/HangulInput.
 * [일반] 페이지. 끄면 윈도우 입력기를 돌려 붙인다.
 */
public class HangulInputModule extends Module {

	private static HangulInputModule instance;

	private final KeybindSetting toggle = register(new KeybindSetting(
			"toggle_key", "한/영 전환 키", "키보드의 한/영 키는 늘 됩니다. 이 키로도 바꿉니다(한/영 키가 오른쪽 Alt로 잡히는 키보드 포함).",
			InputConstants.KEY_RALT));

	private final BooleanSetting indicator = register(new BooleanSetting(
			"indicator", "한/A 표시", "입력칸 옆에 지금 한글인지 영문인지 보여줍니다.", true));

	public HangulInputModule() {
		super("hangul_input", "한글 입력", ModuleCategory.FEATURE, "모드가 직접 한글 조합");
		settingsPage(SettingsPage.GENERAL);
		defaultEnabled(true);
		instance = this;
		HangulInput.setEmitter(new HangulInput.Emitter() {
			@Override
			public void key(long window, int key, int scancode, int action, int mods) {
				Object kh = net.minecraft.client.Minecraft.getInstance().keyboardHandler;
				((kr.lunaslight.mod.mixin.KeyboardInvokerMixin) kh).lunaslight$keyPress(window, action,
						new net.minecraft.client.input.KeyEvent(key, scancode, mods));
			}

			@Override
			public void chr(long window, int codePoint) {
				Object kh = net.minecraft.client.Minecraft.getInstance().keyboardHandler;
				((kr.lunaslight.mod.mixin.KeyboardInvokerMixin) kh).lunaslight$charTyped(window,
						new net.minecraft.client.input.CharacterEvent(codePoint));
			}
		});
		LunaCompat.registerScreenAfterRender((screen, ctx, mx, my) -> {
			try {
				HangulInput.drawIndicator(screen, ctx);
			} catch (Throwable t) {
				LunaCompat.warnOnce("hangul:indicator", t);
			}
		});
	}

	public static boolean on() {
		HangulInputModule m = instance;
		return m != null && m.isEnabled();
	}

	/** 추가 전환 키(없으면 -1). */
	public static int toggleKey() {
		HangulInputModule m = instance;
		return m == null || !m.toggle.isBound() || m.toggle.isMouse() ? -1 : m.toggle.getBaseKey();
	}

	public static boolean showIndicator() {
		HangulInputModule m = instance;
		return m == null || m.indicator.get();
	}

	@Override
	public void onTick() {
		HangulInput.tick();
	}

	@Override
	protected void onDisable() {
		HangulInput.disabled();
	}
}
