package kr.lunaslight.mod.util;

/**
 * 49-643차(다른 유저 기록: 서버를 옮길 때마다 25초 멈춤 - 렌더 스레드가 SoundEngine.shouldChangeDevice →
 * Library.isCurrentDeviceDisconnected → alcGetInteger(OpenAL)에서 서 있음. 바로 앞에 보이스챗이 다시 붙으며
 * "MicrophoneThread … OpenAL microphones"): 보이스챗 마이크 스레드가 OpenAL 장치를 여는 동안 바닐라의 "소리 장치 빠졌나"
 * 확인(1초마다)이 같은 OpenAL에 막혀 게임 전체가 선다(일부 사운드 장치/드라이버). 마이크 스레드가 살아 있는 동안은 그 확인을 건너뛴다
 * (그동안 이어폰을 뽑아도 소리 장치 자동 전환은 안 됨 - 보이스챗을 끄면 원래대로).
 */
public final class AudioDeviceGuard {
	private AudioDeviceGuard() {
	}

	private static Boolean voiceLoaded;
	private static boolean micAlive;
	private static boolean logged;

	/** 지금 바닐라 소리 장치 확인을 건너뛸지. */
	public static boolean skipDeviceCheck() {
		try {
			if (voiceLoaded == null) {
				voiceLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat");
			}
			if (!voiceLoaded) {
				return false;
			}
			// 바닐라는 이 확인을 1초에 한 번 부른다 - 그때마다 바로 본다(마이크가 막 켜진 순간을 놓치지 않게)
			micAlive = micThreadAlive();
			if (micAlive && !logged) {
				logged = true;
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 보이스챗이 켜져 있어 소리 장치 확인을 건너뜀(멈춤 방지)");
			}
			return micAlive;
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean micThreadAlive() {
		ThreadGroup g = Thread.currentThread().getThreadGroup();
		while (g.getParent() != null) {
			g = g.getParent();
		}
		Thread[] ts = new Thread[g.activeCount() + 16];
		int n = g.enumerate(ts, true);
		for (int i = 0; i < n; i++) {
			Thread t = ts[i];
			String nm = t == null ? null : t.getName();
			// 보이스챗이 붙는 순간(연결, 인증 스레드)부터 마이크 스레드가 도는 동안
			if (t != null && t.isAlive() && ("MicrophoneThread".equals(nm) || "VoiceChatConnectionThread".equals(nm)
					|| "VoiceChatAuthenticationThread".equals(nm))) {
				return true;
			}
		}
		return false;
	}
}
