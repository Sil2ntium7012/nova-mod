package kr.lunaslight.mod.module.impl.render;

// 20차: WorldRenderContext가 버전마다 패키지/시그니처가 다른 문제(1.21.11은
// v1.world.WorldRenderEvents로 재구현됨 - Fabric API 이슈 #4902) 대응은 CrosshairOutlineModule과
// 동일 - kr.lunaslight.mod.util.LunaCompat으로 리플렉션 우회하여 5개 버전 전부에서 동작.

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;

public class ItemLightBeamModule extends Module {

	/**
	 * 49-65차(4-31 "빛 기둥 색: 따로 설정 안 했으면 클라이언트 색 따라가기"):
	 * 기본값을 <b>Luna 기본 강조색</b>으로 바꿨다. 색을 직접 고르지 않은 동안에는
	 * {@link LunaTheme#themed(int)}가 런처에서 장착한 색으로 갈아 끼워 준다 - 이미 있던 장치인데
	 * 이 모듈만 기본값이 달라서(초록 0x3DDC84) 그 대상에서 빠져 있었다.
	 *
	 * <p>알파(0xA0)는 그대로 둔다 - 빛기둥은 반투명이어야 뒤가 비친다.
	 */
	private final ColorSetting beamColor = register(new ColorSetting(
			"beam_color", "빛기둥 색", "아이템 위 빛기둥의 색입니다. 안 건드리면 클라이언트 색을 따라갑니다.",
			0xA0000000 | (LunaTheme.DEFAULT_ACCENT & 0x00FFFFFF)));

	// 49-195차(사용자: "1.3블록 이런 식으로 세부 소수점 단위까지"): 정수 → 0.1블록 단위. 같은 id라 예전 값(3)이 그대로 3.0이 된다.
	private final kr.lunaslight.mod.module.setting.FloatSetting height = register(new kr.lunaslight.mod.module.setting.FloatSetting(
			"height", "높이", "빛기둥 높이(블록)입니다.", 3f, 0.5f, 16f, 0.1f).unit("블록"));

	private final IntSetting maxBeams = register(new IntSetting(
			"max_beams", "최대 개수", "한 번에 그리는 최대 빛기둥 수입니다.", 64, 8, 256, 1));

	private final IntSetting renderDistance = register(new IntSetting(
			"render_distance", "표시 거리", "빛기둥을 그리는 최대 거리(블록)입니다.", 32, 8, 128, 1).unit("블록"));

	public ItemLightBeamModule() {
		super("item_light_beam", "빛기둥", ModuleCategory.INVENTORY, "떨어진 아이템 위 빛기둥");
	}

	private static final double HALF_THICKNESS = 0.06;

	@Override
	public void onWorldRender(Object context) {
		if (client.level == null || client.player == null) return;

		Vec3 camPos = LunaCompat.getWorldRenderCameraPos(context);
		if (camPos == null) return;
		double dist = renderDistance.get();

		AABB searchBox = new AABB(
				camPos.x - dist, camPos.y - dist, camPos.z - dist,
				camPos.x + dist, camPos.y + dist, camPos.z + dist);

		List<ItemEntity> items = LunaCompat.getEntitiesByClass(client.level, ItemEntity.class, searchBox);
		if (items == null || items.isEmpty()) return;

		PoseStack matrices = LunaCompat.getMatrices(context);
		if (matrices == null) return;
		Object consumers = LunaCompat.call(context, "consumers");

		int argb = LunaTheme.themed(beamColor.getArgb());   // 49-65차(4-31): 안 건드린 색이면 장착한 색으로
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float a = ((argb >>> 24) & 0xFF) / 255f;

		// 48-2차: "네모 와이어 박스" 대신 진짜 빛기둥 - 번개 레이어(가산 혼합) 쿼드 십자 2겹
		// (안쪽 진한 기둥 + 바깥 넓고 옅은 광채), 위로 갈수록 투명해지는 그라데이션.
		VertexConsumer beam = LunaCompat.getBeamBuffer(consumers);
		VertexConsumer lines = beam == null ? LunaCompat.getLineBuffer(consumers) : null;

		int drawn = 0;
		int cap = maxBeams.get();
		float h = height.get();
		for (ItemEntity item : items) {
			if (drawn >= cap) break;
			// 49-195차(사용자: "엔티티 줄이기에 해당하는 애들은 빛기둥 안 나오게"): 엔티티 줄이기(겹침/원거리)·가리기·가려진 것
			// 안 그리기로 안 그려지는 아이템에는 빛기둥도 안 세운다.
			if (kr.lunaslight.mod.util.EntityHideHook.shouldHide(item, camPos.x, camPos.y, camPos.z)) continue;
			Vec3 pos = LunaCompat.getPos(item);
			if (pos == null) continue;
			float bx = (float) (pos.x - camPos.x);
			float by = (float) (pos.y - camPos.y);
			float bz = (float) (pos.z - camPos.z);
			if (beam != null) {
				// 49-6차: "십자 짝대기" 느낌 제거 - 코어를 45도 간격 4장(★)으로 돌려 어느
				// 각도에서 봐도 둥근 기둥처럼 보이게 + 축 2장짜리 넓은 광채. 아이템당 6쿼드라
				// 기존(4쿼드)과 비용 차이 거의 없음(가산 혼합이라 겹칠수록 은은히 밝아짐).
				// 49-195차(사용자: "빛기둥 위쪽에 빈 공간 보이던데"): 예전엔 바닥에서 꼭대기까지 한 번에 0으로 옅어지고 광채는
				// 70% 높이에서 끝나서, 위쪽 30%가 거의 안 보이는 가는 선만 남아 기둥이 중간에 끊긴 것처럼 보였다.
				// 이제 75% 높이까지는 거의 그대로 밝고(몸통) 그 위 25%만 부드럽게 사라진다(끝). 광채도 끝까지 같이 간다.
				float inner = 0.05f;
				float d = inner * 0.7071f; // 45도 성분
				float mid = by + h * 0.75f;
				float aMid = a * 0.85f;
				float glowW = 0.16f;
				float glow = a * 0.30f;
				float glowMid = glow * 0.85f;
				for (int seg = 0; seg < 2; seg++) {
					float y0 = seg == 0 ? by : mid;
					float y1 = seg == 0 ? mid : by + h;
					float c0 = seg == 0 ? a : aMid;
					float c1 = seg == 0 ? aMid : 0f;
					float g0 = seg == 0 ? glow : glowMid;
					float g1 = seg == 0 ? glowMid : 0f;
					LunaCompat.emitBeamQuad(matrices, beam, bx - inner, bz, bx + inner, bz, y0, y1, r, g, b, c0, c1);
					LunaCompat.emitBeamQuad(matrices, beam, bx, bz - inner, bx, bz + inner, y0, y1, r, g, b, c0, c1);
					LunaCompat.emitBeamQuad(matrices, beam, bx - d, bz - d, bx + d, bz + d, y0, y1, r, g, b, c0, c1);
					LunaCompat.emitBeamQuad(matrices, beam, bx - d, bz + d, bx + d, bz - d, y0, y1, r, g, b, c0, c1);
					LunaCompat.emitBeamQuad(matrices, beam, bx - glowW, bz, bx + glowW, bz, y0, y1, r, g, b, g0, g1);
					LunaCompat.emitBeamQuad(matrices, beam, bx, bz - glowW, bx, bz + glowW, y0, y1, r, g, b, g0, g1);
				}
			} else if (lines != null) {
				// 폴백: 예전 방식(선 박스)
				AABB beamBox = new AABB(
						pos.x - HALF_THICKNESS, pos.y, pos.z - HALF_THICKNESS,
						pos.x + HALF_THICKNESS, pos.y + h, pos.z + HALF_THICKNESS);
				LunaCompat.drawBox(matrices, lines, beamBox.move(-camPos.x, -camPos.y, -camPos.z), r, g, b, a);
			}
			drawn++;
		}
	}

	// ==================== 49-220차: 월드 렌더 이벤트가 없는 버전(26.x 등) ====================
	// 제보: "26.2 아이템 빛기둥 안 보여요". 26.x는 월드 렌더가 SubmitNodeCollector 기반으로 바뀌어
	// registerWorldRenderLast가 등록을 건너뛰고(HUD 투영으로 그리기로 함) onWorldRender가 한 번도 안 불렸다.
	// 웨이포인트 레이저처럼 HUD 투영(LunaProjection.drawBeam)으로 대신 그린다. 벽 너머 아이템이 보이면 안 되니
	// 눈에서 기둥 아래, 가운데, 위 중 한 곳이라도 막힘 없이 보일 때만 그린다.

	@Override
	public void onHudRender(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
		if (LunaCompat.worldRenderAvailable() || isPreview()) {
			return;
		}
		if (client.level == null || client.player == null || LunaCompat.screenOf(client) != null) {
			return;
		}
		kr.lunaslight.mod.util.LunaProjection proj = kr.lunaslight.mod.util.LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		double dist = renderDistance.get();
		AABB searchBox = new AABB(
				proj.camX - dist, proj.camY - dist, proj.camZ - dist,
				proj.camX + dist, proj.camY + dist, proj.camZ + dist);
		List<ItemEntity> items = LunaCompat.getEntitiesByClass(client.level, ItemEntity.class, searchBox);
		if (items == null || items.isEmpty()) {
			return;
		}
		int argb = LunaTheme.themed(beamColor.getArgb());
		int col = (Math.min(255, Math.round(((argb >>> 24) & 0xFF) * 1.4f)) << 24) | (argb & 0x00FFFFFF);
		float h = height.get();
		int cap = maxBeams.get();
		int drawn = 0;
		for (ItemEntity item : items) {
			if (drawn >= cap) break;
			if (kr.lunaslight.mod.util.EntityHideHook.shouldHide(item, proj.camX, proj.camY, proj.camZ)) continue;
			Vec3 pos = LunaCompat.getPos(item);
			if (pos == null) continue;
			if (!seen(proj, pos.x, pos.y + 0.25, pos.z) && !seen(proj, pos.x, pos.y + h * 0.5, pos.z)
					&& !seen(proj, pos.x, pos.y + h * 0.95, pos.z)) {
				continue;
			}
			proj.drawBeam(context, pos.x, pos.y, pos.z, h, 0.12, col);
			drawn++;
		}
	}

	/** 카메라에서 점까지 꽉 찬 불투명 블록에 안 막히는가(0.4블록 간격으로 훑기 - DroppedItemInfoModule과 같은 방식). */
	private boolean seen(kr.lunaslight.mod.util.LunaProjection proj, double x, double y, double z) {
		try {
			double dx = x - proj.camX, dy = y - proj.camY, dz = z - proj.camZ;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1.0e-4) {
				return true;
			}
			int steps = (int) Math.ceil(d / 0.4);
			double stop = d - 0.45;
			int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
			for (int i = 1; i <= steps; i++) {
				double t = d * i / steps;
				if (t >= stop) {
					break;
				}
				double f = t / d;
				int bx = (int) Math.floor(proj.camX + dx * f);
				int by = (int) Math.floor(proj.camY + dy * f);
				int bz = (int) Math.floor(proj.camZ + dz * f);
				if (bx == lx && by == ly && bz == lz) {
					continue;
				}
				lx = bx;
				ly = by;
				lz = bz;
				if (LunaCompat.isOpaqueFullCube(client.level, new net.minecraft.core.BlockPos(bx, by, bz))) {
					return false;
				}
			}
			return true;
		} catch (Throwable ignored) {
			return true;
		}
	}
}
