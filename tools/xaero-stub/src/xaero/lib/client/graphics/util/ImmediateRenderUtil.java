package xaero.lib.client.graphics.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.MeshData;

/** 노바 컴파일용 껍데기(Xaero 라이브러리, 두 Xaero 모드가 같이 씀). */
public final class ImmediateRenderUtil {
	private ImmediateRenderUtil() {
	}

	public static void drawImmediateMeshData(MeshData meshData, RenderPipeline renderPipeline, java.util.Map<String, ?> textures) {
		throw new UnsupportedOperationException();
	}
}
