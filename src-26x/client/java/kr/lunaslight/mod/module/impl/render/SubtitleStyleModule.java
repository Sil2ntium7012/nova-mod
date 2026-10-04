package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;
import kr.lunaslight.mod.util.SubtitleHook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-48차: 자막 꾸미기 - 마인크래프트 자막(설정 > 소리 > 자막 표시)의 <b>색만</b> 바꾼다.
 *
 * 위치·방향 화살표(◀ ▶)·사라지는 속도는 바닐라 그대로다(다시 그리지 않는다 - 왜 그렇게 했는지는
 * {@link SubtitleHook} 주석 참고). 바닐라 자막 자체가 꺼져 있으면 이 기능도 보일 게 없다.
 *
 * <p>49-53차(4-30): [화살표 색]을 빼고 [종류별 색]을 넣었다. 화살표만 따로 색을 정하는 건 쓸 일이
 * 거의 없는데 설정만 하나 차지했고, 정작 필요한 건 "무슨 소리인지 한눈에 보이는 것"이었다.
 * 1.15.2 자막은 번역 키가 없는 문자열이라 종류를 알 수 없어서 그 버전에서는 이 설정을 아예 만들지
 * 않는다(켜도 안 되는 설정을 보여 주지 않는다).
 */
public class SubtitleStyleModule extends Module {

	/** 1.15.2 자막은 번역 키 없는 문자열이라 종류를 알 수 없다. */
	private static final boolean KIND_SUPPORTED = LunaVersion.isWithin("1.16", null);

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", KIND_SUPPORTED
			? "자막 글자와 방향 화살표의 색입니다. [종류별 색]을 켜면 화살표와 분류되지 않은 소리에만 쓰입니다."
			: "자막 글자와 방향 화살표의 색입니다.", 0xFFFFFFFF));
	private final BooleanSetting byKind = KIND_SUPPORTED ? register(new BooleanSetting(
			"by_kind", "종류별 색", "적대 몹은 빨강, 중립(소/주민/트름/먹기)은 노랑, 플레이어는 파랑, 블록은 초록, 나머지는 회색입니다.", true)) : null;
	private final ColorSetting bgColor = register(new ColorSetting(
			"bg_color", "배경 색", "자막 뒤 상자의 색입니다(투명도 포함).", 0xA6090A0C));

	public SubtitleStyleModule() {
		super("subtitle_style", "자막 꾸미기", ModuleCategory.VIEW, "마인크래프트 자막의 색");
		defaultEnabled(true);   // 49-121차(사용자): 기본 활성화
	}

	@Override
	protected void onEnable() {
		push();
	}

	@Override
	protected void onDisable() {
		SubtitleHook.active = false;
	}

	@Override
	public void onTick() {
		push();
	}

	private void push() {
		SubtitleHook.active = isEnabled();
		SubtitleHook.text = textColor.getArgb();
		SubtitleHook.byKind = byKind != null && byKind.get();
		SubtitleHook.background = bgColor.getArgb();
	}

	// ==================== 미리보기 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (!isPreview()) {
			return;   // 실제 그리기는 바닐라가 한다(우리는 색만 바꿔 끼움)
		}
		boolean kind = byKind != null && byKind.get();
		// 49-76차(6-7 · 6-13): 화살표는 바닐라와 같은 < > 로(◀▶는 픽셀 글꼴에 없어 네모로 깨졌다),
		// 줄은 다섯 종류가 다 보이게.
		String[] rows = {"크리퍼가 쉭 소리를 냄", "트름", "플레이어가 다침", "문이 열림", "비가 내림"};
		int[] kinds = {SubtitleHook.HOSTILE, SubtitleHook.NEUTRAL, SubtitleHook.PLAYER, SubtitleHook.BLOCK, SubtitleHook.OTHER};
		int rowH = 11;
		int w = 0;
		for (String r : rows) {
			w = Math.max(w, LunaCompat.getTextWidth(client.font, r) + 24);
		}
		int x = previewX() + (previewW() - w) / 2;
		int y = previewY() + (previewH() - rows.length * rowH) / 2;
		for (int i = 0; i < rows.length; i++) {
			int ry = y + i * rowH;
			context.fill(x, ry, x + w, ry + rowH - 1, bgColor.getArgb());
			LunaCompat.drawHudText(context, client.font, "<", x + 3, ry + 1, textColor.getArgb());
			LunaCompat.drawHudText(context, client.font, rows[i],
				x + (w - LunaCompat.getTextWidth(client.font, rows[i])) / 2, ry + 1,
				kind ? kinds[i] : textColor.getArgb());
			LunaCompat.drawHudText(context, client.font, ">", x + w - 9, ry + 1, textColor.getArgb());
		}
	}
}
