package kr.lunaslight.mod.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * 49-7차: 성능 진단서용 지표 수집기. ModuleManager가 매 틱 tick(), 매 프레임 frame()을 불러줌.
 * 수집 비용을 거의 0으로 유지(초당 1회만 샘플링, 프레임 훅은 nanoTime 뺄셈 하나).
 *
 * 정직성 메모: JVM 구조상 "모드별 메모리 사용량"은 정확히 귀속시킬 수 없음(힙은 공유되고
 * 객체에 소유자 표시가 없음) - 그래서 전체 지표(FPS/메모리/핑/서버 틱/스파이크)를 근거로
 * 원인 후보를 진단하고 해결책을 추천하는 방식으로 설계. 허수 데이터는 만들지 않는다.
 */
public final class LunaPerf {
	private LunaPerf() {
	}

	// ---- 프레임 ----
	private static long lastFrameNano;
	private static final Deque<Long> SPIKES_MS = new ArrayDeque<>(); // 100ms 초과 프레임의 발생 시각
	private static volatile double avgFrameMs;

	// ---- 1초 샘플 ----
	private static long lastSampleMs;
	private static int tickCount;
	private static final Deque<Integer> FPS_HISTORY = new ArrayDeque<>(); // 최근 60초
	private static final Deque<Float> MEM_HISTORY = new ArrayDeque<>();   // 최근 60초 사용률
	/** 49-32차: 경고를 올리기 전에 최소 이만큼(초)은 평균을 재고 본다. */
	private static final int WARN_MIN_SAMPLES = 15;
	private static volatile int fps = -1;
	private static volatile long usedMemMb, maxMemMb;
	private static volatile int ping = -1;
	private static volatile float serverTickRatio = 1f; // 1.0 = 정상, 낮을수록 서버 렉
	private static long lastWorldTime = -1;
	private static long lastWorldSampleMs;
	private static volatile int modCount = -1;

	public static void frame() {
		long now = System.nanoTime();
		if (lastFrameNano != 0) {
			double ms = (now - lastFrameNano) / 1_000_000.0;
			avgFrameMs = avgFrameMs * 0.95 + ms * 0.05;
			if (ms > 100) {
				synchronized (SPIKES_MS) {
					SPIKES_MS.addLast(System.currentTimeMillis());
				}
			}
		}
		lastFrameNano = now;
	}

	public static void tick(Minecraft client) {
		tickCount++;
		long now = System.currentTimeMillis();
		if (now - lastSampleMs < 1000) {
			return;
		}
		lastSampleMs = now;

		// FPS (리플렉션 - getCurrentFps는 1.15~1.21.11 전부 존재 확인)
		try {
			Object r = LunaCompat.getMethodCompat(client.getClass(), "getCurrentFps").invoke(client);
			if (r instanceof Integer i) {
				fps = i;
				FPS_HISTORY.addLast(i);
				if (maxMemMb > 0) {
					MEM_HISTORY.addLast(usedMemMb / (float) maxMemMb);
					while (MEM_HISTORY.size() > 60) {
						MEM_HISTORY.removeFirst();
					}
				}
				while (FPS_HISTORY.size() > 60) {
					FPS_HISTORY.removeFirst();
				}
			}
		} catch (Throwable ignored) {
		}

		// 메모리
		Runtime rt = Runtime.getRuntime();
		usedMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
		maxMemMb = rt.maxMemory() / (1024 * 1024);

		// 핑(멀티에서만) - networkHandler.getPlayerListEntry(uuid).getLatency()
		ping = -1;
		try {
			if (client.player != null && !client.isLocalServer()) {
				Object nh = LunaCompat.getMethodCompat(client.getClass(), "getNetworkHandler").invoke(client);
				if (nh != null) {
					Object entry = LunaCompat.getMethodCompat(nh.getClass(), "getPlayerListEntry", java.util.UUID.class)
							.invoke(nh, client.player.getUUID());
					if (entry != null) {
						Object lat = LunaCompat.getMethodCompat(entry.getClass(), "getLatency").invoke(entry);
						if (lat instanceof Integer i) {
							ping = i;
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}

		// 서버 틱 진행률: 월드 시간이 실시간 대비 얼마나 흘렀는지(5초 창)
		try {
			if (client.level != null) {
				long wt = client.level.getGameTime();
				if (lastWorldTime >= 0 && now - lastWorldSampleMs >= 5000) {
					double expectedTicks = (now - lastWorldSampleMs) / 50.0;
					double actualTicks = wt - lastWorldTime;
					if (expectedTicks > 0 && actualTicks >= 0) {
						serverTickRatio = (float) Math.max(0, Math.min(1.2, actualTicks / expectedTicks));
					}
					lastWorldTime = wt;
					lastWorldSampleMs = now;
				} else if (lastWorldTime < 0) {
					lastWorldTime = wt;
					lastWorldSampleMs = now;
				}
			} else {
				lastWorldTime = -1;
				serverTickRatio = 1f;
			}
		} catch (Throwable ignored) {
		}

		// 모드 수(1회)
		if (modCount < 0) {
			try {
				modCount = net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size();
			} catch (Throwable ignored) {
				modCount = -2;
			}
		}

		// 스파이크 60초 창 유지
		synchronized (SPIKES_MS) {
			Long first;
			while ((first = SPIKES_MS.peekFirst()) != null && now - first > 60_000) {
				SPIKES_MS.removeFirst();
			}
		}
	}

	// ---- 조회 ----
	public static int fps() {
		return fps;
	}

	public static int avgFps() {
		if (FPS_HISTORY.isEmpty()) {
			return fps;
		}
		int sum = 0;
		for (int v : FPS_HISTORY) {
			sum += v;
		}
		return sum / FPS_HISTORY.size();
	}

	/** 최근 기록된 메모리 사용률 평균(0~1). 기록이 없으면 0. */
	public static float avgMemPct() {
		if (MEM_HISTORY.isEmpty()) {
			return 0f;
		}
		float sum = 0f;
		for (float v : MEM_HISTORY) {
			sum += v;
		}
		return sum / MEM_HISTORY.size();
	}

	public static long usedMemMb() {
		return usedMemMb;
	}

	public static long maxMemMb() {
		return maxMemMb;
	}

	public static int ping() {
		return ping;
	}

	public static float serverTickRatio() {
		return serverTickRatio;
	}

	public static int spikeCountLastMinute() {
		synchronized (SPIKES_MS) {
			return SPIKES_MS.size();
		}
	}

	public static double avgFrameMs() {
		return avgFrameMs;
	}

	public static int modCount() {
		return modCount;
	}

	/** 49-166차(진단 대시보드): 최근 60초 FPS(오래된 것부터). 기록이 없으면 빈 배열. */
	public static int[] fpsHistory() {
		synchronized (FPS_HISTORY) {
			int[] out = new int[FPS_HISTORY.size()];
			int i = 0;
			for (int v : FPS_HISTORY) {
				out[i++] = v;
			}
			return out;
		}
	}

	// ---- 진단 ----
	public record Issue(int level, String title, String value, String advice) {
	}

	/** level: 0 좋음 / 1 주의 / 2 문제. 각 지표를 값+진단+권고로. */
	public static List<Issue> diagnose(Minecraft client) {
		List<Issue> out = new ArrayList<>();

		int af = avgFps();
		if (af >= 0) {
			// 49-32차(사용자: "특정 값 밑으로 내려가면 바로 경고 날리지 말고 평균을 조금 더 재고"):
			// 표본이 15초어치도 안 쌓였으면(월드 입장 직후·순간 렉) 아직 판단하지 않는다.
			int lvl = FPS_HISTORY.size() < WARN_MIN_SAMPLES ? 0 : (af < 45 ? 2 : (af < 75 ? 1 : 0));
			out.add(new Issue(lvl, "FPS", af + " fps (평균)"
					+ (FPS_HISTORY.size() < WARN_MIN_SAMPLES ? " · 측정 중" : ""),
				lvl == 0 ? "쾌적함"
					: "렌더 거리·구름·입자를 낮춰보세요. 스카이팩터리류 모드팩이면 소듐(Sodium) 계열 최적화 모드가 큰 차이를 만듭니다"));
		}

		if (maxMemMb > 0) {
			float pct = avgMemPct() > 0 ? avgMemPct() : usedMemMb / (float) maxMemMb;
			int lvl = MEM_HISTORY.size() < WARN_MIN_SAMPLES ? 0 : (pct > 0.85f ? 2 : (pct > 0.7f ? 1 : 0));
			String advice;
			if (maxMemMb < 2600) {
				advice = "할당된 메모리 자체가 작습니다(" + (maxMemMb / 1024f >= 1 ? String.format("%.1fGB", maxMemMb / 1024f) : maxMemMb + "MB")
					+ "). 런처 설정에서 4~6GB 할당을 권장";
				lvl = Math.max(lvl, 1);
			} else if (lvl == 2) {
				advice = "메모리가 거의 꽉 찼습니다 - 할당을 늘리거나(런처 설정) 리소스팩/모드를 줄여보세요";
			} else if (lvl == 1) {
				advice = "메모리 사용이 높은 편 - 렉 스파이크가 잦다면 할당을 1~2GB 늘려보세요";
			} else {
				advice = "여유 있음";
			}
			out.add(new Issue(lvl, "메모리", usedMemMb + " / " + maxMemMb + " MB", advice));
		}

		int spikes = spikeCountLastMinute();
		int slvl = spikes >= 6 ? 2 : (spikes >= 2 ? 1 : 0);
		out.add(new Issue(slvl, "프레임 스파이크", "최근 1분 " + spikes + "회",
			slvl == 0 ? "안정적" : "순간 멈칫거림 - 청크 로딩/GC가 원인일 가능성. 메모리 할당을 늘리거나 렌더 거리를 줄여보세요"));

		if (ping >= 0) {
			int plvl = ping >= 150 ? 2 : (ping >= 80 ? 1 : 0);
			out.add(new Issue(plvl, "핑(서버 지연)", ping + " ms",
				plvl == 0 ? "좋음" : "인터넷 상태 또는 서버와의 물리적 거리 문제 - 컴퓨터 성능과는 무관합니다"));
		}

		if (client != null && client.level != null && !client.isLocalServer()) {
			float tr = serverTickRatio;
			int tlvl = tr < 0.9f ? 2 : (tr < 0.97f ? 1 : 0);
			out.add(new Issue(tlvl, "서버 틱 진행률", Math.round(tr * 100) + "%",
				tlvl == 0 ? "서버 정상" : "서버(맵) 자체가 느립니다 - 내 컴퓨터 문제가 아니라 서버 렉입니다"));
		}

		if (modCount > 0) {
			int mlvl = modCount > 60 ? 1 : 0;
			out.add(new Issue(mlvl, "설치된 모드", modCount + "개",
				mlvl == 0 ? "적정" : "모드가 많은 편 - 시작 시간과 메모리에 영향. 안 쓰는 모드 정리를 권장"));
		}

		return out;
	}
}
