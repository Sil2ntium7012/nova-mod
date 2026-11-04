package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-125차(사용자: "낚시 미끼가 물었을 때 소리 울리는 기능"): <b>낚시 알림</b>.
 *
 * <p>낚시찌(플레이어의 {@code fishHook})가 물고기를 물면 소리를 한 번 낸다. 물었는지는 찌 엔티티의
 * {@code caughtFish} 깃발(서버가 데이터 트래커로 내려주는 값 - 1.15.2~26.x 전부 있음)을 리플렉션으로 읽고,
 * 못 읽는 버전에서는 찌가 갑자기 아래로 확 잠기는 순간(세로 속도 -0.2 미만)으로 대신 판단한다.
 * 한 번 문 뒤에는 찌를 다시 던질 때까지 다시 울리지 않는다.
 *
 * <p>소리는 클라이언트에서만 나므로 서버에 아무것도 보내지 않는다.
 */
public class FishingAlertModule extends Module {

	public enum Sound {
		BELL("종", "BLOCK_NOTE_BLOCK_BELL", 1.8f),
		PLING("핑", "BLOCK_NOTE_BLOCK_PLING", 1.6f),
		XP("경험치", "ENTITY_EXPERIENCE_ORB_PICKUP", 1.2f),
		LEVEL("레벨 업", "ENTITY_PLAYER_LEVELUP", 1.4f);

		private final String label;
		final String field;
		final float pitch;

		Sound(String label, String field, float pitch) {
			this.label = label;
			this.field = field;
			this.pitch = pitch;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Sound> sound = register(new EnumSetting<>(
			"sound", "소리", "물었을 때 낼 소리입니다.", Sound.BELL, Sound.class));

	private final BooleanSetting flash = register(new BooleanSetting(
			"flash", "글자 표시", "물었을 때 '물었다!'를 잠깐 띄웁니다.", true));

	// 49-516차(사용자: "낚였다 메시지를 낚싯대 위에 할지 화면에 할지 고르게, 화면에서도 움직일 수 있게")
	public enum Place {
		SCREEN("화면"),
		BOBBER("낚시찌 위");

		private final String label;

		Place(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Place> place = register(new EnumSetting<>(
			"place", "표시 자리", "'물었다!'를 화면(HUD 편집기에서 옮길 수 있음)에 띄울지, 물고기가 문 낚시찌 위에 띄울지 고릅니다.",
			Place.SCREEN, Place.class));

	/** 예전 자리(화면 가운데 아래 64px)와 같게: 상자 높이 14, 아래에서 53. */
	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "[표시 자리]가 화면일 때 '물었다!'가 뜨는 자리입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 53)));

	private boolean biting;
	private boolean hadHook;
	private long bitAtMs;
	/** 물었을 때 찌 자리(찌 위 표시용, 찌를 거둔 뒤에도 사라질 때까지 그 자리). */
	private double hookX, hookY, hookZ;
	private boolean hookPosSet;

	public FishingAlertModule() {
		super("fishing_alert", "낚시 알림", ModuleCategory.FEATURE, "미끼를 물면 소리로 알림");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xB2090A0C, false, 0xB40B0C0E, kr.lunaslight.mod.module.Module.HudShape.FOLLOW);
		position.disabledWhen(() -> place.get() == Place.BOBBER);
	}

	@Override
	protected void onDisable() {
		biting = false;
		hadHook = false;
		bitAtMs = 0;
		hookPosSet = false;
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.level == null) {
			biting = false;
			hadHook = false;
			return;
		}
		Object hook = LunaCompat.getFieldValue(client.player, "fishHook", "fishing");
		if (hook == null) {
			// 찌를 거둠 - 다음 던지기부터 다시 알림
			biting = false;
			hadHook = false;
			return;
		}
		if (!hadHook) {
			hadHook = true;
			biting = false;
		}
		boolean now = isBiting(hook);
		if (now) {
			// 찌가 물속에서 움직이니 무는 동안은 자리를 따라간다
			net.minecraft.world.phys.Vec3 hp = LunaCompat.getPos(hook);
			if (hp != null) {
				hookX = hp.x;
				hookY = hp.y;
				hookZ = hp.z;
				hookPosSet = true;
			}
		}
		if (now && !biting) {
			bitAtMs = System.currentTimeMillis();
			Sound s = sound.get();
			LunaCompat.playUiSound(client, s.field, s.pitch);
		}
		biting = now;
	}

	/** 찌가 물고기를 물었는지. caughtFish 깃발 → 없으면 찌가 확 잠기는 속도로 판단. */
	private boolean isBiting(Object hook) {
		try {
			Object v = LunaCompat.getFieldValue(hook, "caughtFish", "biting");
			if (v instanceof Boolean b) {
				return b;
			}
		} catch (Throwable ignored) {
		}
		try {
			Object vel = LunaCompat.callNoArg(hook, "getVelocity");
			Object y = vel == null ? null : LunaCompat.getFieldValue(vel, "y");
			if (y instanceof Number n) {
				return n.doubleValue() < -0.2;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		// 49-347차(사용자: "낚시 알림도 미리보기좀"): 미리보기 칸에선 월드 밖이어도 '물었다!'를 칸 가운데에 보여 준다
		if (!flash.get() || client == null || (!isPreview() && client.player == null)) {
			return;
		}
		long age = System.currentTimeMillis() - bitAtMs;
		if (isPreview()) {
			age = 0;
		} else if (bitAtMs == 0 || age > 1500) {
			return;
		}
		float alpha = age < 1100 ? 1f : 1f - (age - 1100) / 400f;
		String text = "물었다!";
		int tw = LunaCompat.getTextWidth(client.font, text);
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
		int bw = tw + 12, bh = 14;
		if (isPreview() && isPreviewBoxed()) {
			// 설정 화면 미리보기 칸: 가운데
			drawLabel(context, previewCenterX() - bw / 2, previewCenterY() - 7, bw, bh, text, alpha, a);
			return;
		}
		if (place.get() == Place.BOBBER && !isPreview()) {
			// 49-516차: 찌 위 - 이름표처럼 거리에 따라 작아진다. 화면 밖이거나 창이 열려 있으면 안 그린다.
			if (!hookPosSet || LunaCompat.screenOf(client) != null) {
				return;
			}
			LunaProjection proj = LunaProjection.capture(client);
			double[] out = new double[3];
			if (proj == null || !proj.project(hookX, hookY + 0.6, hookZ, out)) {
				return;
			}
			if (out[0] < -60 || out[0] > proj.sw + 60 || out[1] < -60 || out[1] > proj.sh + 60) {
				return;
			}
			float sc = (float) (0.03 * proj.sh / (2.0 * proj.tanHalf * Math.max(0.5, out[2])));
			sc = Math.max(0.5f, Math.min(1.6f, sc));
			LunaCompat.guiPush(context);
			try {
				LunaCompat.guiTranslate(context, (float) out[0], (float) out[1]);
				LunaCompat.guiScale(context, sc, sc);
				drawLabel(context, -bw / 2, -bh, bw, bh, text, alpha, a);
			} finally {
				LunaCompat.guiPop(context);
			}
			return;
		}
		// 화면(또는 HUD 편집기): [위치] 설정 자리
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), bw);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), bh);
		drawLabel(context, x, y, bw, bh, text, alpha, a);
	}

	private void drawLabel(GuiGraphicsExtractor context, int x, int y, int bw, int bh, String text, float alpha, int a) {
		drawHudBox(context, x, y, bw, bh, alpha);   // 49-156차: 이 기능의 [배경] 설정대로
		LunaCompat.drawHudText(context, client.font, text, x + 6, y + 3, (a << 24) | 0xFFD75E);
	}

	@Override
	public boolean hasPreview() {
		return true;   // 49-347차
	}
}
