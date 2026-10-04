package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.util.EntityPos;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-15차 전면 재작성. 사용자 지적: "두께가 2픽셀이라 한쪽으로 두껍고 위치도 안 맞음".
 *
 * 원인 두 가지:
 *  1) 두께가 2px로 박혀 있었음. 마크 화면의 "중심 픽셀"은 [w/2, w/2+1) × [h/2, h/2+1)이라
 *     (바닐라가 15×15 크로스헤어를 w/2-7에 그리는 데서 확인) 실제 중심 좌표는 w/2+0.5.
 *     예전 코드는 cx-1..cx+1을 채워 중심선이 cx가 됐고 → 중심 픽셀 기준 왼쪽(위쪽) 1.5px,
 *     오른쪽(아래쪽) 0.5px = "한쪽만 두꺼움". 이제 홀수 두께는 중심 픽셀에 정확히 대칭
 *     (두께 1 = 딱 중심 픽셀 하나), 짝수 두께는 가로·세로가 같은 방향으로만 치우쳐 모양은 대칭.
 *  2) 바닐라 크로스헤어가 그대로 남아 우리 것과 겹쳐 보였음 → CrosshairHideMixin이
 *     바닐라 렌더를 취소(옵션, 기본 켜짐).
 *
 * 추가: 팔 길이/두께/중앙 간격/가운데 점/검은 테두리를 각각 조절. 테두리는 밝은 배경(눈·모래)
 * 위에서 흰 크로스헤어가 사라지는 걸 막아줌.
 */
public class CustomCrosshairModule extends Module {

	public enum CrosshairPreset {
		CROSS("십자"),
		DOT("점"),
		CIRCLE("원"),
		T_SHAPE("T자"),
		X_SHAPE("X자"),
		CROSS_CIRCLE("십자+원");

		private final String label;

		CrosshairPreset(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<CrosshairPreset> preset = register(new EnumSetting<>(
			"preset", "모양", "크로스헤어 모양입니다.", CrosshairPreset.CROSS, CrosshairPreset.class));

	private final ColorSetting color = register(new ColorSetting(
			"color", "색", "크로스헤어 색입니다.", 0xFFFFFFFF));

	private final IntSetting length = register(new IntSetting(
			"size", "팔 길이", "가운데에서 뻗는 선의 길이입니다. 원 모양에서는 반지름입니다.", 4, 1, 20, 1));

	private final IntSetting thickness = register(new IntSetting(
			"thickness", "두께", "선 두께(픽셀)입니다. 홀수가 정중앙에 맞습니다.", 1, 1, 8, 1).unit("px"));

	private final IntSetting gap = register(new IntSetting(
			"gap", "간격", "가운데를 비우는 간격입니다. 0이면 붙은 십자입니다.", 0, 0, 8, 1));

	private final BooleanSetting centerDot = register(new BooleanSetting(
			"center_dot", "가운데 점", "가운데에 점을 찍습니다.", false));

	// 49-22차: 이름 '검은 테두리' → '윤곽선', 스타일 그룹으로(.style()).
	private final BooleanSetting outline = register(new BooleanSetting(
			"outline", "윤곽선", "밝은 배경에서도 잘 보이도록 검은 윤곽선을 두릅니다.", true).style());

	// 49-32차: 꾸밀 거리 추가
	private final BooleanSetting toolCheck = register(new BooleanSetting(
			"tool_check", "도구 표시", "바라보는 블록에 맞는 도구를 들고 있으면 조준선 옆에 표시합니다.", true));
	private final ColorSetting toolColor = register(new ColorSetting(
			"tool_color", "도구 표시 색", "맞는 도구일 때 표시 색입니다.", 0xFF8BE07A));
	private final ColorSetting targetColor = register(new ColorSetting(
			"target_color", "조준 시 색", "몹을 조준하고 있을 때 쓰는 색입니다.", 0xFFE86A5E));
	private final BooleanSetting colorOnTarget = register(new BooleanSetting(
			"color_on_target", "몹 조준 색 변경", "몹을 조준하면 조준선 색이 바뀝니다.", true));

	private final BooleanSetting blockColorOn = register(new BooleanSetting(
			"color_on_block", "블록 조준 색 변경", "캘 수 있는 블록을 조준하면 조준선 색이 바뀝니다.", false));
	private final ColorSetting blockColor = register(new ColorSetting(
			"block_color", "블록 조준 색", "블록을 조준하고 있을 때 쓰는 색입니다.", 0xFFA9D973));

	// ---- 49-70차(4-29 · 4-7): 상태에 따라 벌어지는 조준선 ----
	private final BooleanSetting cooldownSpread = register(new BooleanSetting(
			"cooldown_spread", "공격 쿨다운 반응",
			"때린 뒤 공격력이 다시 찰 때까지 조준선이 벌어졌다가 모입니다. 다 차면 딱 붙습니다.", false));
	private final BooleanSetting moveSpread = register(new BooleanSetting(
			"move_spread", "이동 시 벌어짐", "걷거나 달리면 조준선이 살짝 벌어집니다.", false));
	private final IntSetting spreadAmount = register(new IntSetting(
			"spread_amount", "벌어짐 크기", "가장 많이 벌어졌을 때의 간격(픽셀)입니다.", 4, 1, 16, 1).unit("px"));
	private final IntSetting opacity = register(new IntSetting(
			"opacity", "투명도", "조준선의 불투명도입니다(100이면 그대로).", 100, 10, 100, 5).style());

	private final BooleanSetting hideVanilla = register(new BooleanSetting(
			"hide_vanilla", "기본 숨김", "마인크래프트 기본 크로스헤어를 숨깁니다.", true));

	/**
	 * 지금 벌어진 정도(픽셀). 목표값으로 <b>프레임 시간에 비례해</b> 따라간다 - 프레임률이 달라도
	 * 같은 속도로 모인다. 모이는 쪽이 벌어지는 쪽보다 느리다(총 게임의 반동 회복과 같은 느낌).
	 */
	private float spread;
	/** 프레임 간격을 직접 잰다 - HUD 렌더 경로에서는 LunaDraw.beginFrame()이 안 불려 dt()가 멈춰 있다. */
	private long lastSpreadNanos;

	public CustomCrosshairModule() {
		super("custom_crosshair", "크로스헤어", ModuleCategory.VIEW, "모양 | 색 | 두께");
	}

	/** 기본 프리셋(CROSS)으로 되돌립니다. */
	public void resetPreset() {
		preset.setValue(CrosshairPreset.CROSS);
	}

	/** CrosshairHideMixin이 바닐라 크로스헤어를 취소할지 판단할 때 사용. */
	public boolean shouldHideVanilla() {
		return isEnabled() && isVersionSupported() && hideVanilla.get();
	}

	/**
	 * 두께 t짜리 띠의 시작 좌표. 중심 픽셀이 [c, c+1)이므로 홀수 두께는 그 픽셀을 중심으로
	 * 정확히 대칭이 되고(t=1 → 중심 픽셀 하나), 짝수 두께는 가로·세로 모두 같은 방향으로만
	 * 0.5px 치우쳐 모양 자체의 대칭은 유지된다.
	 */
	private static int bandStart(int center, int t) {
		return center - (t - 1) / 2;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		int cx, cy;
		if (isPreview()) {
			// 49-22차: 미리보기 칸 가운데에 실제 모양 그대로
			cx = previewCenterX();
			cy = previewCenterY();
		} else {
			// 49-8차: F1(HUD 숨김) 상태에선 그리지 않음
			if (kr.lunaslight.mod.util.LunaCompat.isHudHidden(client)) {
				return;
			}
			// 49-34차: 바닐라 십자와 같은 픽셀(짝수 폭에서 w/2는 1px 어긋남 - LunaCompat 설명 참고)
			cx = kr.lunaslight.mod.util.LunaCompat.crosshairCenterX(client);
			cy = kr.lunaslight.mod.util.LunaCompat.crosshairCenterY(client);
		}
		int t = Math.max(1, thickness.get());
		int len = Math.max(1, length.get());
		int g = Math.max(0, gap.get()) + spreadNow();

		// 중심 띠(가로 선의 세로 범위 = 세로 선의 가로 범위)
		int bxLo = bandStart(cx, t);
		int bxHi = bxLo + t;
		int byLo = bandStart(cy, t);
		int byHi = byLo + t;

		List<int[]> rects = new ArrayList<>();
		switch (preset.get()) {
			case DOT -> rects.add(new int[]{bxLo, byLo, bxHi, byHi});
			case CROSS -> {
				addArms(rects, bxLo, bxHi, byLo, byHi, len, g, true);
				if (g == 0 || centerDot.get()) {
					rects.add(new int[]{bxLo, byLo, bxHi, byHi});
				}
			}
			case T_SHAPE -> {
				addArms(rects, bxLo, bxHi, byLo, byHi, len, g, false);
				if (g == 0 || centerDot.get()) {
					rects.add(new int[]{bxLo, byLo, bxHi, byHi});
				}
			}
			case CIRCLE -> {
				addRing(rects, cx, cy, len, t);
				if (centerDot.get()) {
					rects.add(new int[]{bxLo, byLo, bxHi, byHi});
				}
			}
			case X_SHAPE -> {
				addDiagonals(rects, cx, cy, len, t, g);
				if (centerDot.get()) {
					rects.add(new int[]{bxLo, byLo, bxHi, byHi});
				}
			}
			case CROSS_CIRCLE -> {
				addArms(rects, bxLo, bxHi, byLo, byHi, len, g, true);
				// 원은 팔 끝보다 조금 바깥에 - 팔과 겹치면 두 배로 두꺼워 보인다
				addRing(rects, cx, cy, len + g + 3, t);
				if (centerDot.get()) {
					rects.add(new int[]{bxLo, byLo, bxHi, byHi});
				}
			}
		}

		// 검은 테두리 먼저(각 사각형을 1px 키워서) → 그 위에 색을 올림
		if (outline.get()) {
			for (int[] r : rects) {
				context.fill(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, 0xA0000000);
			}
		}
		// 49-32차: 몹을 조준하고 있으면 색을 바꿔 준다(설정).
		// 49-70차(4-29): 블록 조준 색도 따로. 몹이 블록보다 세다 - 싸우는 중에는 그게 먼저다.
		int argb = color.getArgb();
		if (!isPreview()) {
			if (colorOnTarget.get() && client.targetedEntity != null) {
				argb = targetColor.getArgb();
			} else if (blockColorOn.get() && lookingAtBlock()) {
				argb = blockColor.getArgb();
			}
		}
		argb = withOpacity(argb);
		for (int[] r : rects) {
			context.fill(r[0], r[1], r[2], r[3], argb);
		}

		// 49-32차(사용자: "올바른 도구를 들면 체크 표시가 뜬다거나"):
		// 바라보는 블록에 맞는 도구를 들고 있으면 조준선 오른쪽 위에 작은 체크.
		if (toolCheck.get() && (isPreview() || suitableTool())) {
			int tc = toolColor.getArgb();
			int mx = cx + len + gap.get() + 5;
			int my = cy - len - gap.get() - 1;
			context.fill(mx, my + 3, mx + 1, my + 5, tc);
			context.fill(mx + 1, my + 4, mx + 2, my + 6, tc);
			context.fill(mx + 2, my + 3, mx + 3, my + 5, tc);
			context.fill(mx + 3, my + 1, mx + 4, my + 3, tc);
			context.fill(mx + 4, my, mx + 5, my + 2, tc);
		}
	}

	/**
	 * 49-70차(4-29): 지금 벌어져 있어야 할 픽셀 수.
	 *
	 * <p><b>공격 쿨다운</b>: 때린 직후엔 공격력이 0에 가깝고 시간이 지나면 1로 찬다
	 * ({@code getAttackCooldownProgress} - 1.15.2부터 지금까지 같은 이름, javap 실측).
	 * 그 반대만큼 벌린다 = <b>다 차면 딱 붙는다</b>. 바닐라 공격 표시기(칼 아이콘)와 같은 정보인데
	 * 눈이 이미 가 있는 자리에서 읽힌다는 게 다르다.
	 *
	 * <p><b>움직임</b>: 달리면 1, 걸으면 0.55, 서 있으면 0. 웅크리면 0(정조준하는 자세니까).
	 *
	 * <p>둘 다 켜져 있으면 <b>큰 쪽</b>을 쓴다(더하면 금방 화면 밖으로 벌어진다).
	 * 값이 튀지 않게 프레임 시간 기준으로 따라가고, <b>모이는 쪽을 조금 느리게</b> 해서
	 * 딱 붙는 순간이 눈에 들어오게 했다.
	 */
	private int spreadNow() {
		if (isPreview()) {
			return 0;
		}
		float target = 0f;
		try {
			if (cooldownSpread.get() && client.player != null) {
				target = Math.max(target, 1f - Math.max(0f, Math.min(1f, client.player.getAttackCooldownProgress(0f))));
			}
			if (moveSpread.get() && client.player != null) {
				float move = 0f;
				if (!client.player.isSneaking()) {
					if (client.player.isSprinting()) {
						move = 1f;
					} else if (isWalking()) {
						move = 0.55f;
					}
				}
				target = Math.max(target, move);
			}
		} catch (Throwable ignored) {
			return 0;
		}
		target *= Math.max(1, spreadAmount.get());
		long now = System.nanoTime();
		float dt = lastSpreadNanos == 0 ? 0.016f
				: Math.max(0.001f, Math.min(0.1f, (now - lastSpreadNanos) / 1_000_000_000f));
		lastSpreadNanos = now;
		float speed = target > spread ? 26f : 14f;    // 벌어질 땐 빠르게, 모일 땐 천천히
		spread += (target - spread) * Math.min(1f, dt * speed);
		return Math.round(spread);
	}

	private double lastX, lastZ;
	private boolean haveLast;

	/**
	 * 수평으로 실제로 움직이고 있는지(제자리 점프·낙하는 제외).
	 *
	 * <p>바닐라의 {@code prevX}/{@code prevZ}를 안 쓴다 - <b>1.21.8에서 그 필드 이름이 바뀌어</b>
	 * 컴파일이 깨졌다(실측). 자리를 <b>내가 기억해 두고</b> 비교하면 어느 버전에서도 같은 값이 나온다.
	 */
	private boolean isWalking() {
		try {
			double x = EntityPos.x(client.player);
			double z = EntityPos.z(client.player);
			boolean moving = haveLast
					&& (x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ) > 1.0E-5;
			lastX = x;
			lastZ = z;
			haveLast = true;
			return moving;
		} catch (Throwable ignored) {
			haveLast = false;
			return false;
		}
	}

	/** 지금 블록을 조준하고 있는지(허공·엔티티면 false). */
	private boolean lookingAtBlock() {
		try {
			return client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult
					&& client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 설정 투명도를 곱한 색. 100이면 손대지 않는다. */
	private int withOpacity(int argb) {
		int pct = opacity.get();
		if (pct >= 100) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * (pct / 100f));
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
	}

	/**
	 * X자 - 대각선 네 개. 픽셀 격자에 대각선을 그리면 계단이 생기므로 <b>한 칸씩 짧은 사각형</b>을
	 * 이어 붙인다(두께 t면 t×t 정사각형). 십자와 달리 중앙이 비어 있어 블록 모서리를 겨눌 때 잘 보인다.
	 */
	private void addDiagonals(List<int[]> rects, int cx, int cy, int len, int t, int g) {
		int start = (t + 1) / 2 + g;
		for (int i = start; i < start + len; i++) {
			int o = i - (t - 1) / 2;
			rects.add(new int[]{cx + o, cy + o, cx + o + t, cy + o + t});
			rects.add(new int[]{cx - o, cy + o, cx - o + t, cy + o + t});
			rects.add(new int[]{cx + o, cy - o, cx + o + t, cy - o + t});
			rects.add(new int[]{cx - o, cy - o, cx - o + t, cy - o + t});
		}
	}

	/** 지금 바라보는 블록에 맞는 도구를 들고 있는지. */
	private boolean suitableTool() {
		try {
			if (client.player == null || client.world == null || client.crosshairTarget == null) {
				return false;
			}
			if (!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)) {
				return false;
			}
			Object state = client.world.getBlockState(hit.getBlockPos());
			Boolean ok = LunaCompat.isSuitableTool(client.player.getMainHandStack(), state);
			return Boolean.TRUE.equals(ok);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 좌/우/아래(+withTop이면 위) 팔. 간격은 중심 띠 바깥 가장자리에서 재므로 항상 대칭. */
	private void addArms(List<int[]> rects, int bxLo, int bxHi, int byLo, int byHi,
			int len, int g, boolean withTop) {
		rects.add(new int[]{bxHi + g, byLo, bxHi + g + len, byHi});           // 오른쪽
		rects.add(new int[]{bxLo - g - len, byLo, bxLo - g, byHi});           // 왼쪽
		rects.add(new int[]{bxLo, byHi + g, bxHi, byHi + g + len});           // 아래
		if (withTop) {
			rects.add(new int[]{bxLo, byLo - g - len, bxHi, byLo - g});       // 위
		}
	}

	/**
	 * 반지름 r, 두께 t짜리 링. 예전엔 24개 사각형을 각도로 찍어서 굵기가 들쭉날쭉했음 -
	 * 이제 중심(c+0.5)에서의 거리로 픽셀을 판정해 두께가 고르다. 가로 런 단위로 묶어 fill 호출도 적다.
	 */
	private void addRing(List<int[]> rects, int cx, int cy, int r, int t) {
		double centerX = cx + 0.5;
		double centerY = cy + 0.5;
		// 49-32차(사용자: "원이 너무 두꺼워"): 예전 식은 띠 폭이 t+1픽셀이 되어 두께 1로 두어도
		// 2픽셀로 그려졌다. 이제 딱 t픽셀만.
		double inner = r - t / 2.0;
		double outer = r + t / 2.0;
		int span = r + t + 1;
		for (int y = cy - span; y <= cy + span; y++) {
			int runStart = Integer.MIN_VALUE;
			for (int x = cx - span; x <= cx + span + 1; x++) {
				double dx = x + 0.5 - centerX;
				double dy = y + 0.5 - centerY;
				double d = Math.sqrt(dx * dx + dy * dy);
				boolean on = x <= cx + span && d >= inner && d <= outer;
				if (on && runStart == Integer.MIN_VALUE) {
					runStart = x;
				} else if (!on && runStart != Integer.MIN_VALUE) {
					rects.add(new int[]{runStart, y, x, y + 1});
					runStart = Integer.MIN_VALUE;
				}
			}
		}
	}
}
