package kr.lunaslight.mod.util;

import java.lang.reflect.Method;

/**
 * 49-178차: 1.21.6+ 정적 Bar.drawExperienceLevel(DrawContext, TextRenderer, int)을 이름으로 부른다
 * (HudElementHideMixin이 그 호출을 가로챈 뒤 "숨김이 아니면" 원래대로 그리기 위해). Bar는 옛 버전에 없는 클래스라
 * 직접 적으면 옛 버전 빌드가 깨진다. 한 번 찾아 두고 계속 쓴다. 실패하면 조용히 안 그린다.
 */
public final class ExpLevelDraw {
	private ExpLevelDraw() {
	}

	private static Method method;
	private static boolean resolved;

	public static void draw(Object ctx, Object textRenderer, int level) {
		if (!resolved) {
			resolved = true;
			try {
				Class<?> bar = LunaCompat.classForName("net.minecraft.client.gui.hud.bar.Bar");
				for (Method m : bar.getMethods()) {
					Class<?>[] p = m.getParameterTypes();
					if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && p.length == 3 && p[2] == int.class
							&& p[0].isInstance(ctx) && p[1].isInstance(textRenderer)
							&& LunaCompat.nameMatches(bar, "drawExperienceLevel", m.getName())) {
						m.setAccessible(true);
						method = m;
						break;
					}
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("expLevelDraw", t);
			}
		}
		if (method == null) {
			return;
		}
		try {
			method.invoke(null, ctx, textRenderer, level);
		} catch (Throwable t) {
			LunaCompat.warnOnce("expLevelDraw:call", t);
		}
	}
}
