package kr.lunaslight.mod.module.impl.render;

// 20차: WorldRenderContext는 버전마다 패키지/시그니처가 달라 Module.onWorldRender(Object)로 받아
// LunaCompat 리플렉션으로 우회한다(등록은 LunaCompat.registerWorldRenderLast).

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-16차 재작성. 사용자 지적 2건:
 *
 *  1) "블록이 작은데 테두리는 큰 경우가 있어" - 예전엔 VoxelShape의 **경계 상자 하나**만 썼다.
 *     울타리·담장·계단·상자·모루처럼 여러 조각으로 된 블록은 경계 상자가 실제 모양보다 훨씬
 *     크다(울타리 = 기둥 4×16×4 + 팔인데 경계 상자는 16×16×16). 이제 바닐라 선택 윤곽과 같이
 *     **VoxelShape 안의 모든 조각 상자**를 각각 그린다 → 실제 실루엣과 정확히 일치.
 *
 *  2) "안 보이는 부분도 보여야 해" - 3D 선(RenderLayer LINES)은 깊이 검사를 하므로 블록에 가린
 *     뒷면 모서리가 안 보인다. "가려진 부분 표시"를 켜면 **HUD 패스에서 화면 좌표로 직접 투영해
 *     2D 선**으로 그린다 - 깊이 검사 자체가 없어 모든 모서리가 보이고, 마크 API에 안 기대므로
 *     어느 버전에서도 같은 코드가 돈다. 투영 입력은 LunaProjection.
 *
 * 49-22차:
 *  ③ "'실제 모양대로' 설정 삭제(항상)" - 조각 상자 전부 그리기를 기본 동작으로 고정.
 *  ④ "AA 아직 덜 됨·너무 두꺼움" - 선을 픽셀 fill 수천 개 대신 **회전 사각형 + 페더 그라데이션**
 *     (LunaProjection.lineQuad)으로 그려 GPU가 알파를 보간(진짜 AA) + 굵기를 0.25px 단위 실수로.
 *     기본 1px. 이게 "테두리 뜰 때 프레임 드랍"의 주범이었다(선당 fill 수백 개 → 3개).
 *  ⑤ "동물은 히트박스 말고 모양 따라가게" - 엔티티는 상자 대신 **바닐라 발광 윤곽**(setGlowing +
 *     getTeamColorValue 믹스인으로 색 지정)을 쓴다. 모델 실루엣을 그대로 따라가고 벽 너머로도 보인다.
 */
public class CrosshairOutlineModule extends Module {

	private static final int MAX_BOXES = 12;   // 조각이 아주 많은 블록은 이 개수까지만(성능)

	/**
	 * 49-66차(4-8 "테두리 블록/엔티티 나누기"): 색을 <b>둘로 나눴다</b>. 예전엔 한 색이라 블록을 보는지
	 * 몹을 보는지 테두리만으로는 구분이 안 됐다 - 서로 겹쳐 있을 때(몹이 블록 앞에 선 상황)가 특히 그랬다.
	 * 켜고 끄는 스위치는 원래부터 따로 있었으니, 색까지 나뉘면 "나누기"가 끝난다.
	 */
	private final ColorSetting outlineColor = register(new ColorSetting(
			"outline_color", "블록 색", "조준한 블록의 테두리 색입니다.", 0xFFA9D973));

	private final ColorSetting entityColor = register(new ColorSetting(
			"entity_color", "엔티티 색", "조준한 엔티티의 윤곽 색입니다.", 0xFFFFB067));

	private final BooleanSetting outlineBlocks = register(new BooleanSetting(
			"outline_blocks", "블록", "블록을 조준하면 테두리를 그립니다.", true).withColor(outlineColor));

	private final BooleanSetting outlineEntities = register(new BooleanSetting(
			"outline_entities", "엔티티", "엔티티를 조준하면 모양을 따라 빛나는 윤곽을 그립니다.", true).withColor(entityColor));

	private final BooleanSetting seeThrough = register(new BooleanSetting(
			"see_through", "가려진 선", "블록에 가려진 뒷면 모서리까지 그립니다.", true));

	private final FloatSetting lineWidth = register(new FloatSetting(
			"line_width", "선 두께", "선 두께(픽셀)입니다.", 1.0f, 0.5f, 3.0f, 0.25f).unit("px").style());

	public CrosshairOutlineModule() {
		super("crosshair_outline", "테두리", ModuleCategory.VIEW, "조준한 블록/엔티티 테두리(색 따로)");
	}

	// ==================== 대상 상자 수집 ====================

	/** 지금 조준 중인 블록의 상자 목록(월드 좌표). 없으면 빈 목록. */
	private List<Box> targetBoxes() {
		List<Box> boxes = new ArrayList<>();
		if (client.world == null || client.player == null || !outlineBlocks.get()) {
			return boxes;
		}
		// 49-223차(사용자: "블록 테두리는 관전일 때 안 보이게"): 바닐라도 관전 모드에선 블록 윤곽을 안 그린다
		if (client.player.isSpectator()) {
			return boxes;
		}
		HitResult target = client.crosshairTarget;
		if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
			return boxes;
		}
		BlockPos pos = blockHit.getBlockPos();
		var state = client.world.getBlockState(pos);
		if (state.isAir()) {
			return boxes;
		}
		net.minecraft.util.shape.VoxelShape shape = null;
		try {
			shape = state.getOutlineShape(client.world, pos);
		} catch (Throwable t) {
			LunaCompat.warnOnce("outline:shape", t);
		}
		if (shape == null || shape.isEmpty()) {
			boxes.add(new Box(pos));
			return boxes;
		}
		// 바닐라 선택 윤곽과 동일하게 조각 상자를 전부 그림(울타리/계단/담장/상자…) - 49-22차: 항상
		try {
			for (Box part : shape.getBoundingBoxes()) {
				boxes.add(part.offset(pos.getX(), pos.getY(), pos.getZ()));
				if (boxes.size() >= MAX_BOXES) {
					break;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("outline:boxes", t);
		}
		if (boxes.isEmpty()) {
			boxes.add(shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ()));
		}
		return boxes;
	}

	// ==================== 엔티티 윤곽(바닐라 발광) ====================

	private static Entity glowing;          // 지금 우리가 빛나게 만든 엔티티
	private static int glowColor;           // 믹스인용 색(0xRRGGBB)

	/** 믹스인(EntityTeamColorMixin)용: 이 엔티티가 우리가 윤곽을 켠 대상이면 색, 아니면 -1. */
	public static int outlineColorFor(Object entity) {
		return entity != null && entity == glowing ? glowColor : -1;
	}

	private void updateEntityGlow() {
		Entity want = null;
		if (outlineEntities.get() && client.world != null && client.currentScreen == null
				&& client.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY) {
			want = hit.getEntity();
		}
		if (want != glowing) {
			setGlow(glowing, false);
			glowing = want;
			setGlow(want, true);
		}
		if (glowing != null && !glowing.isAlive()) {
			setGlow(glowing, false);
			glowing = null;
		}
		glowColor = entityColor.getArgb() & 0x00FFFFFF;   // 49-66차(4-8): 엔티티는 엔티티 색으로
	}

	private static void setGlow(Entity e, boolean on) {
		if (e == null) {
			return;
		}
		try {
			e.setGlowing(on);
		} catch (Throwable t) {
			LunaCompat.warnOnce("outline:glow", t);
		}
	}

	@Override
	protected void onDisable() {
		setGlow(glowing, false);
		glowing = null;
	}

	@Override
	public void onTick() {
		if (glowing != null && (client.world == null || !glowing.isAlive())) {
			setGlow(glowing, false);
			glowing = null;
		}
	}

	// ==================== 3D 선(가려진 부분 표시 끈 경우) ====================

	@Override
	public void onWorldRender(Object context) {
		if (seeThrough.get()) {
			return; // 가려진 부분 표시 모드에서는 HUD 패스에서 2D로 그림(중복 방지)
		}
		List<Box> boxes = targetBoxes();
		if (boxes.isEmpty()) {
			return;
		}
		Vec3d camPos = LunaCompat.getWorldRenderCameraPos(context);
		if (camPos == null) {
			return;
		}
		MatrixStack matrices = LunaCompat.getMatrices(context);
		if (matrices == null) {
			return;
		}
		VertexConsumer buffer = LunaCompat.getLineBuffer(LunaCompat.call(context, "consumers"));
		if (buffer == null) {
			return;
		}
		int argb = outlineColor.getArgb();
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float a = outlineColor.getAlpha01();
		for (Box box : boxes) {
			LunaCompat.drawBox(matrices, buffer, box.offset(-camPos.x, -camPos.y, -camPos.z), r, g, b, a);
		}
	}

	// ==================== 2D 투영(가려진 부분까지 전부) ====================

	// 49-47차(사용자: "미리보기 필요 없는 것들은 없애도 돼"): 블록 테두리는 월드 안에 그리는 것이라
	// 작은 미리보기 칸에 담으면 실제와 전혀 달라 보인다 - 미리보기 없음(기본값 그대로).

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			if (outlineBlocks.get()) {   // 49-88차(8-12): 블록 테두리를 껐으면 미리보기도 빈다
				drawPreview(context);
			}
			return;
		}
		// 화면(인벤토리/HUD 편집기 등)이 열려 있으면 그리지 않음 - 편집기가 모듈 HUD를 직접 호출해서 필요.
		if (client.currentScreen != null) {
			return;
		}
		updateEntityGlow();
		if (!seeThrough.get()) {
			return;
		}
		List<Box> boxes = targetBoxes();
		if (boxes.isEmpty()) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		drawBoxes(context, proj, boxes, outlineColor.getArgb(), lineWidth.get());
	}

	private static final double[][] VIEW = new double[8][3];

	private void drawBoxes(DrawContext context, LunaProjection proj, List<Box> boxes, int argb, float width) {
		double[][] view = VIEW;
		for (Box box : boxes) {
			double[] bb = LunaCompat.boxBounds(box); // 49-36차: 1.15.2는 필드가 x1..z2라 리플렉션
			for (int i = 0; i < 8; i++) {
				// 꼭짓점 번호 비트: bit0 = x(최소/최대), bit1 = z, bit2 = y
				double x = (i & 1) == 0 ? bb[0] : bb[3];
				double z = (i & 2) == 0 ? bb[2] : bb[5];
				double y = (i & 4) == 0 ? bb[1] : bb[4];
				proj.toView(x, y, z, view[i]);
			}
			for (int[] e : EDGES) {
				proj.drawViewSegment(context, view[e[0]], view[e[1]], width, argb);
			}
		}
	}

	/** 설정 화면 미리보기: 칸 가운데에 회전한 정육면체 테두리(실제 선 두께/색/AA 그대로). */
	private void drawPreview(DrawContext context) {
		double cx = previewCenterX(), cy = previewCenterY();
		double s = Math.min(previewW(), previewH()) * 0.22;
		double yaw = Math.toRadians(35), pitch = Math.toRadians(-22);
		double[][] pts = new double[8][2];
		double[] depth = new double[8];
		for (int i = 0; i < 8; i++) {
			double x = ((i & 1) == 0 ? -1 : 1) * s;
			double y = (i < 4 ? -1 : 1) * s;
			double z = ((i & 2) == 0 ? -1 : 1) * s;
			// yaw 회전(y축) 후 pitch 회전(x축), 정사영
			double x1 = x * Math.cos(yaw) - z * Math.sin(yaw);
			double z1 = x * Math.sin(yaw) + z * Math.cos(yaw);
			double y2 = y * Math.cos(pitch) - z1 * Math.sin(pitch);
			depth[i] = y * Math.sin(pitch) + z1 * Math.cos(pitch);
			pts[i][0] = cx + x1;
			pts[i][1] = cy - y2;
		}
		// 49-88차(8-12): "가려진 선"을 끄면 실제로는 뒤쪽 모서리가 안 보인다 - 가장 먼 꼭짓점에 붙은 세 변을 뺀다
		int hidden = -1;
		if (!seeThrough.get()) {
			for (int i = 0; i < 8; i++) {
				if (hidden < 0 || depth[i] > depth[hidden]) {
					hidden = i;
				}
			}
		}
		int argb = outlineColor.getArgb();
		float w = lineWidth.get();
		for (int[] e : EDGES) {
			if (e[0] == hidden || e[1] == hidden) {
				continue;
			}
			LunaProjection.lineAA(context, pts[e[0]][0], pts[e[0]][1], pts[e[1]][0], pts[e[1]][1], w, argb,
					previewX() + previewW() + 400, previewY() + previewH() + 400);
		}
	}

	private static final int[][] EDGES = {
		{0, 1}, {1, 3}, {3, 2}, {2, 0},   // 아랫면(비트: x=bit0, z=bit1)
		{4, 5}, {5, 7}, {7, 6}, {6, 4},   // 윗면
		{0, 4}, {1, 5}, {2, 6}, {3, 7},   // 기둥
	};
}
