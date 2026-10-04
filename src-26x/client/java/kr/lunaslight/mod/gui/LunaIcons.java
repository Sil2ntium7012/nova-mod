package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * 아이콘 시스템. assets/lunaslight/font/icons.ttf(Lucide, ISC)를 텍스트처럼 그린다 - 색·배율·안티앨리어싱이
 * 폰트 파이프라인을 그대로 타 어떤 GUI 배율에서도 선명하다. 일반(SIZE)·중간(SIZE_MD)·큰(SIZE_LG) 세 크기의
 * JSON을 LunaCompat.fontStyle("icons"/"iconsmd"/"iconslg")로 가져온다(배율별 icons1~6 등).
 *
 * <h3>49-86차(8-3·8-5·8-9·8-10): 아이콘 전면 재작업</h3>
 * 이전엔 Lucide 서브셋에서 대충 고른 것 + 손으로 그린 글리프(삐꾸)가 섞여 겹치고 어설펐다(사용자 지적).
 * 이번엔 <b>모듈·역할마다 실제 Lucide 픽토그램을 하나씩</b> 골라 전부 서로 다르게(겹침 0) 새로 구웠다.
 * 폰트는 {@code tools/lucide2font.py}가 Lucide SVG(획 2·둥근 끝)를 채운 윤곽으로 바꿔 icons.ttf에 U+E900~로 넣는다.
 * 코드포인트는 그 스크립트가 정한다 - 아이콘을 바꾸려면 {@code tools/} 매핑을 고치고 다시 구우면 된다.
 */
public final class LunaIcons {
	private LunaIcons() {
	}
	private static final Map<String, String> MODULE_ICONS = new HashMap<>();
	static {
		MODULE_ICONS.put("afk", "\uE915");
		MODULE_ICONS.put("apple_skin_hud", "\uE91C");
		MODULE_ICONS.put("armor_bar_color", "\uE981");   // 49-245차: 방패 → 갑옷 흉갑(새 글리프)
		MODULE_ICONS.put("armor_hud", "\uE95D");
		MODULE_ICONS.put("auto_reconnect", "\uE950");
		MODULE_ICONS.put("auto_refill", "\uE953");
		MODULE_ICONS.put("background_sound", "\uE977");
		MODULE_ICONS.put("beacon_beam", "\uE924");
		MODULE_ICONS.put("block_info_hud", "\uE907");
		MODULE_ICONS.put("blocks_broken_hud", "\uE94F");
		MODULE_ICONS.put("borderless_window", "\uE93A");
		MODULE_ICONS.put("chat_enhancements", "\uE93D");
		MODULE_ICONS.put("chat_face", "\uE964");
		MODULE_ICONS.put("chat_filter", "\uE93E");
		MODULE_ICONS.put("chat_tidy", "\uE936");
		MODULE_ICONS.put("client_time", "\uE969");
		MODULE_ICONS.put("clock_hud", "\uE913");
		MODULE_ICONS.put("color_grading", "\uE919");
		MODULE_ICONS.put("combo_counter", "\uE96A");
		MODULE_ICONS.put("compass_hud", "\uE917");
		MODULE_ICONS.put("coords_biome_hud", "\uE938");
		MODULE_ICONS.put("cps_hud", "\uE944");
		MODULE_ICONS.put("crafting_helper", "\uE92C");
		MODULE_ICONS.put("crit_indicator", "\uE97B");
		MODULE_ICONS.put("crosshair_outline", "\uE957");
		MODULE_ICONS.put("custom_crosshair", "\uE91A");
		MODULE_ICONS.put("custom_keybinds", "\uE96C");
		MODULE_ICONS.put("death_info", "\uE962");
		MODULE_ICONS.put("debug_hud_style", "\uE90A");
		MODULE_ICONS.put("direction_hud", "\uE948");
		MODULE_ICONS.put("drop_dump", "\uE94C");
		MODULE_ICONS.put("drop_protection", "\uE937");
		MODULE_ICONS.put("dropped_item_info", "\uE94A");
		MODULE_ICONS.put("elytra_swap", "\uE920");
		MODULE_ICONS.put("entity_cull", "\uE971");
		MODULE_ICONS.put("entity_hide", "\uE91F");
		MODULE_ICONS.put("entity_look_overlay", "\uE958");
		MODULE_ICONS.put("fire_overlay", "\uE922");
		MODULE_ICONS.put("fov_lock", "\uE959");
		MODULE_ICONS.put("fps_hud", "\uE900");
		MODULE_ICONS.put("free_look", "\uE975");
		MODULE_ICONS.put("friend_alert", "\uE970");
		MODULE_ICONS.put("gamma_control", "\uE968");
		MODULE_ICONS.put("glint_control", "\uE965");
		MODULE_ICONS.put("gui_blur", "\uE91B");
		MODULE_ICONS.put("harvest_tracker", "\uE980");   // 49-116\uCC28: wheat \u2192 sprout(\uC0C8\uC2F9)
		MODULE_ICONS.put("held_item_counter", "\uE92D");
		MODULE_ICONS.put("hitbox", "\uE908");
		MODULE_ICONS.put("hotbar_row_swap", "\uE902");
		MODULE_ICONS.put("hud_background", "\uE967");
		MODULE_ICONS.put("hud_hide", "\uE91E");
		MODULE_ICONS.put("interface_style", "\uE96E");
		MODULE_ICONS.put("inventory_hud", "\uE92A");
		MODULE_ICONS.put("inventory_move", "\uE945");
		MODULE_ICONS.put("item_finder", "\uE95A");
		MODULE_ICONS.put("item_info_hud", "\uE96B");
		MODULE_ICONS.put("item_light_beam", "\uE923");
		MODULE_ICONS.put("item_pickup_toast", "\uE94B");
		MODULE_ICONS.put("item_tooltip_info", "\uE930");
		MODULE_ICONS.put("key_item_count", "\uE909");
		MODULE_ICONS.put("keystrokes_hud", "\uE931");
		MODULE_ICONS.put("measure", "\uE956");
		MODULE_ICONS.put("blueprint", "\uE949");       // 49-253차: 설계도(입체 상자)
		MODULE_ICONS.put("blueprint_hud", "\uE932");   // 49-253차: 설계도 HUD(층)
		MODULE_ICONS.put("memory_usage_hud", "\uE93B");
		MODULE_ICONS.put("mouse_tweaks", "\uE942");
		MODULE_ICONS.put("mousestrokes_hud", "\uE943");
		MODULE_ICONS.put("nametag_visibility", "\uE90D");
		MODULE_ICONS.put("now_playing", "\uE947");
		MODULE_ICONS.put("video_pip", "\uE941");
		MODULE_ICONS.put("occlusion_cull", "\uE929");
		MODULE_ICONS.put("pack_priority", "\uE932");
		MODULE_ICONS.put("particle_filter", "\uE966");
		MODULE_ICONS.put("ping_hud", "\uE961");
		MODULE_ICONS.put("ping_mark", "\uE939");
		MODULE_ICONS.put("player_block", "\uE973");
		MODULE_ICONS.put("potion_effects_hud", "\uE925");
		MODULE_ICONS.put("pvp_analyze", "\uE972");
		MODULE_ICONS.put("rarity_outline", "\uE928");
		MODULE_ICONS.put("aim_distance_hud", "\uE946");
		MODULE_ICONS.put("recorder", "\uE912");
		MODULE_ICONS.put("scoreboard_tweaks", "\uE935");
		MODULE_ICONS.put("screenshot_tool", "\uE90B");
		MODULE_ICONS.put("server_address_hud", "\uE95B");
		MODULE_ICONS.put("shield_cooldown", "\uE95E");
		MODULE_ICONS.put("shield_offset", "\uE960");
		MODULE_ICONS.put("shulker_peek", "\uE918");
		MODULE_ICONS.put("simple_health_hud", "\uE92E");
		MODULE_ICONS.put("smooth_scroll", "\uE911");
		MODULE_ICONS.put("sound_filter", "\uE976");
		MODULE_ICONS.put("move_rate_hud", "\uE927");
		MODULE_ICONS.put("sprint_toggle", "\uE926");
		MODULE_ICONS.put("subtitle_style", "\uE90C");
		MODULE_ICONS.put("tab_list_limit", "\uE974");
		MODULE_ICONS.put("tnt_timer", "\uE906");
		MODULE_ICONS.put("toast_filter", "\uE905");
		MODULE_ICONS.put("tps_hud", "\uE904");
		MODULE_ICONS.put("uptime_hud", "\uE96D");
		MODULE_ICONS.put("user_keybinds", "\uE916");
		MODULE_ICONS.put("view_snap", "\uE954");
		MODULE_ICONS.put("voice_hud", "\uE93F");
		MODULE_ICONS.put("waypoint", "\uE921");
		MODULE_ICONS.put("weather_changer", "\uE914");
		MODULE_ICONS.put("whisper_alert", "\uE93C");
		MODULE_ICONS.put("zoom", "\uE97C");
	}

	/** 49-76차(6-19): 일반 12 / 중간 16 / 큰 22px. 폰트 JSON은 tools/bake-font.py로 굽는다(시각 중심 3.5 유지). */
	public static final int SIZE = 12;
	public static final int SIZE_LG = 22;
	public static final int SIZE_MD = 16;

	public static final String BACK = "\uE901";
	public static final String CHECK = "\uE90E";
	public static final String CLOSE = "\uE97A";
	public static final String EDIT = "\uE94E";
	public static final String GRIP = "\uE92B";
	public static final String INFO = "\uE930";
	public static final String KEYBOARD = "\uE931";
	public static final String LAYOUT = "\uE934";
	public static final String LEFT = "\uE90F";
	public static final String LOGO = "\uE928";
	public static final String MOVE = "\uE945";
	public static final String PALETTE = "\uE94D";
	public static final String POWER = "\uE951";
	public static final String RESET = "\uE955";
	public static final String RIGHT = "\uE910";
	public static final String SEARCH = "\uE95A";
	public static final String SETTINGS = "\uE95C";
	public static final String SLIDERS = "\uE963";
	public static final String USER = "\uE96F";
	public static final String USERS = "\uE974";
	public static final String ACTIVITY = "\uE900";
	public static final String CHART = "\uE904";
	public static final String WIFI = "\uE979";
	public static final String PACKAGE = "\uE949";
	public static final String IMAGE = "\uE92F";
	// 49-90차(8-18·8-19): 삭제(trash-2) · 차단(ban) · 복사(copy) - 우클릭 메뉴·스크린샷 버튼용
	public static final String TRASH = "\uE97D";
	public static final String BAN = "\uE97E";
	public static final String COPY = "\uE97F";
	public static final String ALL = "\uE92A";

	/** [전체] 탭 = 격자(모두 보기). */
	public static final String ALL_TAB = ALL;

	/** 49-56차: 설정 페이지 탭 아이콘(49-86차: 빈 값이던 것을 알맞은 아이콘으로). */
	public static String forPage(kr.lunaslight.mod.module.SettingsPage p) {
		return switch (p) {
			case GENERAL -> "\uE95C";
			case UI -> "\uE94D";
			case GRAPHICS -> "\uE940";
			case KEYS -> KEYBOARD;   // 49-89차(8-8)
		};
	}

	public static String forCategory(ModuleCategory c) {
		return switch (c) {
			case HUD -> "\uE933";
			case VIEW -> "\uE91D";
			case COMBAT -> "\uE96A";
			case INVENTORY -> "\uE903";
			case FEATURE -> "\uE952";
			case SERVER -> WIFI;   // 49-138차: 서버 전용 기능
		};
	}

	public static String forModule(String moduleId, ModuleCategory category) {
		String g = MODULE_ICONS.get(moduleId);
		return g != null ? g : forCategory(category);
	}

	// ---------------------------------------------------------------- 그리기

	private static Component iconText(String glyph, boolean large) {
		return LunaCompat.styledText(glyph, LunaCompat.fontStyle(large ? "iconslg" : "icons"));
	}

	private static Component iconTextMd(String glyph) {
		return LunaCompat.styledText(glyph, LunaCompat.fontStyle("iconsmd"));
	}

	public static int width(Font tr, String glyph, boolean large) {
		Component t = iconText(glyph, large);
		return t == null ? 0 : LunaCompat.textWidth(tr, t);
	}

	/**
	 * 49-128차(사용자: "아이콘도 맨날 한 칸씩 밀려 있고"): 아이콘 글리프는 12px 칸 가운데에 잉크가 약 10px라
	 * 칸 왼쪽에 1px 여백이 있다. 그래서 x에 그리면 잉크가 x+1부터 시작해 어디서든 1px 오른쪽으로 밀려 보였다.
	 * 칸을 1px 당겨 잉크가 정확히 x에서 시작하게 한다(가운데 정렬 drawCentered는 폭 기준이라 그대로).
	 */
	public static void draw(GuiGraphicsExtractor ctx, Font tr, String glyph, int x, int y, int color) {
		Component t = iconText(glyph, false);
		if (t != null) {
			ctx.text(tr, t, x - 1, y, LunaDraw.applyAlpha(color), false);
		}
	}

	public static void drawLarge(GuiGraphicsExtractor ctx, Font tr, String glyph, int x, int y, int color) {
		Component t = iconText(glyph, true);
		if (t != null) {
			ctx.text(tr, t, x, y, LunaDraw.applyAlpha(color), false);
		}
	}

	public static void drawCentered(GuiGraphicsExtractor ctx, Font tr, String glyph, int cx, int y, int color, boolean large) {
		Component t = iconText(glyph, large);
		if (t != null) {
			ctx.text(tr, t, cx - LunaCompat.textWidth(tr, t) / 2, y, LunaDraw.applyAlpha(color), false);
		}
	}

	public static void drawInBox(GuiGraphicsExtractor ctx, Font tr, String glyph, int boxX, int boxY, int box, int color) {
		Component t = iconTextMd(glyph);
		if (t == null) {
			return;
		}
		int w = LunaCompat.textWidth(tr, t);
		// 49-236차: FreeType에서 잰 폭이 잉크보다 좁게 나와 아이콘이 가운데보다 1px 오른쪽에 섰다(GUI 2 실측) - 1px 당긴다
		drawAtPx(ctx, tr, t, boxX + (box - w) / 2 - 1, LunaDraw.iconMdYf(boxY, box), color);
	}

	/**
	 * 49-245차(사용자: "아이콘이 아래로 밀려 있어, 버전별로 확인해서 고쳐줘"): 상자 가운데 = 정수 + 0.5인 경우가 많아(짝수 상자, 잉크 중심 3.5)
	 * 반올림 방향에 따라 버전마다 아이콘이 반 칸씩 위나 아래로 밀렸다. 이제 반올림하지 않고 화면 픽셀 단위로 맞춘 실제 자리에 그린다
	 * (설정 화면 가상 GUI 2면 0.5단위 = 1픽셀이라 흐려지지 않는다). 행렬을 못 쓰는 버전만 예전처럼 반올림.
	 */
	private static void drawAtPx(GuiGraphicsExtractor ctx, Font tr, Component t, int x, float y, int color) {
		float ppu = Math.max(1f, LunaGfx.pxPerUnit());
		float snapped = Math.round(y * ppu) / ppu;
		int iy = (int) Math.floor(snapped);
		float frac = snapped - iy;
		if (frac > 0.01f && LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, 0f, frac);
			ctx.text(tr, t, x, iy, LunaDraw.applyAlpha(color), false);
			LunaCompat.guiPop(ctx);
			return;
		}
		iy = Math.round(snapped);
		ctx.text(tr, t, x, iy, LunaDraw.applyAlpha(color), false);
	}
}
