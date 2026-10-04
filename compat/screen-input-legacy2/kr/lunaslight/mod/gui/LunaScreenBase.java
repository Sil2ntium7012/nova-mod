package kr.lunaslight.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

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
 * 이 파일(legacy2): <b>1.15.2~1.19.4</b> - 입력 시그니처는 legacy3와 완전히 같지만, 이 시대엔
 * 화면 그리기가 <code>render(MatrixStack, int, int, float)</code>라서 공유 소스의
 * <code>render(DrawContext, …)</code>가 실제 오버라이드가 되지 못한다. 그래서 여기서 진짜
 * render를 오버라이드해 MatrixStack을 compat DrawContext(shim)로 감싸 넘겨 준다.
 *
 * 49-45차: 이게 없어서 1.15.2~1.19.4(17개 버전)는 gui/*Screen.java를 통째로 컴파일에서 빼고
 * 있었고, 그 버전들에선 Luna 설정·타이틀·일시정지 화면이 아예 열리지 않았다("이 버전에서는
 * 아직 지원되지 않는 화면입니다" 로그만). 이제 열린다.
 */
public abstract class LunaScreenBase extends Screen {
	protected LunaScreenBase(Text title) {
		super(title);
	}

	// ---- 그리기: 이 시대의 진짜 render를 받아 shim DrawContext로 넘김 ----

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		render(new DrawContext(matrices), mouseX, mouseY, delta);
	}

	/** 공유 소스(LunaClientScreen 등)가 오버라이드하는 버전 무관 그리기 훅. */
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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

	// ---- 1.20.1 이하 실제 시그니처 ----
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (lunaMouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (lunaMouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (lunaMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (lunaKeyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (lunaKeyReleased(keyCode, scanCode, modifiers)) {
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (lunaMouseScrolled(mouseX, mouseY, amount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (lunaCharTyped(chr)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}
}
