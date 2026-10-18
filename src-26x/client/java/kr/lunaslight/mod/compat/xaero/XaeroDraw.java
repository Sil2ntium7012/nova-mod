package kr.lunaslight.mod.compat.xaero;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;
import xaero.lib.client.graphics.util.ImmediateRenderUtil;

import java.util.Map;

/**
 * 49-407차: Xaero 지도 위에 원/사각형을 한 번에 그린다(Xaero가 자기 fill에 쓰는 것과 같은 길 - RenderPipelines.GUI +
 * xaerolib ImmediateRenderUtil, 지금 묶인 화면(미니맵이면 미니맵 프레임버퍼)에 바로 그림). 쿼드는 앞뒤 두 방향을 다 넣는다 -
 * 지도마다 위아래가 뒤집힌 화면이 있어 면 버리기(cull)에 한쪽이 안 보일 수 있어서(한쪽만 그려짐 → 겹쳐 진해지지 않음).
 */
final class XaeroDraw {
	private XaeroDraw() {
	}

	private static BufferBuilder begin() {
		return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
	}

	private static void end(BufferBuilder b) {
		MeshData md = b.build();
		if (md != null) {
			ImmediateRenderUtil.drawImmediateMeshData(md, RenderPipelines.GUI, Map.of());
		}
	}

	private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
			float r, float g, float bl, float a) {
		b.addVertex(m, x0, y0, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x2, y2, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x3, y3, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x3, y3, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x2, y2, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, 0f).setColor(r, g, bl, a);
		b.addVertex(m, x0, y0, 0f).setColor(r, g, bl, a);
	}

	/** 가운데 (cx, cy), 반지름 rad, 굵기 w(모두 지금 행렬 단위)인 고리. 그림자처럼 바깥에 옅은 검은 테 하나. */
	static void ring(Matrix4f m, double cx, double cy, double rad, float w, int argb) {
		if (!(rad > 0.5) || rad > 4_000_000) {
			return;
		}
		int n = (int) Math.max(32, Math.min(720, rad * 0.9));
		BufferBuilder b = begin();
		ringInto(b, m, cx, cy, rad, w + 2f, 0f, 0f, 0f, 0.35f, n);
		float a = (argb >>> 24) / 255f, r = (argb >> 16 & 0xFF) / 255f, g = (argb >> 8 & 0xFF) / 255f, bl = (argb & 0xFF) / 255f;
		ringInto(b, m, cx, cy, rad, w, r, g, bl, a, n);
		end(b);
	}

	private static void ringInto(BufferBuilder b, Matrix4f m, double cx, double cy, double rad, float w, float r, float g, float bl, float a, int n) {
		double ro = rad + w / 2.0, ri = Math.max(0, rad - w / 2.0);
		double step = Math.PI * 2 / n;
		double c0 = 1, s0 = 0;
		for (int i = 1; i <= n; i++) {
			double c1 = Math.cos(i * step), s1 = Math.sin(i * step);
			quad(b, m,
					(float) (cx + c0 * ro), (float) (cy + s0 * ro),
					(float) (cx + c1 * ro), (float) (cy + s1 * ro),
					(float) (cx + c1 * ri), (float) (cy + s1 * ri),
					(float) (cx + c0 * ri), (float) (cy + s0 * ri), r, g, bl, a);
			c0 = c1;
			s0 = s1;
		}
	}

	/** (x0,y0)~(x1,y1) 안은 비우고 그 바깥을 ext만큼 칠한다(범위 밖 어둡게) + 경계선. */
	static void outside(Matrix4f m, float x0, float y0, float x1, float y1, float ext, int shade, int line, float lw) {
		BufferBuilder b = begin();
		float a = (shade >>> 24) / 255f, r = (shade >> 16 & 0xFF) / 255f, g = (shade >> 8 & 0xFF) / 255f, bl = (shade & 0xFF) / 255f;
		float L = x0 - ext, R = x1 + ext, T = y0 - ext, B = y1 + ext;
		rect(b, m, L, T, R, y0, r, g, bl, a);   // 위
		rect(b, m, L, y1, R, B, r, g, bl, a);   // 아래
		rect(b, m, L, y0, x0, y1, r, g, bl, a); // 왼쪽
		rect(b, m, x1, y0, R, y1, r, g, bl, a); // 오른쪽
		float la = (line >>> 24) / 255f, lr = (line >> 16 & 0xFF) / 255f, lg = (line >> 8 & 0xFF) / 255f, lb = (line & 0xFF) / 255f;
		rect(b, m, x0 - lw, y0 - lw, x1 + lw, y0, lr, lg, lb, la);
		rect(b, m, x0 - lw, y1, x1 + lw, y1 + lw, lr, lg, lb, la);
		rect(b, m, x0 - lw, y0, x0, y1, lr, lg, lb, la);
		rect(b, m, x1, y0, x1 + lw, y1, lr, lg, lb, la);
		end(b);
	}

	private static void rect(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float bl, float a) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		quad(b, m, x1, y0, x0, y0, x0, y1, x1, y1, r, g, bl, a);
	}
}
