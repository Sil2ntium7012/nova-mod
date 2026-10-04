package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaNowPlaying;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * 49-74차(4-5): <b>듣고 있는 노래</b> - 지금 나오는 곡을 화면 구석에 한 줄.
 *
 * <p>사용자 요청 4-5: "듣고 있는 노래 표시".
 *
 * <h3>어디서 오는 정보인가</h3>
 * <b>런처가 윈도우에 물어본다.</b> 곡 정보는 운영체제(윈도우 미디어 세션)가 갖고 있고, Spotify·
 * 유튜브(크롬/엣지)·윈도우 미디어 플레이어 같은 프로그램이 거기에 올린다. 자바에는 그걸 물어볼 길이
 * 없어서, 런처가 대신 물어보고 게임 폴더에 한 줄 적어 두면 모드가 그걸 읽는다
 * ({@link LunaNowPlaying} 주석에 이유를 다 적어 뒀다).
 *
 * <h3>안 뜨는 경우 - 고장이 아니다</h3>
 * <ul>
 *   <li><b>전용 런처로 켜지 않았다</b> - 적어 줄 사람이 없다.</li>
 *   <li><b>그 프로그램이 윈도우에 곡 정보를 안 올린다</b> - 일부 옛 플레이어가 그렇다.
 *       윈도우 볼륨 조절기 위에 곡 제목이 뜨는 프로그램이면 여기에도 뜬다.</li>
 *   <li><b>아무것도 재생 중이 아니다</b> - 이때는 <b>일부러 아무것도 그리지 않는다.</b>
 *       빈 상자를 띄워 두면 화면만 어지럽다.</li>
 * </ul>
 *
 * <h3>성능</h3>
 * 프레임마다 하는 일은 <b>문자열 두 개를 읽어 그리는 것</b>뿐이다. 파일 읽기는 2초에 한 번,
 * 그것도 <b>다른 스레드</b>에서 돈다. 꺼져 있으면 비용이 0이다.
 */
public class NowPlayingModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "노래 표시가 뜨는 자리입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_LEFT, 4, 4)));
	private final BooleanSetting artist = register(new BooleanSetting(
			"artist", "가수", "곡 제목 뒤에 가수(채널) 이름을 같이 보여 줍니다.", true));
	// 49-195차(사용자: "노래 멈춰도 표시가 기본 기능으로"): [멈춰도 표시] 설정을 빼고 일시정지 중에도 늘 보여 준다.
	private final IntSetting maxWidth = register(new IntSetting(
			"max_width", "최대 너비", "이보다 길면 제목을 잘라냅니다(픽셀).", 140, 80, 300, 10).unit("px"));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "곡 제목의 색입니다.", 0xFFECECF1));
	// 49-177차(사용자: "멜론 스포티파이 유튜브뮤직 유튜브 숲 이런 플랫폼별 색으로 입혀주라")
	private final BooleanSetting platformColor = register(new BooleanSetting(
			"platform_color", "플랫폼 색", "멜론, Spotify, YouTube Music, YouTube는 막대와 윤곽선을 그 플랫폼 색으로 표시합니다(배경이 꺼져 있으면 막대만).", true));

	// 49-184차(사용자: "노래 표시에도 그림자 넣어주고"): 상자 아래 그림자 + 막대에도 글자처럼 1px 그림자
	private final BooleanSetting shadow = register(new BooleanSetting(
			"shadow", "그림자", "음악 막대에 그림자를 넣습니다.", true).style());

	public NowPlayingModule() {
		super("now_playing", "듣고 있는 노래", ModuleCategory.HUD,
				"지금 나오는 곡을 화면 구석에");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xD00E1116, true, (kr.lunaslight.mod.util.LunaTheme.DEFAULT_ACCENT & 0x00FFFFFF) | 0x8C000000, kr.lunaslight.mod.module.Module.HudShape.FOLLOW);
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		String song;
		String who;
		String src;
		if (isPreview()) {
			song = "Weightless";
			who = "Marconi Union";
			src = "spotify";
		} else {
			LunaNowPlaying.poll();
			if (!LunaNowPlaying.has()) {
				return;
			}
			song = LunaNowPlaying.title();
			who = LunaNowPlaying.artist();
			src = LunaNowPlaying.source();
		}
		int brand = platformColor.get() ? brandColor(src) : 0;
		String text = song + (artist.get() && who != null && !who.isEmpty() ? " §7- " + who : "");
		text = clip(text, maxWidth.get());

		int tw = LunaCompat.getTextWidth(client.textRenderer, text);
		// 49-182차(사용자: "움직이는 막대와 노래 제목 사이에 띄어쓰기 하나"): 막대(x+5~x+13) 뒤에 글꼴의 띄어쓰기 한 칸만큼 띄운다
		int gap = Math.max(3, LunaCompat.getTextWidth(client.textRenderer, " "));
		int textX = 13 + gap;
		int w = tw + textX + 6;
		int h = 16;
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), h);
		// 49-179차(사용자: "배경이 테두리 밖으로 튀어나가고 배경 없음으로 해도 테두리가 남음"): 따로 그리던 색 테두리와
		// 네모 빛을 없애고, 이 기능의 배경 상자 윤곽선 색만 플랫폼 색으로 바꾼다 - 모양/둥글기/배경 없음을 그대로 따른다.
		hudOutlineOverride = brand != 0 ? LunaDraw.withAlpha(brand, 0xC8) : 0;
		try {
			// 49-232차(사용자: "듣고 있는 음악 배경에 이상한 검정 배경이 하나 더 있어"): 상자 바깥 그림자(큰 어두운 판 두 장) 삭제
			drawHudBox(context, x, y, w, h);   // 49-156차: 이 기능의 [배경] 설정대로
		} finally {
			hudOutlineOverride = 0;
		}
		// 음표 대신 막대 세 개 - 글꼴에 없는 글자를 쓰면 버전마다 네모로 깨진다
		boolean moving = isPreview() || LunaNowPlaying.playing();
		if (shadow.get()) {
			// 글자 그림자와 같은 방식: 1px 오른쪽 아래에 어둡게 한 번 더
			drawBars(context, x + 6, y + 5, moving, 0xFF000000 | darken(brand != 0 ? brand : LunaDraw.ACCENT));
		}
		drawBars(context, x + 5, y + 4, moving, brand != 0 ? brand : LunaDraw.ACCENT);
		LunaCompat.drawHudText(context, client.textRenderer, text, x + textX, y + 4, textColor.getArgb());
	}

	/** 작은 이퀄라이저 막대. 재생 중이면 높이가 움직이고, 멈춰 있으면 낮게 고정된다. */
	/**
	 * 49-177차: 플랫폼 색(각 회사의 대표 색). SOOP은 새 로고의 파란색(공개된 색 코드가 없어 로고에서 뽑은 근사값).
	 * YouTube Music과 YouTube는 둘 다 빨강이라, YouTube Music은 살짝 분홍 쪽으로 옮겨 구분한다.
	 */
	private static int brandColor(String src) {
		if (src == null) {
			return 0;
		}
		return switch (src) {
			case "melon" -> 0xFF00CD3C;
			case "spotify" -> 0xFF1ED760;
			case "ytmusic" -> 0xFFFF2D55;
			case "youtube" -> 0xFFFF0000;
			case "soop" -> 0xFF2F6BFF;
			default -> 0;
		};
	}

	private void drawBars(DrawContext ctx, int x, int y, boolean moving, int color) {
		long t = System.currentTimeMillis();
		for (int i = 0; i < 3; i++) {
			int h;
			if (moving) {
				// 막대마다 다른 주기 - 진짜 소리를 분석하는 게 아니라 "재생 중"이라는 표시일 뿐이다
				double phase = (t % (520 + i * 190L)) / (double) (520 + i * 190L);
				h = 2 + (int) Math.round(5 * Math.abs(Math.sin(phase * Math.PI)));
			} else {
				h = 2;
			}
			ctx.fill(x + i * 3, y + 8 - h, x + i * 3 + 2, y + 8, color);
		}
	}

	/** 바닐라 글자 그림자처럼 색을 1/4 밝기로. */
	private static int darken(int argb) {
		int r = ((argb >> 16) & 0xFF) / 4, g = ((argb >> 8) & 0xFF) / 4, b = (argb & 0xFF) / 4;
		return (r << 16) | (g << 8) | b;
	}

	/** 폭에 맞춰 자른다(색 코드는 세지 않는다 - 잘린 자리에 "…"를 붙인다). */
	private String clip(String text, int limit) {
		if (LunaCompat.getTextWidth(client.textRenderer, text) <= limit) {
			return text;
		}
		String cut = text;
		while (cut.length() > 1 && LunaCompat.getTextWidth(client.textRenderer, cut + "…") > limit) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "…";
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
