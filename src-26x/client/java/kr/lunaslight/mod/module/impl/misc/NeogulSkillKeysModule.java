package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulSkillKeys;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-431차(사용자: "스킬 키 세팅 호환해 줘 - 가능하면 예쁘게"): <b>너굴 스킬 키</b>. 키 처리는 {@link NeogulSkillKeys}(NG-SkillKeydown 대신)가
 * 늘 하고, 이 기능은 던전 스킬 모드일 때 핫바 위에 1~4번 스킬 키캡(키 + 스킬 이름)을 띄운다. 누르면 키캡이 눌리며 반짝인다.
 * 서버가 키를 알려 준 때(F1~F4, R F C V, 직접 고른 키)만 뜬다 - 숫자키(2~5) 방식은 핫바와 겹치므로 안 띄운다.
 */
public class NeogulSkillKeysModule extends Module {

	/** 너굴마을 /스킬키세팅의 1~4번(돌진, 회전, 사슬 모으기, 큐브). */
	private static final String[] NAMES = {"돌진", "회전", "모으기", "큐브"};
	private static final int CAP = 20;
	private static final int GAP = 4;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 66)));

	public NeogulSkillKeysModule() {
		super("neogul_skill_keys", "너굴 스킬 키", ModuleCategory.SERVER, "던전 스킬 키 세팅 | 따로 모드 없이 동작");
		serverGroup("너굴마을");
		defaultEnabled(true);
		enableHudStyle(0xC80E1014, false, 0xB40B0C0E, Module.HudShape.FOLLOW);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
		if (client == null) {
			return;
		}
		boolean preview = isPreview();
		if (!preview && !NeogulSkillKeys.skillMode()) {
			return;
		}
		int[] keys = new int[4];
		for (int i = 0; i < 4; i++) {
			keys[i] = preview ? new int[]{82, 70, 67, 86}[i] : NeogulSkillKeys.keyOf(i + 1);
		}
		int w = CAP * 4 + GAP * 3 + 8;
		int h = CAP + 14;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int x = isPreviewBoxed() ? previewCenterX() - w / 2 : position.get().resolveX(sw, w);
		int y = isPreviewBoxed() ? previewCenterY() - h / 2 : position.get().resolveY(sh, h);
		drawHudBox(ctx, x, y, w, h);
		long now = System.currentTimeMillis();
		int acc = LunaDraw.ACCENT;
		for (int i = 0; i < 4; i++) {
			int cx = x + 4 + i * (CAP + GAP);
			int cy = y + 3;
			long since = now - NeogulSkillKeys.castAt(i + 1);
			float flash = !preview && since >= 0 && since < 350 ? 1f - since / 350f : 0f;
			int press = flash > 0.5f ? 1 : 0;
			// 키캡: 아래 턱 + 윗면
			LunaDraw.roundRect(ctx, cx, cy + 2, CAP, CAP - 2, 4, 0xC0000000);
			int face = LunaDraw.lerpColor(0xFF2B3038, LunaDraw.withAlpha(acc, 0xFF), 0.15f + 0.6f * flash);
			LunaDraw.roundRect(ctx, cx, cy + press, CAP, CAP - 3, 4, face);
			LunaDraw.roundRect(ctx, cx + 1, cy + press + 1, CAP - 2, 1, 1, 0x30FFFFFF);
			String name = keys[i] < 0 ? "-" : LunaCompat.keySymName(keys[i]);
			if (LunaDraw.width(client.font, name) > CAP - 4) {
				name = name.substring(0, Math.min(2, name.length()));
			}
			LunaDraw.textCentered(ctx, client.font, name, cx + CAP / 2, cy + press + (CAP - 3 - 8) / 2 + 1, 0xFFFFFFFF);
			// 스킬 이름(작게)
			String label = NAMES[i];
			int lw = LunaDraw.width(client.font, label);
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiTranslate(ctx, cx + CAP / 2f - lw * 0.35f, cy + CAP + 1);
				LunaCompat.guiScale(ctx, 0.7f, 0.7f);
				LunaDraw.text(ctx, client.font, label, 0, 0, flash > 0 ? 0xFFFFFFFF : 0xFFB8BEC8);
			} finally {
				LunaCompat.guiPop(ctx);
			}
		}
	}
}
