package kr.lunaslight.mod.util;

/** 49-24차: HUD 편집기가 열려 있는 동안 true - ModuleManager의 HUD 패스가 그리기를 편집기에 맡긴다. */
public final class HudEditorState {
	private HudEditorState() {
	}

	public static volatile boolean active;
}
