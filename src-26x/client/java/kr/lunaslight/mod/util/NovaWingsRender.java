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
		draw(context, others, flush, NovaWings.WING);
	}

	/** 49-351차: 칸(날개/가방)을 골라 그린다 - 가방 모듈은 NovaWings.BAG으로 부른다. */
	public static void draw(Object context, boolean others, boolean flush, NovaWings.Slot slot) {
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
			float lift = slot == NovaWings.BAG ? kr.lunaslight.mod.module.impl.render.BagsModule.lift() : 0f;   // 49-352차
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
				for (String key : slot.keysFor(p.getUUID().toString(), others)) {   // 49-355차: 모자는 한 사람이 2개까지
				NovaWings.Asset a = slot.asset(key);   // 49-353차: 칸마다 묶음 이름(모자 = hat-<key>)
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
				float pitch = 0f;
				if (slot.head) {
					// 49-353차: 모자는 머리 방향과 숙임을 따른다(바닐라 머리와 같은 보간)
					float hd = (p.yHeadRot - p.yHeadRotO) % 360f;
					if (hd >= 180f) {
						hd -= 360f;
					}
					if (hd < -180f) {
						hd += 360f;
					}
					by = p.yHeadRotO + hd * td;
					pitch = p.getViewXRot(td);
				}
				float[] buf = new float[Math.max(32, a.maxVertices() * 8)];
				int n = NovaWings.build(a, time + (p.getUUID().hashCode() & 0xFF) / 97.0, by, p.isCrouching(), dx, py - c.cam.y, dz, lift,
						slot.head, pitch, buf);
				if (n == 0) {
					continue;
				}
				scaleAbout(buf, n, dx, py - c.cam.y, dz, p.getScale());   // 49-370차
				int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, td);
				RenderType type = RenderTypes.entityTranslucent(tex);
				final int count = n;
				c.collector.submitCustomGeometry(c.pose, type, (pose, vc) -> emit(pose, vc, buf, count, light));
				}
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** 49-364차: 발자취 입자(NovaTrails가 카메라 기준으로 만든 사각형, 꼭짓점마다 색). 밝기는 늘 최대(반짝이). */
	public static void drawTrails(Object context) {
		if (broken || !NovaTrails.any() || !(context instanceof BlueprintWorld.Ctx c)) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.level == null) {
				return;
			}
			Identifier tex = NovaWings.texture(NovaWings.asset(NovaTrails.ASSET));
			if (tex == null) {
				return;
			}
			float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			int n = NovaTrails.build(c.cam.x, c.cam.y, c.cam.z, td);
			if (n == 0) {
				return;
			}
			final float[] copy = java.util.Arrays.copyOf(NovaTrails.buffer(), n * NovaTrails.STRIDE);
			final int count = n;
			c.collector.submitCustomGeometry(c.pose, RenderTypes.entityTranslucent(tex), (pose, vc) -> emitTrail(pose, vc, copy, count));
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void emitTrail(PoseStack.Pose pose, VertexConsumer vc, float[] buf, int n) {
		try {
			for (int i = 0; i < n; i++) {
				int b = i * NovaTrails.STRIDE;
				vc.addVertex(pose, buf[b], buf[b + 1], buf[b + 2])
						.setColor(NovaTrails.color(buf[b + 8]))
						.setUv(buf[b + 3], buf[b + 4])
						.setOverlay(655360)
						.setLight(0xF000F0)
						.setNormal(pose, buf[b + 5], buf[b + 6], buf[b + 7]);
			}
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** 49-370차(사용자: "치장이 마크 캐릭 크기가 달라지면 같아지게"): 몸 크기(배율 속성, LivingEntity#getScale)만큼 발 기준으로 늘리거나 줄인다. */
	private static void scaleAbout(float[] b, int n, double ox, double oy, double oz, float s) {
		if (Float.isNaN(s) || s <= 0.01f || Math.abs(s - 1f) < 1e-3) {
			return;
		}
		for (int i = 0; i < n; i++) {
			int k = i * 8;
			b[k] = (float) (ox + (b[k] - ox) * s);
			b[k + 1] = (float) (oy + (b[k + 1] - oy) * s);
			b[k + 2] = (float) (oz + (b[k + 2] - oz) * s);
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
