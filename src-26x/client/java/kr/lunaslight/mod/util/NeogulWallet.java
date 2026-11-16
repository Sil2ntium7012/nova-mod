package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * 49-670차(사용자 사진: 왼쪽 위 돈 183,380,399 / 도토리 10,122 / 섬 - "디코봇 개인 채널에 유저들 인게임 돈도 보여지면 좋을 듯"):
 * 너굴마을 <b>소지금, 도토리</b> 읽기.
 *
 * <p>서버는 이 판을 보스바 글자(ng:hud 글꼴 개인 영역)로 그린다(neogul-hud-probe.txt):
 * <pre>
 *  돈:    (동전) … (판) 띄움 ~(0~9) (,) - 숫자 사이 (띄움)
 *  도토리: (도토리) … (판) 띄움 ~(0~9) (,)
 * </pre>
 * 판 글자 다음부터 숫자(판+1 ~ 판+10)와 쉼표(판+11)를 모으고, 숫자가 나온 뒤 다른 글자가 오면 끝.
 * 못 읽으면 -1(리소스팩이 바뀌어 글자가 달라졌을 때 - 그때는 probe로 다시 확인).
 */
public final class NeogulWallet {
	private NeogulWallet() {
	}

	public static volatile long money = -1;
	public static volatile long acorn = -1;
	public static volatile long readAt;

	private static final char MONEY_PANEL = '';
	private static final char ACORN_PANEL = '';
	private static final char GAP = '';

	/** 보스바를 읽어 갱신(1초에 한 번쯤 부르면 됨). */
	public static void read(Minecraft mc) {
		try {
			if (mc == null || mc.gui == null || !NeogulData.onNeogul(mc)) {
				return;
			}
			Object boss = LunaCompat.getFieldValue(mc.gui, "bossOverlay");
			Object events = boss == null ? null : LunaCompat.getFieldValue(boss, "events");
			if (!(events instanceof Map<?, ?> m)) {
				return;
			}
			for (Object ev : m.values()) {
				if (!(ev instanceof net.minecraft.world.BossEvent be) || be.getName() == null) {
					continue;
				}
				String s = be.getName().getString();
				long w = parse(s, '', MONEY_PANEL);
				long a = parse(s, '', ACORN_PANEL);
				if (w >= 0) {
					money = w;
					readAt = System.currentTimeMillis();
				}
				if (a >= 0) {
					acorn = a;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("wallet:read", t);
		}
	}

	/** icon 뒤 첫 panel 글자 다음 숫자. 없으면 -1. */
	static long parse(String s, char icon, char panel) {
		int i = s.indexOf(icon);
		if (i < 0) {
			return -1;
		}
		int p = s.indexOf(panel, i + 1);
		if (p < 0 || p - i > 8) {
			return -1;
		}
		long v = 0;
		int digits = 0;
		for (int k = p + 1; k < s.length() && digits < 16; k++) {
			char c = s.charAt(k);
			int d = c - panel - 1;
			if (d >= 0 && d <= 9) {
				v = v * 10 + d;
				digits++;
			} else if (c == panel + 11 || c == GAP) {
				// 쉼표, 숫자 사이 띄움
			} else if (digits > 0) {
				break;
			}
		}
		return digits > 0 ? v : -1;
	}
}
