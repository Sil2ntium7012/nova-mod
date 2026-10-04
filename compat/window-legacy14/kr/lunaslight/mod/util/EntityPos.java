package kr.lunaslight.mod.util;

import net.minecraft.entity.Entity;

/** 49-163차: 1.14.4 판 - getX()가 없고 public 필드 x/y/z뿐이다. 설명은 compat/window-modern 쪽 파일. */
public final class EntityPos {
	private EntityPos() {
	}

	public static double x(Entity e) {
		return e.x;
	}

	public static double y(Entity e) {
		return e.y;
	}

	public static double z(Entity e) {
		return e.z;
	}
}
