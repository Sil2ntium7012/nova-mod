package kr.lunaslight.mod.util;

import net.minecraft.entity.Entity;

/**
 * 49-163차(1.14.4 추가): 엔티티 좌표. 1.15 ~ 1.21.11은 {@code Entity#getX/getY/getZ()}, 1.14.4는 public 필드 x/y/z뿐이다
 * (getPos()는 1.21.9+에서 getEntityPos()로 이름이 바뀌어 공유 소스에 못 쓴다). {@link WindowAccess}와 같은 폴더에서
 * 버전에 맞는 한 벌만 얹는다(compat/window-modern: 1.15+, compat/window-legacy14: 1.14.4). 직접 호출이라 비용 0.
 */
public final class EntityPos {
	private EntityPos() {
	}

	public static double x(Entity e) {
		return e.getX();
	}

	public static double y(Entity e) {
		return e.getY();
	}

	public static double z(Entity e) {
		return e.getZ();
	}
}
