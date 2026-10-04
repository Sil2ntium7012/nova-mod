package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

/**
 * 49-163차(1.14.4 추가): 게임 창 얻기. 1.15부터는 {@code MinecraftClient#getWindow()}, 1.14.4는 public 필드 {@code window}뿐이라
 * (yarn 1.14.4+build.18 javap 실측) 공유 소스가 이 한 곳만 부르고, loom-common.gradle이 버전에 맞는 한 벌만 얹는다
 * (compat/window-modern: 1.15+, compat/window-legacy14: 1.14.4). 리플렉션이 아니라 직접 호출이라 프레임마다 불러도 비용 0.
 */
public final class WindowAccess {
	private WindowAccess() {
	}

	public static Window of(MinecraftClient client) {
		return client.getWindow();
	}
}
