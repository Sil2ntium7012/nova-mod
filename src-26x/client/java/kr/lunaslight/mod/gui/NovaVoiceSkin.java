package kr.lunaslight.mod.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 49-473차(사용자: "V가 보이스 설정이던데 … 그 너굴보이스 모드 우리 클라처럼 UI 싹 바꿔 줘, 배포할 때 적용되면 좋을 텐데"): <b>너굴마을 보이스 화면 노바 스타일</b>.
 * 너굴마을 보이스(ng-voiceaddon, Simple Voice Chat의 V 화면을 바꾸는 모드)는 화면 조각을 전부 {@code kr.mcng.voiceaddon.gui.Flat}의
 * sprite(판, 버튼, 알약 스위치, 손잡이, 탭 …)와 text로 그린다. 그 두 곳만 가로채(mixin/NgVoiceFlatMixin) 같은 자리에 노바 설정 화면 모양
 * (둥근 카드, 테마 강조색, 테마 글자색, 노바 아이콘, 노바 글꼴 설정)으로 그린다. 그 모드 파일은 안 건드린다 - 노바만 업데이트하면 바뀐다.
 * 탭 아이콘(마이크, 스피커 …) 같은 그림은 그대로 쓴다. 그 모드가 없거나 구조가 바뀌면 아무 일도 안 한다.
 */
public final class NovaVoiceSkin {
	private NovaVoiceSkin() {
	}

	/** NgVoiceSkinModule이 끼운다. */
	public static volatile boolean on = true;

	private static boolean themed;

	private static void theme() {
		LunaClientScreen.applyScreenTheme();
		themed = true;
	}

	/** 그 모드의 sprite 하나를 노바 모양으로 그렸으면 true(원래 그림은 안 그림). */
	public static boolean sprite(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h) {
		if (!on || id == null || !"ng_voiceaddon".equals(id.getNamespace())) {
			return false;
		}
		try {
			theme();
			String p = id.getPath();
			int acc = LunaClientScreen.themeAccent();
			int r = Math.max(2, Math.min(6, h / 3));
			switch (p) {
				case "panel" -> {
					LunaDraw.card3d(ctx, x, y, w, h, 8, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());
					LunaDraw.roundRect(ctx, x + 10, y + 1, w - 20, 2, 1, LunaDraw.withAlpha(acc, 0xC0));
				}
				case "btn" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaClientScreen.ink(0x16));
				case "btn_hover" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaClientScreen.ink(0x2A));
				case "btn_disabled" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaClientScreen.ink(0x0A));
				case "btn_accent" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaDraw.withAlpha(acc, 0xE0));
				case "btn_accent_hover" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaDraw.withAlpha(acc, 0xFF));
				case "ghost_hover" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaClientScreen.ink(0x1C));
				case "pill_on" -> LunaDraw.roundRect(ctx, x, y, w, h, h / 2, LunaDraw.withAlpha(acc, 0xF0));
				case "pill_off" -> LunaDraw.roundRect(ctx, x, y, w, h, h / 2, LunaClientScreen.ink(0x30));
				case "pill_disabled" -> LunaDraw.roundRect(ctx, x, y, w, h, h / 2, LunaClientScreen.ink(0x12));
				case "knob" -> LunaDraw.roundRect(ctx, x, y, w, h, Math.min(w, h) / 2, 0xFFFFFFFF);
				case "tab_sel" -> {
					LunaDraw.roundRect(ctx, x, y, w, h, r, LunaDraw.withAlpha(acc, 0x30));
					LunaDraw.roundRect(ctx, x + 4, y + h - 2, w - 8, 2, 1, acc);
				}
				case "tab_hover" -> LunaDraw.roundRect(ctx, x, y, w, h, r, LunaClientScreen.ink(0x18));
				case "reset" -> icon(ctx, LunaIcons.RESET, x, y, w, h);
				case "arrow_l" -> icon(ctx, LunaIcons.LEFT, x, y, w, h);
				case "arrow_r" -> icon(ctx, LunaIcons.RIGHT, x, y, w, h);
				default -> {
					return false;
				}
			}
			return true;
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("novaVoiceSkin", t);
			return false;
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
	}

	private static void icon(GuiGraphicsExtractor ctx, String glyph, int x, int y, int w, int h) {
		int box = Math.min(w, h);
		LunaIcons.drawInBox(ctx, Minecraft.getInstance().font, glyph, x + (w - box) / 2, y + (h - box) / 2, box, LunaClientScreen.themeSub());
	}

	/** 그 모드의 글자색(팔레트 값)을 노바 테마 색으로. 모르는 색은 그대로. */
	public static int color(int c) {
		if (!on) {
			return c;
		}
		if (!themed) {
			theme();
			LunaClientScreen.restoreScreenTheme();
		}
		return switch (c) {
			case 0xFFF1F3EE -> LunaClientScreen.themeText();
			case 0xFFCFD4CB -> LunaClientScreen.themeSub();
			case 0xFF9AA094 -> LunaClientScreen.themeDim();
			case 0xFF241A05 -> kr.lunaslight.mod.util.LunaTheme.luminance(LunaClientScreen.themeAccent()) > 0.6f ? 0xFF15181C : 0xFFFFFFFF;
			case 0xFF272A25 -> LunaClientScreen.themeLine();
			default -> c;
		};
	}
}
