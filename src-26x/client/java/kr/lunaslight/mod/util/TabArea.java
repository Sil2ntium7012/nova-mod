package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;

/**
 * 49-303차(사용자: "탭 눌렀을 때 그 탭 근처에 있는 HUD들 가려줘"): 지금 탭(플레이어 목록)이 떠 있으면 그 자리(화면 GUI 좌표)를 대략 구한다.
 * 바닐라처럼 20명마다 한 열, 열 폭 = 가장 긴 이름 + 머리/핑 자리, 위아래에 서버가 보낸 머리글/바닥글 줄. 0.25초마다만 다시 잰다.
 * 뜨지 않았으면 null. ModuleManager가 이 자리와 겹치는 HUD를 그 동안 건너뛴다.
 */
public final class TabArea {
	private TabArea() {
	}

	private static long calcAt;
	private static int[] cached;
	private static int cachedW, cachedH;

	public static int[] rect(Minecraft mc, int sw, int sh) {
		try {
			if (mc == null || mc.player == null || !tabHeld(mc)) {
				return null;
			}
			long now = System.currentTimeMillis();
			if (cached != null && now - calcAt < 250 && cachedW == sw && cachedH == sh) {
				return cached;
			}
			calcAt = now;
			cachedW = sw;
			cachedH = sh;
			java.util.Collection<?> players = players(mc);
			int n = players == null ? 1 : players.size();
			if (n <= 1 && LunaCompat.isSinglePlayer(mc)) {
				cached = null;   // 혼자인 싱글은 바닐라도 탭을 안 띄운다
				return null;
			}
			int nameW = 0;
			if (players != null) {
				int k = 0;
				for (Object e : players) {
					if (++k > 200) {
						break;
					}
					Object prof = LunaCompat.callNoArg(e, "getProfile");
					Object nm = prof == null ? null : LunaCompat.callNoArg(prof, "getName");
					if (nm == null && prof != null) {
						nm = LunaCompat.callNoArg(prof, "name");
					}
					if (nm instanceof String s) {
						nameW = Math.max(nameW, LunaCompat.getTextWidth(mc.font, s));
					}
				}
			}
			if (nameW == 0) {
				nameW = 90;
			}
			int rows = n;
			int cols = 1;
			while (rows > 20) {
				cols++;
				rows = (n + cols - 1) / cols;
			}
			int colW = nameW + 9 + 13 + 14;   // 머리 + 핑 + 여유(칭호/점수)
			int w = Math.min(sw - 50, cols * colW + (cols - 1) * 5);
			int lines = 0;
			Object tab = tabOverlay(mc);
			for (String f : new String[]{"header", "footer"}) {
				Object c = tab == null ? null : fieldOf(tab, f);
				Object str = c == null ? null : LunaCompat.callNoArg(c, "getString");
				if (str instanceof String s && !s.isEmpty()) {
					for (String line : s.split("\\n")) {
						lines++;
						w = Math.max(w, Math.min(sw - 50, LunaCompat.getTextWidth(mc.font, line)));
					}
				}
			}
			int h = 10 + rows * 9 + lines * 9 + (lines > 0 ? 2 : 0) + 4;
			int x0 = sw / 2 - w / 2 - 6, x1 = sw / 2 + w / 2 + 6;
			cached = new int[]{x0, 0, x1, Math.min(sh, h + 4)};
			return cached;
		} catch (Throwable t) {
			return null;
		}
	}

	/** 사각형 (x, y, w, h)가 탭 자리와 겹치나. */
	public static boolean overlaps(int[] r, int x, int y, int w, int h) {
		return r != null && x < r[2] && x + w > r[0] && y < r[3] && y + h > r[1];
	}

	private static boolean tabHeld(Minecraft mc) {
		Object options = mc.options;
		for (String n : new String[]{"playerListKey", "keyPlayerList"}) {
			java.lang.reflect.Field f = LunaCompat.findField(options.getClass(), n);
			if (f == null) {
				continue;
			}
			try {
				f.setAccessible(true);
				Object binding = f.get(options);
				for (String m : new String[]{"isPressed", "isDown"}) {
					Object pressed = binding == null ? null : LunaCompat.callNoArg(binding, m);
					if (pressed instanceof Boolean b) {
						return b;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	private static java.util.Collection<?> players(Minecraft mc) {
		Object conn = null;
		for (String n : new String[]{"getNetworkHandler", "getConnection"}) {
			conn = LunaCompat.callNoArg(mc, n);
			if (conn != null) {
				break;
			}
		}
		if (conn == null) {
			return null;
		}
		for (String n : new String[]{"getListedPlayerListEntries", "getListedOnlinePlayers", "getPlayerList", "getOnlinePlayers"}) {
			Object v = LunaCompat.callNoArg(conn, n);
			if (v instanceof java.util.Collection<?> c) {
				return c;
			}
		}
		return null;
	}

	private static Object tabOverlay(Minecraft mc) {
		for (String fn : new String[]{"inGameHud", "gui"}) {
			Object hud = fieldOf(mc, fn);
			if (hud == null) {
				continue;
			}
			for (String m : new String[]{"getPlayerListHud", "getTabList"}) {
				Object t = LunaCompat.callNoArg(hud, m);
				if (t != null) {
					return t;
				}
			}
		}
		return null;
	}

	private static Object fieldOf(Object o, String name) {
		try {
			java.lang.reflect.Field f = LunaCompat.findField(o.getClass(), name);
			if (f == null) {
				return null;
			}
			f.setAccessible(true);
			return f.get(o);
		} catch (Throwable t) {
			return null;
		}
	}
}
