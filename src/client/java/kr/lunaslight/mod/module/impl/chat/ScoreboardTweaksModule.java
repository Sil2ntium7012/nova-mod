package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 스코어보드(사이드바).
 *
 * 49-24차 재작성(사용자: "숨김 따로, 빨간 숫자 제거, 모서리 둥글게, 위치 이동"): 기능이 켜져 있으면
 * ScoreboardDisplayMixin이 바닐라 사이드바 렌더를 취소하고 여기서 같은 데이터(LunaCompat.sidebar - 팀 색 슬롯/
 * 일반 사이드바, 점수 내림차순 15개, 팀 색 장식, 서버 지정 숫자 형식)를 Luna 스타일로 다시 그린다.
 *  - 숨기기(별도 스위치) / 점수 숫자 표시(끄면 빨간 숫자 제거) / 모서리 둥글기 / 배경·제목 줄 색 / 위치·크기(HUD 편집기)
 * 데이터는 틱마다 한 번만 모으고(리플렉션·정렬) 프레임엔 그리기만.
 */
public class ScoreboardTweaksModule extends Module {

	private final BooleanSetting hideScoreboard = register(new BooleanSetting(
			"hide_scoreboard", "숨김", "사이드바 스코어보드를 표시하지 않습니다.", false));

	// 49-195차(사용자: "점수 숫자 > 점수 끄기로 바꾸고 기본 끄기로"): 켜면 오른쪽 빨간 점수를 숨긴다. 기본 = 숨김.
	// 뜻이 뒤집혀서 새 id(hide_numbers)로 둔다 - 옛 show_numbers 값은 이어받지 않는다.
	private final BooleanSetting hideNumbers = register(new BooleanSetting(
			"hide_numbers", "점수 끄기", "오른쪽의 빨간 점수 숫자를 숨깁니다.", true));

	private final IntSetting radius = register(new IntSetting(
			"radius", "모서리", "모서리를 둥글게 깎는 정도입니다. 0이면 각집니다.", 4, 0, 6, 1).style());

	private final BooleanSetting background = register(new BooleanSetting(
			"background", "배경", "줄 뒤에 배경을 깝니다.", true).style());

	private final ColorSetting backgroundColor = register(new ColorSetting(
			"background_color", "배경 색", "줄 배경의 색입니다.", 0x4D000000));

	private final BooleanSetting titleBar = register(new BooleanSetting(
			"title_bar", "제목 배경", "제목 줄 배경을 조금 더 진하게 표시합니다.", true).style());

	private final ColorSetting titleColor = register(new ColorSetting(
			"title_color", "제목 배경 색", "제목 줄 배경의 색입니다.", 0x66000000));

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "스코어보드 위치입니다. 기본은 오른쪽 가운데입니다.",
			HudPosition.of(HudPosition.Anchor.RIGHT_CENTER, 0, 0)));

	private static final int ROW_H = 10;
	private static final int PAD = 4;

	private LunaCompat.Sidebar cached;

	public ScoreboardTweaksModule() {
		super("scoreboard_tweaks", "스코어보드", ModuleCategory.HUD, "숨기기 | 숫자 | 둥근 모서리 | 위치");
		background.withColor(backgroundColor);
		titleBar.withColor(titleColor);
	}

	/** ScoreboardDisplayMixin: 기능이 켜져 있으면 바닐라 사이드바는 항상 취소(숨김이든 우리가 그리든). */
	public boolean replacesVanilla() {
		return isEnabled() && isVersionSupported();
	}

	/** 예전 호환. */
	public boolean isHidden() {
		return isEnabled() && hideScoreboard.get();
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onTick() {
		cached = LunaCompat.sidebar(client);
	}

	/**
	 * 49-32차: 미리보기용 예시. 실제 서버 사이드바처럼 날짜 · 빈 줄 · 항목 묶음 · 주소 순으로
	 * 짜서, 설정을 바꿨을 때 어떻게 보일지 바로 가늠되게 한다.
	 */
	private LunaCompat.Sidebar sample() {
		java.time.LocalDate today = java.time.LocalDate.now();
		String date = String.format(java.util.Locale.ROOT, "%02d/%02d/%02d",
				today.getYear() % 100, today.getMonthValue(), today.getDayOfMonth());
		List<LunaCompat.SidebarRow> rows = new ArrayList<>();
		// 49-87차(8-11, 사용자: "15~4가 아니라 9~0"): 오른쪽 숫자를 9부터 0까지 열 줄로
		sampleRow(rows, date + "  ", "3F21A", 0x808080, 0x808080, 9);
		sampleRow(rows, "등급: ", "다이아", 0xAAAAAA, 0x55FFFF, 8);
		sampleRow(rows, "코인: ", "12,450", 0xAAAAAA, 0xFFD75C, 7);
		sampleBlank(rows, 6);
		sampleRow(rows, "처치: ", "128", 0xAAAAAA, 0xFFFFFF, 5);
		sampleRow(rows, "죽음: ", "17", 0xAAAAAA, 0xFFFFFF, 4);
		sampleRow(rows, "연승: ", "6", 0xAAAAAA, 0x55FF55, 3);
		sampleBlank(rows, 2);
		sampleRow(rows, "접속자: ", "1,204명", 0xAAAAAA, 0xFFFFFF, 1);
		sampleRow(rows, "play.nova.kr", "", 0xFFFF55, 0xFFFF55, 0);
		return new LunaCompat.Sidebar(LunaCompat.coloredText("NOVA 네트워크", 0xFFD75C), rows);
	}

	/** 왼쪽 이름 + (오른쪽 빨간 숫자 자리에 들어갈) 값. 값은 이름 뒤에 붙여 서버처럼 보이게 한다. */
	private void sampleRow(List<LunaCompat.SidebarRow> rows, String label, String value,
			int labelColor, int valueColor, int score) {
		Text name = value.isEmpty()
				? LunaCompat.coloredText(label, labelColor)
				: LunaCompat.join(LunaCompat.coloredText(label, labelColor),
						LunaCompat.coloredText(value, valueColor));
		rows.add(new LunaCompat.SidebarRow(name, LunaCompat.coloredText(String.valueOf(score), 0xFF5555), score));
	}

	private void sampleBlank(List<LunaCompat.SidebarRow> rows, int score) {
		rows.add(new LunaCompat.SidebarRow(LunaCompat.coloredText(" ", 0xFFFFFF),
				LunaCompat.coloredText(String.valueOf(score), 0xFF5555), score));
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		LunaCompat.Sidebar sb;
		if (isPreview()) {
			sb = sample();
		} else {
			if (hideScoreboard.get() || LunaCompat.isHudHidden(client)) {
				return;
			}
			sb = cached;
			if (sb == null || sb.rows().isEmpty()) {
				return;
			}
		}
		var tr = client.textRenderer;
		int titleW = LunaCompat.textWidth(tr, sb.title()); // 49-36차: 1.15.2엔 getWidth(Text) 없음
		int maxW = titleW;
		for (LunaCompat.SidebarRow r : sb.rows()) {
			int w = LunaCompat.textWidth(tr, r.name()) + (!hideNumbers.get() ? 6 + LunaCompat.textWidth(tr, r.score()) : 0);
			maxW = Math.max(maxW, w);
		}
		int panelW = maxW + PAD * 2;
		int panelH = ROW_H + 1 + sb.rows().size() * ROW_H + 2;
		int x, y;
		if (isPreviewBoxed()) {
			// 49-87차(8-11): 설정 미리보기에서는 화면 실제 위치가 아니라 미리보기 상자 안에 그린다.
			// 예전엔 우상단 실제 자리에 그려서 설정 패널·탭과 겹쳐 보였다(사용자: "제목이랑 내용 겹침").
			x = previewCenterX() - panelW / 2;
			y = previewCenterY() - panelH / 2;
		} else {
			x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), panelW);
			y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), panelH);
		}
		int r = Math.max(0, Math.min(6, radius.get()));

		if (background.get()) {
			kr.lunaslight.mod.gui.LunaDraw.roundRect(context, x, y, panelW, panelH, r, backgroundColor.getArgb());
		}
		if (titleBar.get()) {
			// 제목 줄: 위쪽만 둥글게(아래는 직각으로 본문과 이어짐).
			// 49-195차(사용자: "스코어보드 미리보기 제목이랑 바로 밑 내용이 겹침"): 예전엔 r만큼 더 길게 그린 띠의 아래를
			// 투명(0)으로 칠해 지우려 했는데 fill은 덮어쓰기가 아니라 섞기라 안 지워져서 제목 띠가 첫 줄까지 덮였다.
			kr.lunaslight.mod.gui.LunaDraw.roundRectTop(context, x, y, panelW, ROW_H + 1, r, titleColor.getArgb());
		}
		// 제목(가운데)
		int titleX = x + (panelW - titleW) / 2;
		context.drawText(tr, sb.title(), titleX, y + 1, 0xFFFFFFFF, true);
		int ry = y + ROW_H + 1 + 1;
		for (LunaCompat.SidebarRow row : sb.rows()) {
			context.drawText(tr, row.name(), x + PAD, ry, 0xFFFFFFFF, true);
			if (!hideNumbers.get()) {
				int sw = LunaCompat.textWidth(tr, row.score());
				context.drawText(tr, row.score(), x + panelW - PAD - sw, ry, 0xFFFFFFFF, true);
			}
			ry += ROW_H;
		}
	}
}
