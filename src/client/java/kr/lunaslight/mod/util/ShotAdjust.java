package kr.lunaslight.mod.util;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-192차(사용자: "스크린샷 보는 곳에서 보정할 수 있게"): 스크린샷 보정 값과 그 계산.
 *
 * <p>마크 타입을 하나도 안 쓴다(자바 기본 그림 도구만) - 그래서 어느 버전에서든 같은 코드로 돈다. 계산은 전부
 * 스크린샷 화면의 백그라운드 스레드에서 한다: 미리보기는 줄인 그림(긴 변 960)으로, [새로 저장]은 원본 크기로.
 *
 * <p>값은 모두 -100~100(비네팅만 0~100):
 * <ul>
 *   <li>밝기: 노출처럼 곱한다(2^(값 × 1.2 / 100)) - 밝은 곳이 먼저 하얗게 날아가지 않는다.</li>
 *   <li>대비: 가운데(128)에서 벌리거나 좁힌다.</li>
 *   <li>채도: 회색(밝기)에서 얼마나 멀어질지.</li>
 *   <li>색온도: + 따뜻하게(빨강 올리고 파랑 내림), - 차갑게.</li>
 *   <li>비네팅: 가장자리를 어둡게.</li>
 * </ul>
 */
public final class ShotAdjust {
	public int bright, contrast, saturation, warmth, vignette;

	public boolean isIdentity() {
		return bright == 0 && contrast == 0 && saturation == 0 && warmth == 0 && vignette == 0;
	}

	public void reset() {
		bright = contrast = saturation = warmth = vignette = 0;
	}

	public ShotAdjust copy() {
		ShotAdjust a = new ShotAdjust();
		a.bright = bright;
		a.contrast = contrast;
		a.saturation = saturation;
		a.warmth = warmth;
		a.vignette = vignette;
		return a;
	}

	public static BufferedImage read(Path file) throws Exception {
		try (java.io.InputStream in = Files.newInputStream(file)) {
			BufferedImage img = javax.imageio.ImageIO.read(in);
			if (img == null) {
				throw new IllegalStateException("그림을 읽지 못함");
			}
			return img;
		}
	}

	/** 긴 변이 maxSide 이하가 되게 부드럽게 줄인다(이미 작으면 그대로 복사). */
	public static BufferedImage scaled(BufferedImage src, int maxSide) {
		int w = src.getWidth(), h = src.getHeight();
		double k = Math.min(1.0, maxSide / (double) Math.max(w, h));
		int tw = Math.max(1, (int) Math.round(w * k)), th = Math.max(1, (int) Math.round(h * k));
		BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(src, 0, 0, tw, th, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	/** 보정한 새 그림(원본은 안 건드림). */
	public BufferedImage apply(BufferedImage src) {
		int w = src.getWidth(), h = src.getHeight();
		int[] px = src.getRGB(0, 0, w, h, null, 0, w);
		float expo = (float) Math.pow(2.0, bright * 1.2 / 100.0);
		float con = 1f + contrast / 100f * (contrast > 0 ? 1.0f : 0.8f);
		float sat = 1f + saturation / 100f;
		float warm = warmth / 100f * 28f;
		float vig = vignette / 100f * 0.75f;
		double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
		double maxD2 = cx * cx + cy * cy;
		// 대비/밝기는 채널마다 같은 표로(256칸) - 픽셀마다 pow를 안 부르게
		int[] lut = new int[256];
		for (int i = 0; i < 256; i++) {
			float v = i * expo;
			v = (v - 128f) * con + 128f;
			lut[i] = Math.max(0, Math.min(255, Math.round(v)));
		}
		for (int y = 0; y < h; y++) {
			double dy = (y - cy) * (y - cy);
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				int c = px[i];
				float r = lut[(c >> 16) & 0xFF];
				float g = lut[(c >> 8) & 0xFF];
				float b = lut[c & 0xFF];
				if (sat != 1f) {
					float gray = 0.299f * r + 0.587f * g + 0.114f * b;
					r = gray + (r - gray) * sat;
					g = gray + (g - gray) * sat;
					b = gray + (b - gray) * sat;
				}
				if (warm != 0f) {
					r += warm;
					g += warm * 0.25f;
					b -= warm;
				}
				if (vig > 0f) {
					double d2 = ((x - cx) * (x - cx) + dy) / maxD2;
					float m = (float) (1.0 - vig * d2 * d2 * 0.6 - vig * d2 * 0.4);
					r *= m;
					g *= m;
					b *= m;
				}
				int ri = Math.max(0, Math.min(255, Math.round(r)));
				int gi = Math.max(0, Math.min(255, Math.round(g)));
				int bi = Math.max(0, Math.min(255, Math.round(b)));
				px[i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
			}
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		out.setRGB(0, 0, w, h, px, 0, w);
		return out;
	}

	public static byte[] png(BufferedImage img) throws Exception {
		ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
		if (!javax.imageio.ImageIO.write(img, "png", bo)) {
			throw new IllegalStateException("PNG로 바꾸지 못함");
		}
		return bo.toByteArray();
	}

	/** 원본 옆에 "이름_보정.png"(있으면 _보정2 …)로 저장하고 그 경로를 돌려준다. */
	public Path saveBeside(Path original) throws Exception {
		BufferedImage full = read(original);
		BufferedImage out = apply(full);
		String name = original.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		Path target = original.resolveSibling(base + "_보정.png");
		for (int n = 2; Files.exists(target) && n < 1000; n++) {
			target = original.resolveSibling(base + "_보정" + n + ".png");
		}
		if (!javax.imageio.ImageIO.write(out, "png", target.toFile())) {
			throw new IllegalStateException("저장하지 못함");
		}
		return target;
	}
}
