package kr.lunaslight.mod.gui;

import net.minecraft.client.gui.screen.Screen;
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
 * 이 파일: 1.20.2 ~ 1.21.8 (double/int 시그니처 + 4인자 mouseScrolled)
 */
public abstract class LunaScreenBase extends Screen {
	protected LunaScreenBase(Text title) {
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

	// ---- 1.20.2~1.21.8 실제 시그니처 ----
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
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (lunaMouseScrolled(mouseX, mouseY, verticalAmount)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (lunaCharTyped(chr)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}
}
