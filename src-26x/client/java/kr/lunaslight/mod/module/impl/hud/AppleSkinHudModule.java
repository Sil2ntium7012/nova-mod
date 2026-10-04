package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/**
 * 배고픔 바 위에 포만감(saturation)을 겹쳐 보여주는 AppleSkin 스타일 HUD.
 *
 * 49-23차 재작업(사용자: "포만감도 배고픔에 테두리로 얼마나 차는지 알려주라, 내가 얼마나 포만감이 있는지도,
 * 체력 닳은 거+포만감 계산해서 먹으면 체력 얼마나 차는지도 체력 깜빡이로"):
 *  ① 포만감 = 고기 아이콘의 **검은 윤곽을 금색 테두리로** 덮는다(진짜 AppleSkin 방식). 윤곽 스프라이트
 *     (textures/gui/food_outline.png 9×9 = 바닐라 food_empty 실루엣 − food_full 픽셀)를 바닐라 아이콘 위에
 *     그리므로 모양이 정확히 겹친다. 칸당 2포인트, 오른쪽부터 차오르며(바닐라 반 칸과 같은 방향) 소수는
 *     열 단위로 잘라 부드럽게.
 *  ② 손에 음식을 들면 채워질 배고픔 칸이 깜빡이고(흰 고기), 늘어날 포만감 테두리가 밝게 깜빡이며,
 *     **회복될 체력**을 바닐라 HungerManager.update() 시뮬레이션(포만감 → 빠른 회복 6/10틱, 배고픔 18+ →
 *     80틱당 1)으로 계산해 하트 위에 깜빡이는 하트로 보여준다.
 *  49-25차: 숫자 표시(포만감 수치·+배고픔/+포만감·+회복 체력·수치 텍스트 줄)는 전부 삭제 - "숫자로 뜨는 것들 다 없애줘".
 *
 * 바닐라 배고픔 바 좌표(우측 정렬, x = w/2+91-i*8-9, y = h-39)와 하트 좌표(x = w/2-91+i*8, 10개마다 한 줄
 * 위로)는 1.15~1.21.11 동일 공식.
 */
public class AppleSkinHudModule extends Module {

	private final BooleanSetting overlay = register(new BooleanSetting(
			"saturation_overlay", "포만감 테두리", "고기 아이콘 윤곽을 포만감만큼 금색으로 채웁니다.", true));

	private final ColorSetting overlayColor = register(new ColorSetting(
			"overlay_color", "포만감 색", "포만감 테두리의 색입니다.", 0xFFFFD24A));

	private final BooleanSetting foodPreview = register(new BooleanSetting(
			"food_preview", "음식 미리보기", "손에 든 음식이 채워 줄 배고픔과 포만감을 깜빡여 보여줍니다.", true));

	private final ColorSetting previewColor = register(new ColorSetting(
			"preview_color", "미리보기 색", "채워질 배고픔 칸의 색입니다.", 0xFFFFFFFF));

	// 49-195차(사용자: "포만감에 포만감 미리보기도 색 바꿀 수 있게"): 늘어날 포만감 테두리(예전엔 밝은 금색 고정)를 켜고 끄고 색도 고른다.
	private final BooleanSetting satPreview = register(new BooleanSetting(
			"sat_preview", "포만감 미리보기", "손에 든 음식이 늘려 줄 포만감 테두리를 깜빡여 보여줍니다.", true));

	private final ColorSetting satPreviewColor = register(new ColorSetting(
			"sat_preview_color", "포만감 미리보기 색", "늘어날 포만감 테두리의 색입니다.", 0xFFFFF4C0));

	private final BooleanSetting healthPreview = register(new BooleanSetting(
			"health_preview", "체력 회복 미리보기", "먹으면 회복될 체력을 하트 위에 깜빡여 보여줍니다.", true));

	private final ColorSetting healthColor = register(new ColorSetting(
			"health_color", "회복 하트 색", "깜빡이는 하트의 색입니다.", 0xFFFF5C5C));

	private static final boolean SPRITE_ERA = LunaVersion.isWithin("1.20.2", null);
	private static Identifier heartFull, heartHalf, iconsPng, outlineTex;

	public AppleSkinHudModule() {
		super("apple_skin_hud", "포만감", ModuleCategory.HUD, "포만감 테두리와 음식 | 체력 회복 미리보기");
		defaultEnabled(true);   // 49-121차(사용자): 기본 활성화
		// 49-179차(사용자: "변경 불가능한 거는 배경 모양 설정이 따로 있으면 안되지"): 상자를 안 그리므로 배경 설정 없음
		// 49-23차: 색 설정을 스위치 옆 견본으로(1줄 통합)
		overlay.withColor(overlayColor);
		foodPreview.withColor(previewColor);
		satPreview.withColor(satPreviewColor);
		healthPreview.withColor(healthColor);
	}

	/** 0~1을 오가는 부드러운 깜빡임 값. */
	private static float pulse() {
		double t = (System.currentTimeMillis() % 1400L) / 1400.0 * Math.PI * 2;
		return (float) (0.5 + 0.5 * Math.sin(t));
	}

	private static int withAlpha(int argb, int alpha) {
		return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
	}

	/** 배고픔 아이콘 i(0 = 가장 오른쪽)의 좌상단 x. */
	private static int iconX(int rightEdge, int i) {
		return rightEdge - i * 8 - 9;
	}

	/** 바닐라 고기 아이콘(full/half/empty)을 색 곱해서 그림(LunaGfx 공용). 실패하면 false. */
	private static boolean drawFood(GuiGraphicsExtractor ctx, int x, int y, int kind, int argb) {
		return LunaGfx.drawFoodIcon(ctx, x, y, kind, argb);
	}

	/** 바닐라 하트(full/half)를 색 곱해서 그림. 실패하면 false. */
	private static boolean drawHeart(GuiGraphicsExtractor ctx, int x, int y, int kind, int argb) {
		if (SPRITE_ERA) {
			if (heartFull == null) {
				heartFull = LunaGfx.mcId("textures/gui/sprites/hud/heart/full.png");
				heartHalf = LunaGfx.mcId("textures/gui/sprites/hud/heart/half.png");
			}
			return LunaGfx.drawTex(ctx, kind == 2 ? heartFull : heartHalf, x, y, 9, 9, 0, 0, 9, 9, 9, argb);
		}
		if (iconsPng == null) {
			iconsPng = LunaGfx.mcId("textures/gui/icons.png");
		}
		return LunaGfx.drawTex(ctx, iconsPng, x, y, 9, 9, kind == 2 ? 52 : 61, 0, 9, 9, 256, argb);
	}

	/** 고기 윤곽 스프라이트의 오른쪽 cols열(1~9)을 (x,y)에 색 곱해서 그림 - 포만감 테두리(LunaGfx 공용). */
	private static void drawOutline(GuiGraphicsExtractor ctx, int x, int y, int cols, int argb) {
		LunaGfx.drawFoodOutline(ctx, x, y, cols, argb);
	}

	/** 손에 든(주손 우선, 없으면 보조손) 음식의 [배고픔, 포만감] - 음식이 없으면 null. */
	private float[] heldFood() {
		ItemStack main = client.player.getMainHandItem();
		float[] v = LunaCompat.foodValues(main);
		if (v != null) {
			return v;
		}
		return LunaCompat.foodValues(client.player.getOffhandItem());
	}

	// ---------------------------------------------------------------- 체력 회복 계산

	private static Method getExhaustion;
	private static boolean exhaustionResolved;

	private float exhaustion() {
		try {
			Object hm = client.player.getFoodData();
			if (!exhaustionResolved) {
				exhaustionResolved = true;
				getExhaustion = LunaCompat.findNoArgMethod(hm.getClass(), "getExhaustion");
			}
			if (getExhaustion != null) {
				Object v = getExhaustion.invoke(hm);
				if (v instanceof Number n) {
					return n.floatValue();
				}
			}
		} catch (Throwable ignored) {
		}
		return 0f;
	}

	private static Object naturalRegenKey;
	private static boolean naturalRegenResolved;

	/**
	 * 자연 회복 게임 규칙(naturalRegeneration). 못 읽으면 true. World#getGameRules()가 1.21.11에서 사라지고
	 * (클라이언트가 규칙을 안 가짐) GameRules 클래스/필드 이름도 옮겨져서(world.rule.GameRules,
	 * NATURAL_HEALTH_REGENERATION, getValue) 전부 리플렉션.
	 */
	private boolean naturalRegeneration() {
		try {
			if (client.level == null) {
				return true;
			}
			Object rules = LunaCompat.invokeNoArg(client.level, "getGameRules");
			if (rules == null) {
				return true;
			}
			if (!naturalRegenResolved) {
				naturalRegenResolved = true;
				for (String cn : new String[]{"net.minecraft.world.GameRules", "net.minecraft.world.level.gamerules.GameRules"}) {
					Class<?> gr = LunaCompat.classOrNull(cn);
					if (gr == null) {
						continue;
					}
					for (String fn : new String[]{"NATURAL_REGENERATION", "NATURAL_HEALTH_REGENERATION"}) {
						java.lang.reflect.Field f = LunaCompat.findField(gr, fn);
						if (f != null) {
							naturalRegenKey = f.get(null);
							break;
						}
					}
					if (naturalRegenKey != null) {
						break;
					}
				}
			}
			if (naturalRegenKey == null) {
				return true;
			}
			for (String name : new String[]{"getBoolean", "getValue"}) {
				for (Method m : rules.getClass().getMethods()) {
					if (m.getParameterCount() == 1 && LunaCompat.nameMatches(rules.getClass(), name, m.getName())
							&& m.getParameterTypes()[0].isInstance(naturalRegenKey)) {
						Object v = m.invoke(rules, naturalRegenKey);
						if (v instanceof Boolean b) {
							return b;
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return true;
	}

	/**
	 * 배고픔 food/포만감 sat/피로 exh 상태에서 자연 회복으로 채워질 체력(최대 missing) - 바닐라
	 * HungerManager.update() 그대로: 배고픔 20 & 포만감>0 → 10틱마다 min(포만감,6)/6 회복하며 그만큼 피로,
	 * 배고픔 18+ → 80틱마다 1 회복하며 피로 6. 피로 4마다 포만감 1(없으면 배고픔 1) 소모.
	 */
	static float estimateHeal(int food, float sat, float exh, float missing) {
		float healed = 0f;
		int guard = 0;
		while (healed < missing && guard++ < 400) {
			if (food >= 20 && sat > 0f) {
				float f = Math.min(sat, 6f);
				healed += f / 6f;
				exh += f;
			} else if (food >= 18) {
				healed += 1f;
				exh += 6f;
			} else {
				break;
			}
			while (exh > 4f) {
				exh -= 4f;
				if (sat > 0f) {
					sat = Math.max(sat - 1f, 0f);
				} else {
					food = Math.max(food - 1, 0);
				}
			}
		}
		return Math.min(healed, missing);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreviewBoxed()) {
			renderPreviewBar(context);
			return;
		}
		if (client.player == null) {
			return;
		}
		int foodLevel = client.player.getFoodData().getFoodLevel();
		float saturation = client.player.getFoodData().getSaturationLevel();

		boolean barVisible = !client.player.isPassenger() && client.gameMode != null
				&& LunaCompat.shouldShowSurvivalHud(client);
		float[] food = (foodPreview.get() || satPreview.get() || healthPreview.get()) && barVisible ? heldFood() : null;

		// 먹었을 때의 결과값(바닐라 규칙: 포만감은 배고픔 수치를 넘지 못함)
		int newFood = foodLevel;
		float newSat = saturation;
		if (food != null) {
			newFood = Math.min(20, foodLevel + Math.round(food[0]));
			newSat = Math.min(newFood, saturation + food[1]);
		}

		if (barVisible) {
			int sw = client.getWindow().getGuiScaledWidth();
			int sh = client.getWindow().getGuiScaledHeight();
			int baseY = sh - 39;
			drawOverlays(context, sw / 2 + 91, baseY, foodLevel, saturation, newFood, newSat, food);

			// ---- 체력 회복 미리보기(하트 깜빡임 + 숫자) ----
			if (food != null && healthPreview.get()) {
				float health = client.player.getHealth();
				float maxHealth = LunaCompat.getMaxHealth(client.player); // 49-36차: 1.15.2 getMaximumHealth
				float missing = maxHealth - health;
				if (health > 0f && missing > 0.01f && naturalRegeneration()) {
					float heal = estimateHeal(newFood, newSat, exhaustion(), missing);
					if (heal > 0.05f) {
						float absorption = 0f;
						try {
							absorption = client.player.getAbsorptionAmount();
						} catch (Throwable ignored) {
						}
						drawHealthPreview(context, sw / 2 - 91, baseY, health, maxHealth, absorption, heal);
					}
				}
			}
		}
	}

	/**
	 * 배고픔 바(오른쪽 끝 right, 윗변 baseY) 위에 포만감 테두리/미리보기 오버레이.
	 * 아이콘 i는 배고픔 포인트 [2i, 2i+2)를 담당(i=0이 가장 오른쪽).
	 */
	private void drawOverlays(GuiGraphicsExtractor context, int right, int baseY, int foodLevel, float saturation,
			int newFood, float newSat, float[] food) {
		// ---- ① 손에 든 음식이 채워줄 배고픔 칸: 아이콘 위에 은은하게 깜빡이는 흰 고기 ----
		if (food != null && foodPreview.get() && newFood > foodLevel) {
			int a = Math.round(50 + 90 * pulse());
			int col = withAlpha(previewColor.getArgb(), a);
			for (int i = 0; i < 10; i++) {
				int lo = i * 2, hi = lo + 2;
				if (newFood <= lo || foodLevel >= hi) {
					continue;
				}
				int kind = newFood >= hi ? 2 : 1;
				int ix = iconX(right, i);
				if (!drawFood(context, ix, baseY, kind, col)) {
					context.fill(ix + 1, baseY + 1, ix + (kind == 2 ? 8 : 5), baseY + 8, col);
				}
			}
		}

		// ---- ② 포만감: 고기 윤곽을 금색 테두리로(AppleSkin 방식). 오른쪽부터 차오름 ----
		float sat = Math.min(20f, Math.max(0f, saturation));
		if (overlay.get()) {
			int gold = overlayColor.getArgb();
			for (int i = 0; i < 10; i++) {
				float covered = sat - i * 2f; // 이 칸에서 덮인 포인트(0~2)
				if (covered <= 0.01f) {
					continue;
				}
				int cols = covered >= 2f ? 9 : Math.max(1, Math.round(9f * covered / 2f));
				drawOutline(context, iconX(right, i), baseY, cols, gold);
			}
			// 미리보기: 늘어날 포만감 테두리를 밝은 금색으로 깜빡이며
			if (food != null && satPreview.get() && newSat > sat + 0.01f) {
				int a = Math.round(70 + 150 * pulse());
				int col = withAlpha(satPreviewColor.getArgb(), a);
				float ns = Math.min(20f, newSat);
				for (int i = 0; i < 10; i++) {
					float lo = i * 2f;
					float coveredNew = ns - lo;
					float coveredOld = sat - lo;
					if (coveredNew <= 0.01f || coveredOld >= 2f) {
						continue;
					}
					int colsNew = coveredNew >= 2f ? 9 : Math.max(1, Math.round(9f * coveredNew / 2f));
					int colsOld = coveredOld <= 0f ? 0 : Math.max(1, Math.round(9f * coveredOld / 2f));
					if (colsNew <= colsOld) {
						continue;
					}
					// 이미 찬 부분(오른쪽 colsOld열)은 빼고 새로 늘어나는 열만
					int ix = iconX(right, i);
					if (outlineTex == null) {
						outlineTex = LunaGfx.id("textures/gui/food_outline.png");
					}
					int u = 9 - colsNew;
					int w = colsNew - colsOld;
					if (!LunaGfx.drawTex(context, outlineTex, ix + u, baseY, w, 9, u, 0, w, 9, 9, col)) {
						context.fill(ix + u, baseY + 8, ix + u + w, baseY + 9, col);
					}
				}
			}
		}
	}

	/**
	 * 하트 바(왼쪽 끝 left, 첫 줄 윗변 baseY) 위에 회복될 체력을 깜빡이는 하트로.
	 * 하트 i = 체력 [2i, 2i+2). 바닐라와 같이 10개마다 한 줄 위(rowHeight = max(10 - (줄 수 - 2), 3)).
	 */
	private void drawHealthPreview(GuiGraphicsExtractor context, int left, int baseY, float health, float maxHealth,
			float absorption, float heal) {
		int cur = (int) Math.ceil(health);
		int target = (int) Math.ceil(Math.min(maxHealth, health + heal));
		if (target <= cur) {
			return;
		}
		int rows = (int) Math.ceil((maxHealth + absorption) / 2f / 10f);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		int a = Math.round(70 + 150 * pulse());
		int col = withAlpha(healthColor.getArgb(), a);
		int hearts = (int) Math.ceil(maxHealth / 2f);
		for (int i = 0; i < hearts; i++) {
			int lo = i * 2, hi = lo + 2;
			if (target <= lo || cur >= hi) {
				continue;
			}
			int kind = target >= hi ? 2 : 1;
			int hx = left + (i % 10) * 8;
			int hy = baseY - (i / 10) * rowHeight;
			if (!drawHeart(context, hx, hy, kind, col)) {
				context.fill(hx + 1, hy + 1, hx + (kind == 2 ? 8 : 5), hy + 8, col);
			}
		}
	}

	/**
	 * 설정 화면 미리보기: 가짜 하트/배고픔 바(체력 11, 배고픔 14, 포만감 5.5, 손에 스테이크 +8/+12.8).
	 * 미리보기 칸(170px)엔 실제처럼 한 줄로 못 놓으므로 하트 줄을 위, 배고픔 줄을 아래에 1:1 픽셀로 쌓는다.
	 */
	private void renderPreviewBar(GuiGraphicsExtractor context) {
		int cx = previewCenterX();
		int cy = previewCenterY();
		int foodY = cy + 6;
		int heartY = cy - 20;
		int foodLevel = 14;
		float sat = 5.5f;
		float health = 11f, maxHealth = 20f;
		int left = cx - 40;
		int right = cx + 40;
		int cur = (int) Math.ceil(health);
		for (int i = 0; i < 10; i++) {
			int hx = left + i * 8;
			int lo = i * 2;
			int kind = cur >= lo + 2 ? 2 : (cur >= lo + 1 ? 1 : 0);
			boolean container = SPRITE_ERA
					? LunaGfx.drawTex(context, LunaGfx.mcId("textures/gui/sprites/hud/heart/container.png"),
							hx, heartY, 9, 9, 0, 0, 9, 9, 9, 0xFFFFFFFF)
					: LunaGfx.drawTex(context, LunaGfx.mcId("textures/gui/icons.png"),
							hx, heartY, 9, 9, 16, 0, 9, 9, 256, 0xFFFFFFFF);
			if (!container) {
				context.fill(hx, heartY, hx + 9, heartY + 9, 0x60000000);
			}
			if (kind > 0 && !drawHeart(context, hx, heartY, kind, 0xFFFFFFFF)) {
				context.fill(hx + 1, heartY + 1, hx + (kind == 2 ? 8 : 5), heartY + 8, 0xFFE03030);
			}
		}
		for (int i = 0; i < 10; i++) {
			int ix = iconX(right, i);
			int lo = i * 2;
			int kind = foodLevel >= lo + 2 ? 2 : (foodLevel >= lo + 1 ? 1 : 0);
			if (!drawFood(context, ix, foodY, 0, 0xFFFFFFFF)) {
				context.fill(ix, foodY, ix + 9, foodY + 9, 0x60000000);
			}
			if (kind > 0 && !drawFood(context, ix, foodY, kind, 0xFFFFFFFF)) {
				context.fill(ix + 1, foodY + 1, ix + (kind == 2 ? 8 : 5), foodY + 8, 0xFFC07040);
			}
		}
		float[] food = foodPreview.get() || satPreview.get() || healthPreview.get() ? new float[]{8f, 12.8f} : null;
		int newFood = food == null ? foodLevel : Math.min(20, foodLevel + 8);
		float newSat = food == null ? sat : Math.min(newFood, sat + 12.8f);
		drawOverlays(context, right, foodY, foodLevel, sat, newFood, newSat, food);
		if (food != null && healthPreview.get()) {
			float heal = estimateHeal(newFood, newSat, 0f, maxHealth - health);
			drawHealthPreview(context, left, heartY, health, maxHealth, 0f, heal);
		}
	}
}
