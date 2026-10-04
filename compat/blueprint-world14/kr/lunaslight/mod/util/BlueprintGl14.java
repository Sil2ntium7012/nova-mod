package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * 49-267차: 1.14.4 설계도 홀로그램 그리기 판. 이 버전엔 VertexConsumer가 없어 블록 쿼드의 꼭짓점 자료(위치, UV)를 꺼내
 * Tessellator로 직접 그린다(블록 그림판 + 반투명 섞기 + 깊이 판정, 깊이는 안 씀). 면 밝기는 바닐라처럼 방향으로 곱한다.
 * 1.14.4에서만 컴파일된다(compat/blueprint-world14).
 */
public final class BlueprintGl14 implements BlueprintWorld.Sink {
	private boolean started;

	@Override
	public Vec3d cameraPos() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.gameRenderer == null ? null : mc.gameRenderer.getCamera().getPos();
	}

	@Override
	public void quad(Object q, double ox, double oy, double oz, float r, float g, float b, float a, double grow) {
		if (!(q instanceof BakedQuad quad)) {
			return;
		}
		BufferBuilder bb = Tessellator.getInstance().getBuffer();
		if (!started) {
			bb.begin(7, VertexFormats.POSITION_TEXTURE_COLOR);
			started = true;
		}
		float shade = shade(quad.getFace());
		int cr = c(r * shade), cg = c(g * shade), cb = c(b * shade), ca = c(a);
		int[] data = quad.getVertexData();
		int stride = data.length / 4;
		double s = 1 + grow * 2;
		for (int i = 0; i < 4; i++) {
			int o = i * stride;
			double x = Float.intBitsToFloat(data[o]), y = Float.intBitsToFloat(data[o + 1]), z = Float.intBitsToFloat(data[o + 2]);
			if (grow > 0) {
				x = 0.5 + (x - 0.5) * s;
				y = 0.5 + (y - 0.5) * s;
				z = 0.5 + (z - 0.5) * s;
			}
			float u = Float.intBitsToFloat(data[o + 4]), v = Float.intBitsToFloat(data[o + 5]);
			bb.vertex(ox + x, oy + y, oz + z).texture(u, v).color(cr, cg, cb, ca).next();
		}
	}

	@Override
	public void flush() {
		if (!started) {
			return;
		}
		started = false;
		MinecraftClient.getInstance().getTextureManager().bindTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX);
		GlStateManager.enableTexture();
		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.enableDepthTest();
		GlStateManager.depthMask(false);
		GlStateManager.enableCull();
		GlStateManager.enableAlphaTest();
		GlStateManager.alphaFunc(516, 0.003F);
		GlStateManager.color4f(1f, 1f, 1f, 1f);
		Tessellator.getInstance().draw();
		GlStateManager.alphaFunc(516, 0.1F);
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
	}

	private static float shade(Direction d) {
		if (d == null) {
			return 1f;
		}
		switch (d) {
			case DOWN:
				return 0.5f;
			case NORTH:
			case SOUTH:
				return 0.8f;
			case WEST:
			case EAST:
				return 0.6f;
			default:
				return 1f;
		}
	}

	private static int c(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}
}
