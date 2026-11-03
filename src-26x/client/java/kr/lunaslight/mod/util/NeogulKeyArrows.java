package kr.lunaslight.mod.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-503차(사용자: "너굴마을 리소스팩에 보면 낚시 미니게임 WASD로 돼 있는 거 화살표로도 보이게, 선택 기능, 리팩 비슷하게 최대한"):
 * 너굴마을 리소스팩의 키캡 글자 그림(ng:textures/font/key_w/a/s/d(_gray).png - nexo:default 글꼴 U+A4FC~U+A503)을 읽을 때
 * 글자만 지우고 같은 그림체(2배 픽셀, 흰 획 + 획 아래 끝 그림자)로 ↑ ← ↓ →를 그려 넘긴다. 키캡 색, 테두리, 바닥은 리소스팩 그림 그대로.
 * {@code ResourceArrowMixin}이 리소스를 꺼낼 때 부른다. 켜고 끄면 리소스를 다시 불러온다.
 */
public final class NeogulKeyArrows {
	private NeogulKeyArrows() {
	}

	/** 기능 켬/끔(NeogulKeyArrowsModule이 넣는다). */
	public static volatile java.util.function.BooleanSupplier state = () -> false;
	/** 마지막 리소스 불러오기 때 바꿨는지. */
	public static volatile boolean applied;

	public static boolean on() {
		try {
			return state.getAsBoolean();
		} catch (Throwable t) {
			return false;
		}
	}

	/** 경로 → 방향(0 위, 1 왼쪽, 2 아래, 3 오른쪽). */
	private static final Map<String, Integer> DIR = Map.of(
		"textures/font/key_w.png", 0, "textures/font/key_w_gray.png", 0,
		"textures/font/key_a.png", 1, "textures/font/key_a_gray.png", 1,
		"textures/font/key_s.png", 2, "textures/font/key_s_gray.png", 2,
		"textures/font/key_d.png", 3, "textures/font/key_d_gray.png", 3);

	/** ng 네임스페이스 키캡 그림이면 방향, 아니면 -1. */
	public static int dirOf(String namespace, String path) {
		if (!"ng".equals(namespace)) {
			return -1;
		}
		Integer d = DIR.get(path);
		if (d == null) {
			return -1;
		}
		boolean o = on();
		applied = o;
		return o ? d : -1;
	}

	/** 위 화살표(논리 픽셀 10×14, 획 2칸) + 아래 그림자 1칸 = 글자(W 14칸 + 그림자)와 같은 크기. */
	private static final String[] UP = {
		"....##....",
		"...####...",
		"..######..",
		".########.",
		"##########",
		"##..##..##",
		"....##....",
		"....##....",
		"....##....",
		"....##....",
		"....##....",
		"....##....",
		"....##....",
		"....##....",
	};

	private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

	/** 원본 PNG를 읽어 화살표로 바꾼 PNG. 실패하면 원본 그대로. */
	public static byte[] convert(String key, InputStream in, int dir) throws java.io.IOException {
		byte[] src = in.readAllBytes();
		String ck = key + "#" + java.util.Arrays.hashCode(src);
		byte[] hit = CACHE.get(ck);
		if (hit != null) {
			return hit;
		}
		byte[] out;
		try {
			out = draw(src, dir);
		} catch (Throwable t) {
			LunaCompat.warnOnce("keyArrows", t);
			out = src;
		}
		CACHE.put(ck, out);
		return out;
	}

	private static byte[] draw(byte[] src, int dir) throws java.io.IOException {
		BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(src));
		if (img == null) {
			return src;
		}
		int w = img.getWidth(), h = img.getHeight();
		BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		im.getGraphics().drawImage(img, 0, 0, null);
		// 64×64 = 2배 픽셀(논리 32×32). 다른 크기도 비율로.
		int u = Math.max(1, w / 32);
		int face = im.getRGB(14 * w / 64, 24 * h / 64);
		// 글자 자리(논리 x 6~25, y 5~19): 흰 획과 그림자를 찾고 키캡 면 색으로 지운다
		int x1 = 6 * u, x2 = 26 * u, y1 = 5 * u, y2 = 20 * u;
		int white = 0xFFFFFFFF, shadow = 0;
		for (int y = y1; y < y2; y++) {
			for (int x = x1; x < x2; x++) {
				int c = im.getRGB(x, y);
				if (c == face) {
					continue;
				}
				int lum = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
				if (lum > 600) {
					white = c;
				} else if (shadow == 0) {
					shadow = c;
				}
				im.setRGB(x, y, face);
			}
		}
		if (shadow == 0) {
			shadow = darker(face, 0.53f);
		}
		// 방향대로 돌린 격자
		boolean[][] g = grid(dir);
		int gw = g[0].length, gh = g.length;
		// 글자 가운데(논리 x 11~20, y 5~19)에 맞춘다
		int ox = (16 - (gw + 1) / 2) * u, oy = (5 + (15 - (gh + 1)) / 2) * u;
		for (int y = 0; y < gh; y++) {
			for (int x = 0; x < gw; x++) {
				if (!g[y][x]) {
					continue;
				}
				cell(im, ox, oy, u, x, y, white);
				// 원래 글자와 같은 규칙: 획의 아래 끝 바로 밑 한 칸에 그림자
				if (!(y + 1 < gh && g[y + 1][x])) {
					cell(im, ox, oy, u, x, y + 1, shadow);
				}
			}
		}
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(im, "png", bo);
		return bo.toByteArray();
	}

	private static void cell(BufferedImage im, int ox, int oy, int u, int x, int y, int c) {
		for (int dy = 0; dy < u; dy++) {
			for (int dx = 0; dx < u; dx++) {
				int px = ox + x * u + dx, py = oy + y * u + dy;
				if (px >= 0 && py >= 0 && px < im.getWidth() && py < im.getHeight()) {
					im.setRGB(px, py, c);
				}
			}
		}
	}

	private static boolean[][] grid(int dir) {
		int H = UP.length, W = UP[0].length();
		boolean[][] up = new boolean[H][W];
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				up[y][x] = UP[y].charAt(x) != '.';
			}
		}
		return switch (dir) {
			case 2 -> { // 아래: 위아래 뒤집기
				boolean[][] o = new boolean[H][W];
				for (int y = 0; y < H; y++) {
					o[y] = up[H - 1 - y].clone();
				}
				yield o;
			}
			case 1 -> { // 왼쪽: 위 화살표를 반시계로 90°
				boolean[][] o = new boolean[W][H];
				for (int y = 0; y < H; y++) {
					for (int x = 0; x < W; x++) {
						o[W - 1 - x][y] = up[y][x];
					}
				}
				yield o;
			}
			case 3 -> { // 오른쪽: 시계로 90°
				boolean[][] o = new boolean[W][H];
				for (int y = 0; y < H; y++) {
					for (int x = 0; x < W; x++) {
						o[x][H - 1 - y] = up[y][x];
					}
				}
				yield o;
			}
			default -> up;
		};
	}

	private static int darker(int c, float k) {
		int r = Math.round(((c >> 16) & 0xFF) * k), g = Math.round(((c >> 8) & 0xFF) * k), b = Math.round((c & 0xFF) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
