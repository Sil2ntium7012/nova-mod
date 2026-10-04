package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

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
			"flash", "화면 표시", "물었을 때 화면 가운데 아래에 '물었다!'를 잠깐 띄웁니다.", true));

	private boolean biting;
	private boolean hadHook;
	private long bitAtMs;

	public FishingAlertModule() {
		super("fishing_alert", "낚시 알림", ModuleCategory.FEATURE, "미끼를 물면 소리로 알림");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xB2090A0C, false, 0xB40B0C0E, kr.lunaslight.mod.module.Module.HudShape.FOLLOW);
	}

	@Override
	protected void onDisable() {
		biting = false;
		hadHook = false;
		bitAtMs = 0;
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.world == null) {
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
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (!flash.get() || client == null || client.player == null) {
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
		int tw = LunaCompat.getTextWidth(client.textRenderer, text);
		int cx = WindowAccess.of(client).getScaledWidth() / 2;
		int y = WindowAccess.of(client).getScaledHeight() - 64;
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
		drawHudBox(context, cx - tw / 2 - 6, y - 3, tw + 12, 14, alpha);   // 49-156차: 이 기능의 [배경] 설정대로
		LunaCompat.drawHudText(context, client.textRenderer, text, cx - tw / 2, y, (a << 24) | 0xFFD75E);
	}
}
