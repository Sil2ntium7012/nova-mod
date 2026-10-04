package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 49-270차: 1.14.4 노바 날개 그리기. 이 버전엔 VertexConsumer도 렌더 레이어도 없어 {@link NovaWings#build}가 만든 꼭짓점을
 * Tessellator로 직접 그린다(날개 그림을 묶고 반투명 섞기, 양면, 면 방향으로 밝기). BlueprintWorld14Mixin이 반투명 지형 다음에 부른다.
 * 1.14.4에서만 컴파일된다(compat/blueprint-world14).
 */
public final class NovaWingsGl14 {
	private NovaWingsGl14() {
	}

	private static final float[] BUF = new float[8 * 4 * 64];
	private static boolean broken;

	public static void draw(boolean others) {
		if (broken) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null || mc.gameRenderer == null) {
			return;
		}
		try {
			Vec3d cam = mc.gameRenderer.getCamera().getPos();
			float td = mc.getTickDelta();
			boolean firstPerson = mc.options.perspective == 0;
			double time = System.nanoTime() / 1e9;
			for (Object o : mc.world.getPlayers()) {
				if (!(o instanceof LivingEntity p)) {
					continue;
				}
				if (p == mc.player && firstPerson && mc.getCameraEntity() == p) {
					continue;
				}
				if (p.isInvisible() || p.isSleeping() || flag(p, "isInSwimmingPose") || flag(p, "isFallFlying")) {
					continue;
				}
				String key = NovaWings.keyFor(p.getUuid().toString(), others);
				if (key == null) {
					continue;
				}
				NovaWings.Asset a = NovaWings.asset(key);
				Identifier tex = NovaWings.texture(a);
				if (tex == null) {
					continue;
				}
				Vec3d now = LunaCompat.getPos(p);
				if (now == null) {
					continue;
				}
				double px = p.lastRenderX + (now.x - p.lastRenderX) * td;
				double py = p.lastRenderY + (now.y - p.lastRenderY) * td;
				double pz = p.lastRenderZ + (now.z - p.lastRenderZ) * td;
				if ((px - cam.x) * (px - cam.x) + (pz - cam.z) * (pz - cam.z) > 96 * 96) {
					continue;
				}
				float d = (p.field_6283 - p.field_6220) % 360f;
				if (d >= 180f) {
					d -= 360f;
				}
				if (d < -180f) {
					d += 360f;
				}
				float by = p.field_6220 + d * td;
				Object pose = LunaCompat.callNoArg(p, "isInSneakingPose");   // 49-272차: 웅크린 자세만(쉬프트 아님)
				boolean sneak = pose instanceof Boolean b ? b : p.isSneaking();
				int n = NovaWings.build(a, time + (p.getUuid().hashCode() & 0xFF) / 97.0, by, sneak, px - cam.x, py - cam.y, pz - cam.z, BUF);
				if (n == 0) {
					continue;
				}
				mc.getTextureManager().bindTexture(tex);
				BufferBuilder bb = Tessellator.getInstance().getBuffer();
				bb.begin(7, VertexFormats.POSITION_TEXTURE_COLOR);
				for (int i = 0; i < n; i++) {
					int b = i * 8;
					float shade = 0.75f + 0.25f * Math.abs(BUF[b + 6]) + 0.1f * Math.abs(BUF[b + 7]);
					int c = Math.max(0, Math.min(255, Math.round(255 * Math.min(1f, shade))));
					bb.vertex(BUF[b], BUF[b + 1], BUF[b + 2]).texture(BUF[b + 3], BUF[b + 4]).color(c, c, c, 255).next();
				}
				GlStateManager.enableTexture();
				GlStateManager.disableLighting();
				GlStateManager.enableBlend();
				GlStateManager.blendFuncSeparate(770, 771, 1, 0);
				GlStateManager.enableDepthTest();
				GlStateManager.depthMask(true);
				GlStateManager.disableCull();
				GlStateManager.enableAlphaTest();
				GlStateManager.alphaFunc(516, 0.1F);
				GlStateManager.color4f(1f, 1f, 1f, 1f);
				Tessellator.getInstance().draw();
				GlStateManager.enableCull();
				GlStateManager.disableBlend();
			}
		} catch (Throwable t) {
			broken = true;
			LunaCompat.warnOnce("wings:gl14", t);
		}
	}

	private static boolean flag(Object e, String name) {
		return Boolean.TRUE.equals(LunaCompat.callNoArg(e, name));
	}
}
