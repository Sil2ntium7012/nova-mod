package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * 체력 - 한 줄 하트를 색 층으로.
 *
 * 49-39차 재작성(사용자: "숫자로 하지 말고 기존 체력을 빨주노초파남보, 그 이상은 +숫자"):
 * 하트 줄은 언제나 한 줄(10칸). 체력 20마다 색이 한 단계 올라간다 - 빨강(바닐라) → 주황 → 노랑 → 초록 →
 * 파랑 → 남색 → 보라. 지금 층의 하트는 바로 아래 층(가득 찬 줄) 위에 그려지므로, 예를 들어 45 체력은
 * "주황 줄 위에 노랑 2.5칸". 7층(140)을 넘는 만큼은 하트 왼쪽에 "+N"으로만. 흡수(황금 사과)는 먼저 깎이는
 * 체력이라 맨 위 층에 황금 하트로 얹는다.
 *
 * 그리기: HealthBarMixin(InGameHud#renderHealthBar HEAD)이 HealthBarHook을 통해 여기로 넘긴다. 최대 체력 +
 * 흡수가 20 이하면 바닐라 그대로(깜빡임·재생 애니메이션 유지). 갑옷 줄 위치도 같은 판단(compressingNow)을 써서
 * 하트 바로 위에 붙는다. 층 하트는 흰 하트 스프라이트(textures/gui/heart_w_*.png)에 색을 곱해 그리고 그 위에
 * 바닐라와 같은 자리의 하이라이트 점(heart_hl_*.png)을 얹는다. 1층(빨강)은 바닐라 스프라이트 그대로(독·시듦·얼음·
 * 하드코어 무늬 포함).
 */
public class SimpleHealthHudModule extends Module {

	private final BooleanSetting overflowText = register(new BooleanSetting(
			"overflow_text", "+숫자", "7층(140)을 넘는 체력을 하트 왼쪽에 +숫자로 표시합니다.", true));

	private final ColorSetting overflowColor = register(new ColorSetting(
			"overflow_color", "+숫자 색", "+숫자의 색입니다.", 0xFFF2F4F6));

	/** 층 색(1층은 바닐라 빨강 스프라이트라 여기 값은 미리보기에만 씀). 빨·주·노·초·파·남·보. */
	private static final int[] LAYER_COLORS = {
		0xFFFF1313, 0xFFFF8A1F, 0xFFFFE23A, 0xFF4CE05A, 0xFF3B8BFF, 0xFF4B4BD6, 0xFFB05CFF
	};
	private static final int LAYERS = LAYER_COLORS.length;

	public SimpleHealthHudModule() {
		super("simple_health_hud", "체력", ModuleCategory.HUD, "한 줄 하트 | 20마다 색 층");
		defaultEnabled(true);
		kr.lunaslight.mod.util.HealthBarHook.set(this::replaceHealthBar);
		// 갑옷 줄도 같은 판단을 써서 하트 바로 위에 붙인다(바닐라는 줄 수만큼 갑옷을 띄움).
		kr.lunaslight.mod.util.HealthBarHook.setCompressing(this::compressingNow);
	}

	/** 바닐라 renderStatusBars와 같은 식: max(최대 체력 속성, 현재 체력) + 흡수 > 20이면 두 줄이 된다. */
	private boolean compressingNow() {
		if (!isEnabled() || client == null || client.player == null) {
			return false;
		}
		Player p = client.player;
		float max = LunaCompat.getMaxHealth(p);
		float health = Math.max(0f, p.getHealth());
		float absorption = 0f;
		try {
			absorption = Math.max(0f, p.getAbsorptionAmount());
		} catch (Throwable ignored) {
		}
		return Math.max(max, (float) Math.ceil(health)) + (float) Math.ceil(absorption) > 20f;
	}

	/** HealthBarMixin에서: 한 줄로 그릴 상황이면 바닐라 대신 그리고 true. */
	private boolean replaceHealthBar(Object ctxObj, int x, int y, float maxHealth, int lastHealth, int health, int absorption) {
		if (!isEnabled() || !(ctxObj instanceof GuiGraphicsExtractor ctx) || client == null || client.player == null) {
			return false;
		}
		if (maxHealth + absorption <= 20f) {
			return false;   // 원래 한 줄이면 바닐라 그대로
		}
		Player player = client.player;
		boolean hardcore = isHardcore();
		HeartKind kind = heartKind(player);
		int hp = Math.max(0, health);   // 바닐라가 넘기는 값(1 하트 = 2)

		// 층 계산: 0층 = 1~20, 1층 = 21~40 … 6층 = 121~140. 그 위는 +숫자.
		int layer = hp <= 0 ? 0 : (hp - 1) / 20;
		int overflow = 0;
		int inLayer;
		if (layer >= LAYERS) {
			overflow = hp - LAYERS * 20;
			layer = LAYERS - 1;
			inLayer = 20;   // 보라 줄 가득
		} else {
			inLayer = hp - layer * 20;
		}

		// 1) 빈 하트 틀
		for (int i = 0; i < 10; i++) {
			drawHeart(ctx, x + i * 8, y, HeartKind.CONTAINER, hardcore, false);
		}
		// 2) 바로 아래 층 = 가득 찬 줄
		if (layer > 0) {
			for (int i = 0; i < 10; i++) {
				drawLayerHeart(ctx, x + i * 8, y, layer - 1, kind, hardcore, false);
			}
		}
		// 3) 지금 층(부분)
		for (int i = 0; i < 10; i++) {
			int units = Math.max(0, Math.min(2, inLayer - i * 2));
			if (units == 2) {
				drawLayerHeart(ctx, x + i * 8, y, layer, kind, hardcore, false);
			} else if (units == 1) {
				drawLayerHeart(ctx, x + i * 8, y, layer, kind, hardcore, true);
			}
		}
		// 4) 흡수(먼저 깎이는 체력) = 맨 위 황금 층
		int abs = Math.max(0, absorption);
		if (abs > 0) {
			for (int i = 0; i < 10; i++) {
				int units = Math.max(0, Math.min(2, abs - i * 2));
				if (units == 2) {
					drawHeart(ctx, x + i * 8, y, HeartKind.ABSORBING, hardcore, false);
				} else if (units == 1) {
					drawHeart(ctx, x + i * 8, y, HeartKind.ABSORBING, hardcore, true);
				}
			}
			if (abs > 20) {
				overflow += abs - 20;
			}
		}
		// 5) 7층 초과분 +숫자(하트 왼쪽)
		if (overflow > 0 && overflowText.get()) {
			String t = "+" + overflow;
			int tw = LunaCompat.getTextWidth(client.font, t);
			LunaCompat.drawHudText(ctx, client.font, t, x - 4 - tw, y + Math.round(4.5f - LunaCompat.textVisualCenter()), overflowColor.getArgb());   // 하트(9px) 세로 중심에
		}
		return true;
	}

	/** layer 0 = 바닐라 빨강(무늬 포함), 그 위 층은 흰 하트에 색을 곱함. */
	private void drawLayerHeart(GuiGraphicsExtractor ctx, int x, int y, int layer, HeartKind kind, boolean hardcore, boolean half) {
		if (layer <= 0) {
			drawHeart(ctx, x, y, kind, hardcore, half);
			return;
		}
		drawTintedHeart(ctx, x, y, LAYER_COLORS[Math.min(layer, LAYERS - 1)], half);
	}

	private void drawTintedHeart(GuiGraphicsExtractor ctx, int x, int y, int color, boolean half) {
		String kind = half ? "half" : "full";
		boolean ok = kr.lunaslight.mod.gui.LunaGfx.drawTex(ctx,
			kr.lunaslight.mod.gui.LunaGfx.id("textures/gui/heart_w_" + kind + ".png"), x, y, 9, 9, 0, 0, 9, 9, 9, color);
		if (ok) {
			kr.lunaslight.mod.gui.LunaGfx.drawTex(ctx,
				kr.lunaslight.mod.gui.LunaGfx.id("textures/gui/heart_hl_" + kind + ".png"), x, y, 9, 9, 0, 0, 9, 9, 9, 0xFFFFFFFF);
		} else {
			ctx.fill(x + 1, y + 2, x + (half ? 5 : 8), y + 7, color);
		}
	}

	/** 바닐라 InGameHud.HeartType과 같은 순서/아틀라스 칸(icons.png u = 16 + (칸×2 + 반칸)×9). */
	private enum HeartKind {
		CONTAINER(0, "container"), NORMAL(2, ""), POISONED(4, "poisoned_"), WITHERED(6, "withered_"),
		ABSORBING(8, "absorbing_"), FROZEN(9, "frozen_");

		final int atlasIndex;
		final String spritePrefix;

		HeartKind(int atlasIndex, String spritePrefix) {
			this.atlasIndex = atlasIndex;
			this.spritePrefix = spritePrefix;
		}
	}

	private static HeartKind heartKind(Player p) {
		try {
			if (p.hasEffect(net.minecraft.world.effect.MobEffects.POISON)) {
				return HeartKind.POISONED;
			}
			if (p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
				return HeartKind.WITHERED;
			}
		} catch (Throwable ignored) {
		}
		Object frozen = LunaCompat.invokeNoArg(p, "isFrozen");   // 1.17+
		return frozen instanceof Boolean b && b ? HeartKind.FROZEN : HeartKind.NORMAL;
	}

	private Object hardcoreWorld;
	private boolean hardcoreCached;

	/** 하드코어 월드면 하트 무늬가 다르다(world.getLevelProperties().isHardcore(), 월드별 1회 조회). */
	private boolean isHardcore() {
		Object world = client.level;
		if (world == null) {
			return false;
		}
		if (world != hardcoreWorld) {
			hardcoreWorld = world;
			hardcoreCached = false;
			try {
				Object props = LunaCompat.invokeNoArg(world, "getLevelProperties");
				Object hc = props == null ? null : LunaCompat.invokeNoArg(props, "isHardcore");
				hardcoreCached = hc instanceof Boolean b && b;
			} catch (Throwable ignored) {
			}
		}
		return hardcoreCached;
	}

	private static final boolean SPRITE_ERA = kr.lunaslight.mod.util.LunaVersion.isWithin("1.20.2", null);

	/** 바닐라 하트 스프라이트 한 장. 1.20.2+는 낱장 png(hud/heart/…), 그 아래는 icons.png 좌표. */
	private void drawHeart(GuiGraphicsExtractor ctx, int x, int y, HeartKind kind, boolean hardcore, boolean half) {
		boolean ok;
		if (SPRITE_ERA) {
			String name;
			if (kind == HeartKind.CONTAINER) {
				name = hardcore ? "container_hardcore" : "container";
			} else {
				name = kind.spritePrefix + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
			}
			ok = kr.lunaslight.mod.gui.LunaGfx.drawTex(ctx,
				kr.lunaslight.mod.gui.LunaGfx.mcId("textures/gui/sprites/hud/heart/" + name + ".png"),
				x, y, 9, 9, 0, 0, 9, 9, 9, 0xFFFFFFFF);
		} else {
			Identifier icons = kr.lunaslight.mod.gui.LunaGfx.mcId("textures/gui/icons.png");
			int u = 16 + (kind.atlasIndex * 2 + (kind != HeartKind.CONTAINER && half ? 1 : 0)) * 9;
			int v = hardcore ? 45 : 0;
			ok = kr.lunaslight.mod.gui.LunaGfx.drawTex(ctx, icons, x, y, 9, 9, u, v, 9, 9, 256, 0xFFFFFFFF);
		}
		if (!ok && kind != HeartKind.CONTAINER) {
			int c = kind == HeartKind.ABSORBING ? 0xFFF2C94C : 0xFFFF3A3A;
			ctx.fill(x + 1, y + 2, x + (half ? 5 : 8), y + 7, c);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview()) {
			// 미리보기: 주황 줄 위에 노랑 2.5칸(= 45 체력)
			int hx = previewCenterX() - 20;
			int hy = previewCenterY() - 4;
			for (int i = 0; i < 5; i++) {
				drawHeart(context, hx + i * 8, hy, HeartKind.CONTAINER, false, false);
				drawTintedHeart(context, hx + i * 8, hy, LAYER_COLORS[1], false);
			}
			drawTintedHeart(context, hx, hy, LAYER_COLORS[2], false);
			drawTintedHeart(context, hx + 8, hy, LAYER_COLORS[2], false);
			drawTintedHeart(context, hx + 16, hy, LAYER_COLORS[2], true);
			if (overflowText.get()) {   // 49-88차(8-12): +숫자 설정이 미리보기에도 보이게
				String t = "+12";
				int tw = LunaCompat.getTextWidth(client.font, t);
				LunaCompat.drawHudText(context, client.font, t, hx - 4 - tw,
					hy + Math.round(4.5f - LunaCompat.textVisualCenter()), overflowColor.getArgb());
			}
			return;
		}
		// 실제 그리기는 HealthBarMixin → replaceHealthBar 에서(바닐라 하트 자리 그대로). 여기선 할 일 없음.
	}
}
