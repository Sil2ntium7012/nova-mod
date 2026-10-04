package kr.lunaslight.mod.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 49-195차(사용자: "pip 했을 때 마우스 포인터가 윈도우 기본으로 뜸 - 커스텀 마우스 포인터 쓰는데 사라짐"): 지금 윈도우에 설정된
 * <b>화살표 포인터 그림</b>(사용자 포인터 테마 그대로)을 읽는다. 영상 보기가 창을 찍는 동안 게임 커서를 직접 그리는데
 * (VideoPipModule), 예전엔 윈도우 기본 화살표 모양을 코드로 그려서 사용자가 바꿔 둔 포인터가 사라져 보였다.
 *
 * <p>마크에 딸려 오는 JNA(oshi가 쓴다)를 <b>리플렉션으로</b> 부른다 - 컴파일 때 JNA가 필요 없고, 없는 버전이나 윈도우가 아닌
 * 곳에선 null을 돌려준다(그때는 예전처럼 기본 화살표를 그린다). 부르는 윈도우 함수:
 * LoadCursorW(NULL, IDC_ARROW) → GetIconInfo → GetDIBits(32비트, 위에서 아래로) → DeleteObject.
 * 64비트 구조체 자리: ICONINFO {BOOL fIcon@0, DWORD xHotspot@4, DWORD yHotspot@8, HBITMAP hbmMask@16, HBITMAP hbmColor@24},
 * BITMAP {LONG bmType@0, LONG bmWidth@4, LONG bmHeight@8, ...}.
 */
public final class SystemCursor {
	private SystemCursor() {
	}

	/** RGBA 바이트(위에서 아래로), 폭, 높이, 핫스폿. */
	public static final class Image {
		public final byte[] rgba;
		public final int width;
		public final int height;
		public final int hotX;
		public final int hotY;

		Image(byte[] rgba, int width, int height, int hotX, int hotY) {
			this.rgba = rgba;
			this.width = width;
			this.height = height;
			this.hotX = hotX;
			this.hotY = hotY;
		}
	}

	private static final int IDC_ARROW = 32512;

	private static Class<?> fnClass, ptrClass, memClass;
	private static Method getFunction, invokePointer, invokeInt, getInt, getPointer, getByteArray, setInt, setShort, clear;
	private static Constructor<?> ptrNew, memNew;

	private static boolean resolve() throws Exception {
		if (fnClass != null) {
			return true;
		}
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		if (!os.contains("win") || !"64".equals(System.getProperty("sun.arch.data.model", "64"))) {
			return false;
		}
		Class<?> f = Class.forName("com.sun.jna.Function");
		Class<?> p = Class.forName("com.sun.jna.Pointer");
		Class<?> m = Class.forName("com.sun.jna.Memory");
		getFunction = f.getMethod("getFunction", String.class, String.class);
		invokePointer = f.getMethod("invokePointer", Object[].class);
		invokeInt = f.getMethod("invokeInt", Object[].class);
		getInt = p.getMethod("getInt", long.class);
		getPointer = p.getMethod("getPointer", long.class);
		getByteArray = p.getMethod("getByteArray", long.class, int.class);
		setInt = p.getMethod("setInt", long.class, int.class);
		setShort = p.getMethod("setShort", long.class, short.class);
		clear = m.getMethod("clear");
		ptrNew = p.getConstructor(long.class);
		memNew = m.getConstructor(long.class);
		ptrClass = p;
		memClass = m;
		fnClass = f;
		return true;
	}

	private static Object fn(String lib, String name) throws Exception {
		return getFunction.invoke(null, lib, name);
	}

	private static Object mem(long size) throws Exception {
		Object o = memNew.newInstance(size);
		clear.invoke(o);
		return o;
	}

	/** 지금 설정된 화살표 포인터. 못 읽으면 null. 렌더 스레드가 아니어도 된다(GL을 안 건드림). */
	public static Image arrow() {
		try {
			if (!resolve()) {
				return null;
			}
			Object hCur = invokePointer.invoke(fn("user32", "LoadCursorW"), (Object) new Object[]{null, ptrNew.newInstance((long) IDC_ARROW)});
			if (hCur == null) {
				return null;
			}
			Object ii = mem(32);
			int ok = (Integer) invokeInt.invoke(fn("user32", "GetIconInfo"), (Object) new Object[]{hCur, ii});
			if (ok == 0) {
				return null;
			}
			int hotX = (Integer) getInt.invoke(ii, 4L);
			int hotY = (Integer) getInt.invoke(ii, 8L);
			Object hbmMask = getPointer.invoke(ii, 16L);
			Object hbmColor = getPointer.invoke(ii, 24L);
			Object hdc = null;
			try {
				if (hbmMask == null) {
					return null;
				}
				Object bm = mem(32);
				invokeInt.invoke(fn("gdi32", "GetObjectW"), (Object) new Object[]{hbmMask, 32, bm});
				int w = (Integer) getInt.invoke(bm, 4L);
				int maskH = Math.abs((Integer) getInt.invoke(bm, 8L));
				if (w <= 0 || maskH <= 0 || w > 256 || maskH > 512) {
					return null;
				}
				hdc = invokePointer.invoke(fn("user32", "GetDC"), (Object) new Object[]{null});
				int h = hbmColor != null ? maskH : maskH / 2;
				byte[] mask = bits(hdc, hbmMask, w, maskH);
				if (mask == null) {
					return null;
				}
				byte[] out = new byte[w * h * 4];
				if (hbmColor != null) {
					byte[] col = bits(hdc, hbmColor, w, h);
					if (col == null) {
						return null;
					}
					boolean hasAlpha = false;
					for (int i = 3; i < col.length; i += 4) {
						if (col[i] != 0) {
							hasAlpha = true;
							break;
						}
					}
					for (int i = 0, n = w * h; i < n; i++) {
						int j = i * 4;
						int a = hasAlpha ? col[j + 3] & 0xFF : ((mask[j + 2] & 0xFF) < 128 ? 255 : 0);
						out[j] = col[j + 2];       // R (DIB는 B,G,R,A)
						out[j + 1] = col[j + 1];   // G
						out[j + 2] = col[j];       // B
						out[j + 3] = (byte) a;
					}
				} else {
					// 흑백 포인터: 위 절반 = AND, 아래 절반 = XOR
					for (int i = 0, n = w * h; i < n; i++) {
						int j = i * 4;
						boolean and = (mask[j + 2] & 0xFF) >= 128;
						boolean xor = (mask[(i + n) * 4 + 2] & 0xFF) >= 128;
						int v;
						int a;
						if (!and) {
							v = xor ? 255 : 0;
							a = 255;
						} else if (xor) {
							v = 0;          // 뒤집기 - 게임 화면 위에선 검정으로
							a = 200;
						} else {
							v = 0;
							a = 0;
						}
						out[j] = (byte) v;
						out[j + 1] = (byte) v;
						out[j + 2] = (byte) v;
						out[j + 3] = (byte) a;
					}
				}
				return new Image(out, w, h, hotX, hotY);
			} finally {
				if (hdc != null) {
					invokeInt.invoke(fn("user32", "ReleaseDC"), (Object) new Object[]{null, hdc});
				}
				if (hbmMask != null) {
					invokeInt.invoke(fn("gdi32", "DeleteObject"), (Object) new Object[]{hbmMask});
				}
				if (hbmColor != null) {
					invokeInt.invoke(fn("gdi32", "DeleteObject"), (Object) new Object[]{hbmColor});
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("systemCursor", t);
			return null;
		}
	}

	/** 비트맵을 32비트 BGRA(위에서 아래로)로. */
	private static byte[] bits(Object hdc, Object hbm, int w, int h) throws Exception {
		Object bi = mem(44);
		setInt.invoke(bi, 0L, 40);
		setInt.invoke(bi, 4L, w);
		setInt.invoke(bi, 8L, -h);
		setShort.invoke(bi, 12L, (short) 1);
		setShort.invoke(bi, 14L, (short) 32);
		setInt.invoke(bi, 16L, 0);
		Object buf = mem((long) w * h * 4);
		int lines = (Integer) invokeInt.invoke(fn("gdi32", "GetDIBits"), (Object) new Object[]{hdc, hbm, 0, h, buf, bi, 0});
		if (lines <= 0) {
			return null;
		}
		return (byte[]) getByteArray.invoke(buf, 0L, w * h * 4);
	}
}
