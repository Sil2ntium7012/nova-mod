package kr.lunaslight.mod.util;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-50차: 음성 채팅(Simple Voice Chat) 연동 - 게임 쪽에서 읽는 상태 보관소.
 *
 * <p><b>왜 이렇게 나눠 뒀나.</b> 49-44차에 "보이스챗" 모듈을 아예 지웠던 이유는, 그게 설치 여부만
 * 보고 스위치 하나를 보여줄 뿐 실제로는 아무 일도 하지 않는 껍데기였기 때문이다(가짜 기능 노출 금지).
 * 이번엔 진짜 애드온으로 만든다 - SVC가 공개한 API(de.maxhenkel.voicechat:voicechat-api)를 컴파일
 * 의존성으로 붙이고, {@code fabric.mod.json}의 {@code "voicechat"} 진입점에 우리 플러그인을 등록한다.
 *
 * <p><b>이 파일에 SVC 타입이 하나도 없는 건 일부러다.</b> SVC API를 실제로 만지는 클래스는
 * {@code kr.lunaslight.mod.integration.LunaVoicechatPlugin} 딱 하나이고, 그 클래스는 SVC가 자기
 * 애드온 목록을 읽을 때만 로드된다. 그래서 SVC가 안 깔린 환경에서는 {@code de.maxhenkel...} 클래스를
 * 찾다 NoClassDefFoundError가 날 자리가 아예 없다 - HUD 모듈은 이 보관소만 보고 그린다.
 *
 * <p><b>스레드.</b> {@link #markAudio}는 SVC의 오디오 스레드에서 불린다(초당 20회×말하는 사람 수).
 * 읽는 쪽은 게임 스레드라 값은 전부 volatile/ConcurrentHashMap으로 둔다. 프레임마다 하는 일은
 * 작은 맵 조회 몇 번뿐이라 렉과 무관하다.
 */
public final class VoiceState {

	private VoiceState() {
	}

	/**
	 * SVC API로 가는 다리. 구현은 {@code LunaVoicechatPlugin} 한 곳뿐이고, 여기 시그니처에는
	 * 자바 표준 타입만 쓴다(이 인터페이스가 SVC 없이도 로드돼야 하므로).
	 */
	public interface Bridge {
		boolean muted();

		boolean disabled();

		boolean talking(UUID id);

		boolean whispering(UUID id);

		/** 지금 들어가 있는 그룹 이름(없으면 null). */
		String groupName();
	}

	/** 한 사람분 수신 상태 - 마지막으로 소리가 온 시각과 그때 세기. */
	private static final class Speaker {
		volatile long lastMs;
		volatile float level;
	}

	private static final Map<UUID, Speaker> SPEAKERS = new ConcurrentHashMap<>();

	private static volatile Bridge bridge;
	private static volatile boolean connected;
	private static Boolean installedCache;

	/** Simple Voice Chat 모드가 로드돼 있는지(서버 연결 여부와 별개). 결과는 한 번만 조회해 캐시. */
	public static boolean installed() {
		Boolean cached = installedCache;
		if (cached == null) {
			try {
				cached = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat");
			} catch (Throwable t) {
				cached = Boolean.FALSE;
			}
			installedCache = cached;
		}
		return cached;
	}

	public static void setBridge(Bridge value) {
		bridge = value;
	}

	public static void setConnected(boolean value) {
		connected = value;
		if (!value) {
			SPEAKERS.clear();
		}
	}

	/** 이 서버의 음성 채팅에 실제로 붙어 있는지(= HUD를 그릴 가치가 있는 상태인지). */
	public static boolean connected() {
		return connected && bridge != null;
	}

	/** 내 마이크가 꺼져 있는지(음소거 또는 음성 채팅 자체 끔). */
	public static boolean micOff() {
		Bridge b = bridge;
		if (b == null) {
			return false;
		}
		try {
			return b.muted() || b.disabled();
		} catch (Throwable t) {
			LunaCompat.warnOnce("voice:micOff", t);
			return false;
		}
	}

	/** 지금 그 사람이 말하는 중인지(SVC가 직접 알려주는 값 - 우리 오디오 집계보다 정확). */
	public static boolean talking(UUID id) {
		Bridge b = bridge;
		if (b == null || id == null) {
			return false;
		}
		try {
			return b.talking(id);
		} catch (Throwable t) {
			LunaCompat.warnOnce("voice:talking", t);
			return false;
		}
	}

	/** 속삭이는 중인지(소리가 작게 가는 모드 - HUD에서 이름을 흐리게 그린다). */
	public static boolean whispering(UUID id) {
		Bridge b = bridge;
		if (b == null || id == null) {
			return false;
		}
		try {
			return b.whispering(id);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 지금 들어가 있는 그룹 이름(없으면 null). */
	public static String groupName() {
		Bridge b = bridge;
		if (b == null) {
			return null;
		}
		try {
			String name = b.groupName();
			return name == null || name.isEmpty() ? null : name;
		} catch (Throwable t) {
			return null;
		}
	}

	// ==================== 오디오 세기(음량 막대) ====================

	/**
	 * SVC 오디오 스레드에서 부름 - 한 사람분 수신 패킷의 세기를 기록한다.
	 * 파형 전체를 훑지 않고 4칸씩 건너뛴 RMS만 낸다(패킷 하나가 960샘플이라 240번 곱셈).
	 */
	public static void markAudio(UUID id, short[] audio) {
		if (id == null) {
			return;
		}
		Speaker s = SPEAKERS.get(id);
		if (s == null) {
			s = new Speaker();
			Speaker prev = SPEAKERS.putIfAbsent(id, s);
			if (prev != null) {
				s = prev;
			}
		}
		s.lastMs = System.currentTimeMillis();
		// 붙잡았다 천천히 내려오게(막대가 매 패킷마다 덜덜 떨리지 않도록)
		s.level = Math.max(rms(audio), s.level * 0.7f);
	}

	private static float rms(short[] audio) {
		if (audio == null || audio.length == 0) {
			return 0f;
		}
		long sum = 0;
		int n = 0;
		for (int i = 0; i < audio.length; i += 4) {
			int v = audio[i];
			sum += (long) v * v;
			n++;
		}
		if (n == 0) {
			return 0f;
		}
		double value = Math.sqrt((double) sum / n) / 32768.0;
		return (float) Math.min(1.0, value * 5.0);
	}

	/** 그 사람의 지금 음량(0~1). 0.3초 넘게 소리가 없으면 0. */
	public static float level(UUID id) {
		Speaker s = id == null ? null : SPEAKERS.get(id);
		if (s == null) {
			return 0f;
		}
		return System.currentTimeMillis() - s.lastMs > 300 ? 0f : s.level;
	}

	/**
	 * 최근 {@code windowMs} 안에 소리가 온 사람들을 넘긴 리스트에 채운다(리스트는 재사용 - 프레임마다
	 * 새로 만들지 않게). 10초 넘게 조용한 항목은 이 참에 지운다.
	 */
	public static void collect(List<UUID> out, long windowMs) {
		out.clear();
		long now = System.currentTimeMillis();
		for (Map.Entry<UUID, Speaker> e : SPEAKERS.entrySet()) {
			long age = now - e.getValue().lastMs;
			if (age <= windowMs) {
				out.add(e.getKey());
			} else if (age > 10_000L) {
				SPEAKERS.remove(e.getKey());
			}
		}
	}

	/** 서버를 나갈 때(모듈이 월드 전환에서 부름). */
	public static void clear() {
		SPEAKERS.clear();
	}
}
