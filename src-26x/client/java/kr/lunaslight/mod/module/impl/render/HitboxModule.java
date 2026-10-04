package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * 49-66차(4-50): <b>히트박스</b> - 엔티티가 실제로 <b>맞는 범위</b>를 선으로 그린다.
 *
 * <p>사용자 요청 4-50: "실제 엔티티 피격 히트박스 표시".
 *
 * <p><b>F3+B와 무엇이 다른가</b>: 바닐라 F3+B는 엔티티의 <b>충돌 상자</b>를 그린다. 그런데 공격이
 * 실제로 맞는 범위는 그것보다 조금 넓다 - 마인크래프트는 조준 판정에 {@code getTargetingMargin}만큼
 * 부풀린 상자를 쓴다(대부분 0.1블록). PVP에서 "맞은 것 같은데 안 맞았다"가 갈리는 바로 그 여유다.
 * 그래서 <b>[피격 여유 포함]</b>을 기본으로 켜 두고, 끄면 F3+B와 같은 상자를 그린다.
 *
 * <p><b>왜 월드 렌더가 아니라 HUD에서 그리나</b>: Fabric의 월드 렌더 이벤트가 1.21.9+에서 업스트림에서
 * 제거됐다(49-19차 기록). 사용자의 주 버전이 1.21.11이라, 월드 렌더에 얹으면 <b>정작 쓰는 버전에서
 * 안 나오는 기능</b>이 된다. 그래서 블록 테두리(49-22차)가 쓰는 것과 같은 <b>2D 투영</b> 길을 쓴다 -
 * 카메라 행렬로 직접 화면 좌표를 구해 HUD에 선을 긋는 방식이라 전 버전에서 똑같이 나온다.
 *
 * <p><b>성능</b>: 거리 안의 엔티티만, 그것도 {@link #MAX_ENTITIES}개까지만 그린다. 몹 농장 한가운데서
 * 수백 개를 투영하면 이 기능 때문에 프레임이 떨어지는데, 그건 본말전도다.
 */
public class HitboxModule extends Module {

	private static final int MAX_ENTITIES = 64;

	/** 정육면체 열두 모서리(꼭짓점 번호 비트: bit0 = x, bit1 = z, bit2 = y). */
	private static final int[][] EDGES = {
		{0, 1}, {1, 3}, {3, 2}, {2, 0},
		{4, 5}, {5, 7}, {7, 6}, {6, 4},
		{0, 4}, {1, 5}, {2, 6}, {3, 7},
	};

	private final IntSetting distance = register(new IntSetting(
			"distance", "거리", "이 거리(블록) 안의 엔티티만 그립니다.", 16, 4, 48, 2).unit("블록"));
	private final ColorSetting color = register(new ColorSetting(
			"color", "선 색", "히트박스 선의 색입니다.", 0xC0FF5C5C));
	private final BooleanSetting margin = register(new BooleanSetting(
			"margin", "피격 여유 포함", "실제 조준 판정에 쓰이는 여유(대개 0.1블록)만큼 넓혀 그립니다.", true));
	private final BooleanSetting playersOnly = register(new BooleanSetting(
			"players_only", "플레이어 한정", "플레이어만 그립니다. 끄면 몹/아이템까지 전부 그립니다.", false));
	private final FloatSetting lineWidth = register(new FloatSetting(
			"line_width", "선 두께", "선 두께(픽셀)입니다.", 1.0f, 0.5f, 3.0f, 0.25f).unit("px").style());

	public HitboxModule() {
		super("hitbox", "히트박스", ModuleCategory.COMBAT, "엔티티가 실제로 맞는 범위를 선으로");
	}

	private final double[][] view = new double[8][3];

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview() || client.level == null || client.player == null || kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		double max = distance.get();
		double maxSq = max * max;
		double px = client.player.getX();
		double py = client.player.getY();
		double pz = client.player.getZ();
		int argb = color.getArgb();
		float w = lineWidth.get();
		boolean onlyPlayers = playersOnly.get();
		boolean withMargin = margin.get();
		int drawn = 0;
		try {
			for (Entity e : client.level.entitiesForRendering()) {
				if (drawn >= MAX_ENTITIES) {
					break;
				}
				if (e == null || e == client.player) {
					continue;
				}
				if (onlyPlayers && !(e instanceof net.minecraft.world.entity.player.Player)) {
					continue;
				}
				double dx = e.getX() - px;
				double dy = e.getY() - py;
				double dz = e.getZ() - pz;
				if (dx * dx + dy * dy + dz * dz > maxSq) {
					continue;
				}
				// 49-198차(사용자: "히트박스 같은 거 벽 뚫고 보이면 안 되지 - ESP잖아"): 화면에 선을 긋는 방식이라 깊이 검사가 없어
				// 벽 너머 엔티티도 다 보였다. 내 눈에서 블록에 가리지 않고 보이는 엔티티만 그린다(바닐라 시야 검사, 이름표와 같은 기준).
				if (!NametagVisibilityModule.canSeeCached(e)) {
					continue;
				}
				AABB box = e.getBoundingBox();
				if (box == null) {
					continue;
				}
				if (withMargin) {
					double m = targetingMargin(e);
					if (m > 0) {
						box = box.inflate(m);
					}
				}
				drawBox(context, proj, box, argb, w);
				drawn++;
			}
		} catch (Throwable ignored) {
			// 월드가 바뀌는 중 등 - 이번 프레임은 건너뛴다
		}
	}

	/** 조준 판정에 쓰이는 여유. 버전마다 이름이 같지만 안전하게 리플렉션(결과는 클래스별로 캐시된다). */
	private static double targetingMargin(Entity e) {
		Object v = LunaCompat.callNoArg(e, "getTargetingMargin");
		return v instanceof Float f ? f : 0.0;
	}

	private void drawBox(GuiGraphicsExtractor context, LunaProjection proj, AABB box, int argb, float width) {
		double[] bb = LunaCompat.boxBounds(box);   // 49-36차: 1.15.2는 필드 이름이 달라 리플렉션
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? bb[0] : bb[3];
			double z = (i & 2) == 0 ? bb[2] : bb[5];
			double y = (i & 4) == 0 ? bb[1] : bb[4];
			proj.toView(x, y, z, view[i]);
		}
		for (int[] edge : EDGES) {
			proj.drawViewSegment(context, view[edge[0]], view[edge[1]], width, argb);
		}
	}
}
