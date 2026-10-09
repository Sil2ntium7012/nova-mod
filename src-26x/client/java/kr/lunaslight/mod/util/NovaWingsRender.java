package kr.lunaslight.mod.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;


/**
 * 49-270차: 노바 날개 그리기(26.x). BlueprintWorldMixin이 LevelRenderer#submitEntities 끝에서 날개 모듈의 onWorldRender(BlueprintWorld.Ctx)를
 * 부르면, 주변 플레이어마다 {@link NovaWings#build}로 만든 꼭짓점을 엔티티 반투명 렌더 타입(날개 그림, 양면)의 사용자 정의 도형으로 넣는다.
 * 플레이어 렌더러를 안 건드리고 몸 위치, 몸 방향, 웅크림만 읽어 붙인다(자는 중, 수영 자세, 겉날개 비행은 안 그린다).
 */
public final class NovaWingsRender {
	private NovaWingsRender() {
	}

	private static boolean broken;

	public static void fail(Throwable t) {
		broken = true;
		LunaCompat.warnOnce("wings:render", t);
	}

	public static void draw(Object context, boolean others, boolean flush) {
		if (broken || !(context instanceof BlueprintWorld.Ctx c)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return;
		}
		try {
			float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			boolean firstPerson = mc.options.getCameraType().isFirstPerson();
			double time = System.nanoTime() / 1e9;
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p == mc.player && firstPerson && mc.getCameraEntity() == p) {
					continue;
				}
				if (p.isInvisible() || p.isSleeping() || p.isVisuallySwimming() || p.isFallFlying()) {
					continue;
				}
				if (AfkWatch.afkEntity(p)) {
					continue;   // 49-312차: 자리 비움이면 치장(날개) 숨김
				}
				String key = NovaWings.keyFor(p.getUUID().toString(), others);
				if (key == null) {
					continue;
				}
				NovaWings.Asset a = NovaWings.asset(key);
				Identifier tex = NovaWings.texture(a);
				if (tex == null) {
					continue;
				}
				// 49-272차: 엔티티 렌더러와 같은 보간(xOld, xo 아님) - 다르면 움직일 때 날개가 몸에서 떨어져 보인다
				double px = p.xOld + (p.getX() - p.xOld) * td, py = p.yOld + (p.getY() - p.yOld) * td, pz = p.zOld + (p.getZ() - p.zOld) * td;
				double dx = px - c.cam.x, dz = pz - c.cam.z;
				if (dx * dx + dz * dz > 96 * 96) {
					continue;
				}
				float d = (p.yBodyRot - p.yBodyRotO) % 360f;
				if (d >= 180f) {
					d -= 360f;
				}
				if (d < -180f) {
					d += 360f;
				}
				float by = p.yBodyRotO + d * td;
				float[] buf = new float[Math.max(32, a.maxVertices() * 8)];
				int n = NovaWings.build(a, time + (p.getUUID().hashCode() & 0xFF) / 97.0, by, p.isCrouching(), dx, py - c.cam.y, dz, buf);
				if (n == 0) {
					continue;
				}
				int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, td);
				RenderType type = RenderTypes.entityTranslucent(tex);
				final int count = n;
				c.collector.submitCustomGeometry(c.pose, type, (pose, vc) -> emit(pose, vc, buf, count, light));
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] buf, int n, int light) {
		try {
			for (int i = 0; i < n; i++) {
				int b = i * 8;
				vc.addVertex(pose, buf[b], buf[b + 1], buf[b + 2])
						.setColor(0xFFFFFFFF)
						.setUv(buf[b + 3], buf[b + 4])
						.setOverlay(655360)
						.setLight(light)
						.setNormal(pose, buf[b + 5], buf[b + 6], buf[b + 7]);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}
}
