package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ColorGradeHook;
import kr.lunaslight.mod.util.ColorGradeShader;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * 색 보정 - 게임 화면(HUD 제외)을 사진 보정하듯 다듬는다.
 *
 * <p>49-170차 전면 재작성(사용자: "게임 화면을 따스하게 하거나 진하게 하는 느낌이어야 하는데 지금은 흑백, 흐리기
 * 이딴 느낌 - 진짜 사진을 예쁘게 하는 것처럼"). 예전(49-47차)엔 반투명 색 막 3장뿐이라 "어느 색으로 당기기"만 됐다.
 * 이제 {@link ColorGradeShader}가 바닐라 후처리 체인에 Luna 셰이더를 끼워 <b>채도·대비·색온도·생동감·선명도·비네트·
 * 페이드</b>를 픽셀마다 계산한다(1.17+, 1.21.x, 26.x). 셰이더를 못 쓰는 버전/상황에서는 예전 색 막으로 돌아간다
 * (그때는 밝기·색온도·색조만 먹는다 - 미리보기에 그렇게 적힌다).
 *
 * <p>프리셋은 사진 앱의 필터처럼 슬라이더 값을 한 번에 채운다(고른 뒤 슬라이더를 만지면 그 값이 그대로 남는다).
 */
public class ColorGradingModule extends Module implements ColorGradeHook.Handler {

	public enum Preset {
		CUSTOM("직접 설정", null),
		WARM("따뜻함", new int[]{35, 5, 115, 108, 4, 15, 12, 10, 0, 0}),
		VIVID("선명함", new int[]{5, 0, 135, 118, 2, 30, 8, 35, 0, 0}),
		CINEMA("시네마틱", new int[]{-10, 8, 95, 122, -4, 10, 45, 15, 18, -5}),
		VINTAGE("빈티지", new int[]{25, 12, 80, 92, 3, 0, 30, 0, 40, 5}),
		COOL("차가움", new int[]{-35, -5, 105, 110, 2, 10, 10, 10, 0, 0}),
		FRESH("청량함", new int[]{-15, -10, 125, 105, 8, 25, 0, 25, 0, 8}),
		SOFT("부드러움", new int[]{10, 0, 92, 90, 6, 0, 15, 0, 22, 10}),
		MONO("흑백", new int[]{0, 0, 0, 115, 2, 0, 25, 20, 10, 0});

		private final String label;
		final int[] values;

		Preset(String label, int[] values) {
			this.label = label;
			this.values = values;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Preset> preset = register(new EnumSetting<>(
			"preset", "프리셋", "사진 필터처럼 아래 값을 한 번에 채웁니다. 고른 뒤 값을 바꿔도 됩니다.", Preset.CUSTOM, Preset.class).vertical());
	private final IntSetting temperature = register(new IntSetting(
			"temperature_v2", "색온도", "오른쪽은 따뜻하게(주황), 왼쪽은 차갑게(파랑).", 0, -100, 100, 5));
	private final IntSetting tint = register(new IntSetting(
			"tint_v2", "색조", "오른쪽은 자홍 쪽, 왼쪽은 초록 쪽.", 0, -100, 100, 5));
	private final IntSetting saturation = register(new IntSetting(
			"saturation", "채도", "100이 원래 색입니다. 0이면 흑백.", 100, 0, 200, 5).unit("%"));
	private final IntSetting contrast = register(new IntSetting(
			"contrast", "대비", "100이 원래 대비입니다.", 100, 50, 200, 5).unit("%"));
	private final IntSetting brightness = register(new IntSetting(
			"brightness_v2", "밝기", "0이 원래 밝기입니다.", 0, -100, 100, 5));
	private final IntSetting vibrance = register(new IntSetting(
			"vibrance", "생동감", "옅은 색만 골라 진하게 합니다(채도보다 자연스럽습니다).", 0, -100, 100, 5));
	private final IntSetting vignette = register(new IntSetting(
			"vignette", "비네트", "가장자리를 어둡게 합니다.", 0, 0, 100, 5).unit("%"));
	private final IntSetting sharpen = register(new IntSetting(
			"sharpen", "선명도", "윤곽을 또렷하게 합니다.", 0, 0, 100, 5).unit("%"));
	private final IntSetting fade = register(new IntSetting(
			"fade", "페이드", "검정을 살짝 띄워 필름 느낌을 냅니다.", 0, 0, 100, 5).unit("%"));
	private final IntSetting gamma = register(new IntSetting(
			"gamma", "중간 톤", "중간 밝기만 밝게(오른쪽) 또는 어둡게(왼쪽).", 0, -100, 100, 5));

	private final IntSetting[] sliders = {temperature, tint, saturation, contrast, brightness, vibrance, vignette, sharpen, fade, gamma};
	private boolean applying;

	public ColorGradingModule() {
		super("color_grading", "색 보정", ModuleCategory.VIEW, "화면 색감 (HUD 제외)");
		ColorGradeHook.set(this);
		preset.onChange(this::applyPreset);
		for (IntSetting s : sliders) {
			s.onChange(() -> {
				if (!applying && preset.get() != Preset.CUSTOM && !matchesPreset(preset.get())) {
					applying = true;
					preset.setValue(Preset.CUSTOM);
					applying = false;
				}
			});
		}
	}

	private void applyPreset() {
		Preset p = preset.get();
		if (p == null || p.values == null || applying) {
			return;
		}
		applying = true;
		try {
			for (int i = 0; i < sliders.length; i++) {
				sliders[i].setValue(p.values[i]);
			}
		} finally {
			applying = false;
		}
	}

	private boolean matchesPreset(Preset p) {
		if (p == null || p.values == null) {
			return false;
		}
		for (int i = 0; i < sliders.length; i++) {
			if (sliders[i].get() != p.values[i]) {
				return false;
			}
		}
		return true;
	}

	private boolean identity() {
		return temperature.get() == 0 && tint.get() == 0 && saturation.get() == 100 && contrast.get() == 100
				&& brightness.get() == 0 && vibrance.get() == 0 && vignette.get() == 0 && sharpen.get() == 0
				&& fade.get() == 0 && gamma.get() == 0;
	}

	/** 셰이더에 넘길 12개 값: P0(색온도, 색조, 채도, 대비) P1(밝기, 생동감, 비네트, 선명도) P2(페이드, 중간 톤, 0, 0). */
	private float[] params() {
		return new float[]{
				temperature.get() / 100f, tint.get() / 100f, saturation.get() / 100f, contrast.get() / 100f,
				brightness.get() / 100f, vibrance.get() / 100f, vignette.get() / 100f, sharpen.get() / 100f,
				fade.get() / 100f, gamma.get() / 100f, 0f, 0f};
	}

	@Override
	protected void onDisable() {
		ColorGradeShader.uninstall(client);
	}

	// ==================== 적용 ====================

	@Override
	public void grade(DrawContext ctx, int w, int h) {
		boolean on = isEnabled() && !identity();
		if (ColorGradeShader.available()) {
			ColorGradeShader.apply(client, on, params());
			if (ColorGradeShader.available()) {
				return;   // 셰이더가 처리한다(HUD 직전이 아니라 월드 그리기 끝에 돈다)
			}
		}
		if (on) {
			gradeRect(ctx, 0, 0, w, h);
		}
	}

	/** 폴백: 얇은 색 막(밝기·색온도·색조만). 전부 바닐라 알파 블렌딩(ctx.fill)이라 어느 버전에서도 같게 동작한다. */
	private void gradeRect(DrawContext ctx, int x, int y, int w, int h) {
		int x2 = x + w, y2 = y + h;
		float b = brightness.get() / 100f;
		if (b > 0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0xFFFFFF, b * 0.30f));
		} else if (b < -0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0x000000, -b * 0.45f));
		}
		float t = temperature.get() / 100f;
		if (t > 0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0xFFA640, t * 0.24f));
		} else if (t < -0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0x4D8CFF, -t * 0.24f));
		}
		float n = tint.get() / 100f;
		if (n > 0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0xFF5AD6, n * 0.16f));
		} else if (n < -0.005f) {
			ctx.fill(x, y, x2, y2, alpha(0x5AFF7A, -n * 0.16f));
		}
		float v = vignette.get() / 100f;
		if (v > 0.005f) {
			int band = Math.max(8, Math.min(w, h) / 6);
			for (int i = 0; i < 4; i++) {
				int a = Math.round(v * 0x28 * (4 - i) / 4f);
				int in = band * i / 4;
				ctx.fill(x + in, y + in, x2 - in, y + in + band / 4, (a << 24));
				ctx.fill(x + in, y2 - in - band / 4, x2 - in, y2 - in, (a << 24));
				ctx.fill(x + in, y + in, x + in + band / 4, y2 - in, (a << 24));
				ctx.fill(x2 - in - band / 4, y + in, x2 - in, y2 - in, (a << 24));
			}
		}
	}

	private static int alpha(int rgb, float a) {
		int v = Math.max(0, Math.min(255, Math.round(a * 255f)));
		return (v << 24) | (rgb & 0x00FFFFFF);
	}

	// ==================== 미리보기(설정 화면) ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	/** 셰이더와 같은 계산을 CPU로 - 미리보기 색 견본용. */
	private int gradeColor(int argb) {
		float[] p = params();
		float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		r = r * (1f + p[4] * 0.55f) + p[4] * 0.04f;
		g = g * (1f + p[4] * 0.55f) + p[4] * 0.04f;
		b = b * (1f + p[4] * 0.55f) + p[4] * 0.04f;
		r *= 1f + 0.20f * p[0];
		g *= 1f + 0.05f * p[0];
		b *= 1f - 0.22f * p[0];
		r *= 1f + 0.08f * p[1];
		g *= 1f - 0.11f * p[1];
		b *= 1f + 0.08f * p[1];
		float gm = 1f - p[9] * 0.35f;
		r = (float) Math.pow(Math.max(0, r), gm);
		g = (float) Math.pow(Math.max(0, g), gm);
		b = (float) Math.pow(Math.max(0, b), gm);
		float k = p[3];
		float[] c = {r, g, b};
		for (int i = 0; i < 3; i++) {
			float v = c[i];
			float s = v * v * (3f - 2f * v);
			c[i] = k >= 1f ? v + (s - v) * (k - 1f) : 0.5f + (v - 0.5f) * k;
		}
		float l = c[0] * 0.2126f + c[1] * 0.7152f + c[2] * 0.0722f;
		float sat = Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
		float m = Math.max(0f, Math.min(1f, 1f - sat * 1.5f));
		for (int i = 0; i < 3; i++) {
			float boosted = l + (c[i] - l) * (1f + p[5]);
			c[i] = c[i] + (boosted - c[i]) * m;
		}
		l = c[0] * 0.2126f + c[1] * 0.7152f + c[2] * 0.0722f;
		for (int i = 0; i < 3; i++) {
			c[i] = l + (c[i] - l) * p[2];
			c[i] = c[i] * (1f - p[8] * 0.18f) + p[8] * 0.07f;
			c[i] = Math.max(0f, Math.min(1f, c[i]));
		}
		return 0xFF000000 | (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
	}

	private static final int[] SWATCH = {0xFF6FA8DC, 0xFFB7D28A, 0xFF8FBF5A, 0xFFC9A46B, 0xFF9C7A52, 0xFFE2E6EA, 0xFFD9534F, 0xFF3F5E8C};

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (!isPreview()) {
			return;
		}
		int x = previewX(), y = previewY(), w = previewW(), h = previewH();
		boolean shader = ColorGradeShader.available();
		if (!shader && !identity()) {
			// 색 막 방식은 화면 위에 그대로 덮이므로 오른쪽 반만 덮어 비교
			int half = w / 2;
			gradeRect(context, x + half, y, w - half, h);
			context.fill(x + half, y, x + half + 1, y + h, 0x66FFFFFF);
		}
		// 색 견본 두 줄(원본 / 보정) - 셰이더와 같은 식을 CPU로 계산
		int pad = 6;
		int n = SWATCH.length;
		int cell = Math.max(6, (w - pad * 2 - (n - 1) * 2) / n);
		int rowH = Math.max(8, Math.min(14, (h - 40) / 2));
		int sy = y + h - pad - rowH * 2 - 2;
		int sx = x + pad;
		LunaDraw.roundRect(context, x + pad - 3, sy - 13, cell * n + (n - 1) * 2 + 6, rowH * 2 + 18, 4, 0xB0101214);
		LunaCompat.drawHudText(context, client.textRenderer, "§7원본 → 보정", sx, sy - 11, 0xFFFFFFFF);
		for (int i = 0; i < n; i++) {
			int cx = sx + i * (cell + 2);
			context.fill(cx, sy, cx + cell, sy + rowH, SWATCH[i]);
			context.fill(cx, sy + rowH + 2, cx + cell, sy + rowH * 2 + 2, gradeColor(SWATCH[i]));
		}
		String note = shader ? "게임 화면에 바로 적용" : "이 버전은 색 막 방식(채도, 대비 미지원)";
		LunaCompat.drawHudText(context, client.textRenderer, "§7" + note, x + pad, y + pad, 0xFFFFFFFF);
	}
}
