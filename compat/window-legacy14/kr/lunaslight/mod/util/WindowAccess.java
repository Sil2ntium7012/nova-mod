package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

/** 49-163차: 1.14.4 판 - getWindow()가 없고 public 필드 window뿐이다. 설명은 compat/window-modern 쪽 파일. */
public final class WindowAccess {
	private WindowAccess() {
	}

	public static Window of(MinecraftClient client) {
		return client.window;
	}
}
