package kr.lunaslight.mod.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 46차(2026-09-01): Screen의 마우스/키 입력 메서드 시그니처가 버전마다 달라서(≤1.20.1:
 * mouseScrolled(double,double,double) / 1.20.2~1.21.8: mouseScrolled(double,double,double,double) /
 * 1.21.9+: Click/KeyInput 객체 기반으로 전면 개편) 공유 소스의 LunaClientScreen이 한 시그니처로는
 * 전 버전에서 실제 오버라이드가 될 수 없었음(19차에 @Override를 떼고 컴파일만 통과시켜둔 상태라
 * 1.21.9+에서는 설정 화면이 보이기만 하고 클릭/키 입력이 전혀 안 먹었음 - 46차 인게임 검증에서
 * 확인). 그래서 "그 버전의 진짜 시그니처"를 오버라이드하는 이 얇은 중간 클래스를 버전대별로
 * 3벌(compat/screen-input-modern|legacy4|legacy3) 두고, loom-common.gradle이 minecraft_version에
 * 맞는 한 벌만 소스에 얹음. LunaClientScreen은 이 클래스의 luna* 훅만 구현하면 됨.
 * (세 파일은 실제 오버라이드 메서드 시그니처만 다르고 나머지는 동일 - 한쪽을 고치면 나머지도 확인)
 *
 * 이 파일: 1.21.9+ (Click/KeyInput 객체 기반 시그니처)
 */
public abstract class LunaScreenBase extends Screen {
	protected LunaScreenBase(Component title) {
		super(title);
	}

	// ---- LunaClientScreen이 구현하는 버전 무관 훅들 (처리했으면 true) ----
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return false;
	}

	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		return false;
	}

	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	/** 47차: 검색창/문자열 설정 입력용 문자 훅. */
	protected boolean lunaCharTyped(char chr) {
		return false;
	}

	/** 49-24차: 키 뗌 훅(조합키 지정 - Ctrl/Shift/Alt만 눌렀다 떼면 그 키 자체를 지정). */
	protected boolean lunaKeyReleased(int keyCode, int scanCode, int modifiers) {
		return false;
	}

	// ---- 49-215차: 26.3(SDL) 글자 입력 ----
	// SDL은 "글자 입력 중"을 켜야 charTyped가 온다. 우리 화면의 검색창/입력칸은 바닐라 입력칸이 아니라서 화면이
	// 열려 있는 동안 직접 켜 둔다(26.2까지는 아무 일도 안 함). 마우스 버튼은 GLFW 번호(0 왼쪽, 1 오른쪽)로 바꿔 넘긴다.
	@Override
	protected void init() {
		super.init();
		kr.lunaslight.mod.util.LunaInput.textInput(this, true);
	}

	@Override
	public void tick() {
		super.tick();
		kr.lunaslight.mod.util.LunaInput.textInput(this, true);
		lunaSyncIme();
	}

	@Override
	public void removed() {
		kr.lunaslight.mod.util.LunaInput.textInput(this, false);
		if (lunaImeOn) {
			lunaImeOn = false;
			kr.lunaslight.mod.util.LunaInput.imeText(false);
		}
		lunaPreedit = "";
		super.removed();
	}

	// ---- 49-221차: 한글 입력(사용자: "가끔 인게임 귓속말에서 한글이 안 돼요") ----
	// 26.1부터 바닐라 TextInputManager가 "글자 입력 중"이 아니면 매 틱 IME를 영문으로 되돌린다(바닐라 입력칸만
	// startTextInput을 부른다). 우리 입력칸은 바닐라 입력칸이 아니라서 한/영을 눌러도 다음 틱에 영문으로 돌아가
	// 한글이 안 쳐졌다(그 틱 사이에 친 것만 들어가서 "가끔"). 이제 글을 받는 칸에 초점이 있는 동안 startTextInput을
	// 켜 두면 바닐라 채팅처럼 마지막으로 쓰던 한/영 상태가 돌아오고 바뀌어도 유지된다. 숫자/색 코드 칸은 켜지 않는다(영문 유지).
	private boolean lunaImeOn;

	/** 조합 중인 글자(IME 미리보기). 입력칸을 그릴 때 글 뒤에 붙여 보여 준다. */
	protected String lunaPreedit = "";

	/** 지금 한글 같은 자유 글 입력칸에 초점이 있는가. 화면마다 오버라이드. */
	protected boolean lunaWantsText() {
		return false;
	}

	/** 초점이 바뀌었으면 IME 글자 입력을 켜거나 끈다(틱, 클릭, 키 뒤에 부른다). */
	protected final void lunaSyncIme() {
		boolean want;
		try {
			want = lunaWantsText();
		} catch (Throwable t) {
			want = false;
		}
		if (want != lunaImeOn) {
			lunaImeOn = want;
			kr.lunaslight.mod.util.LunaInput.imeText(want);
			if (!want) {
				lunaPreedit = "";
			}
		}
	}

	@Override
	public boolean preeditUpdated(net.minecraft.client.input.PreeditEvent event) {
		if (lunaImeOn) {
			String t = event == null ? null : event.fullText();
			lunaPreedit = t == null ? "" : t;
			return true;
		}
		return super.preeditUpdated(event);
	}

	// ---- 1.21.9+ 실제 시그니처 ----
	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
		if (lunaMouseClicked(click.x(), click.y(), kr.lunaslight.mod.util.LunaInput.toGlfwButton(click.button()))) {
			lunaSyncIme();
			return true;
		}
		boolean r = super.mouseClicked(click, doubled);
		lunaSyncIme();
		return r;
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
		if (lunaMouseReleased(click.x(), click.y(), kr.lunaslight.mod.util.LunaInput.toGlfwButton(click.button()))) {
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
		if (lunaMouseDragged(click.x(), click.y(), kr.lunaslight.mod.util.LunaInput.toGlfwButton(click.button()), deltaX, deltaY)) {
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (lunaMouseScrolled(mouseX, mouseY, verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
		if (lunaKeyPressed(input.key(), kr.lunaslight.mod.util.LunaInput.secondCode(input), 0)) {
			lunaSyncIme();
			return true;
		}
		boolean r = super.keyPressed(input);
		lunaSyncIme();
		return r;
	}

	@Override
	public boolean keyReleased(net.minecraft.client.input.KeyEvent input) {
		if (lunaKeyReleased(input.key(), kr.lunaslight.mod.util.LunaInput.secondCode(input), 0)) {
			return true;
		}
		return super.keyReleased(input);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
		int cp = input.codepoint();
		if (cp >= 0 && cp <= 0xFFFF && lunaCharTyped((char) cp)) {
			return true;
		}
		return super.charTyped(input);
	}
}
