package kr.lunaslight.mod.util;

/**
 * 49-154차(사용자: "리소스팩 다운로드하다 가끔 끝자락에서 안 되는 오류"): <b>화면 멈춤 기록</b>.
 *
 * <p>launcher.log를 보면 받기는 끝났고(바닐라 "Returning cached file" 또는 새로 받음), 리소스 새로고침의
 * 마지막 단계(아틀라스 생성) 뒤에 렌더 스레드가 <b>35~76초 동안 아무 줄도 안 남기고 멈췄다</b>
 * (playfarm.kr 큰 팩에서만, 한 번은 65초 만에 창을 닫음). 로그만으로는 그 사이 무엇을 하는지 알 수 없다.
 *
 * <p>그래서 감시 스레드를 하나 둔다. 렌더 스레드가 틱과 로딩창 그리기 때마다 {@link #beat()}로 신호를 주고,
 * 신호가 10초 넘게 끊기면 그 순간 렌더 스레드가 어디서 멈춰 있는지(스택)를 로그에 남긴다(10초, 25초, 60초에
 * 한 번씩, 멈춤 한 번에 최대 세 번). 풀리면 몇 초 멈췄는지 한 줄. 게임 동작은 전혀 바꾸지 않는다.
 * 마인크래프트 클래스를 안 써서 main 트리와 26x 트리가 같은 파일이다.
 */
public final class StallWatch {
	private StallWatch() {
	}

	private static final long[] REPORT_AT_MS = {10_000L, 25_000L, 60_000L};
	private static final int MAX_FRAMES = 45;

	private static volatile long lastBeat;
	private static volatile Thread renderThread;
	private static boolean started;

	/** 렌더 스레드에서 부른다(틱, 로딩창). 처음 부를 때 감시를 시작한다. */
	public static void beat() {
		lastBeat = System.nanoTime();
		if (!started) {
			start(Thread.currentThread());
		}
	}

	private static synchronized void start(Thread render) {
		if (started) {
			return;
		}
		started = true;
		renderThread = render;
		Thread t = new Thread(StallWatch::loop, "Luna-StallWatch");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	private static void loop() {
		int reported = 0;
		long stallStart = 0L;
		while (true) {
			try {
				Thread.sleep(2000L);
			} catch (InterruptedException e) {
				return;
			}
			Thread render = renderThread;
			if (render == null || !render.isAlive()) {
				return;
			}
			long now = System.nanoTime();
			long sinceMs = (now - lastBeat) / 1_000_000L;
			if (sinceMs < REPORT_AT_MS[0]) {
				if (reported > 0) {
					long total = (now - stallStart) / 1_000_000L;
					log("[Nova] 화면 멈춤 풀림 - 약 " + (total / 1000L) + "초 멈춰 있었음");
				}
				reported = 0;
				continue;
			}
			if (reported == 0) {
				stallStart = lastBeat;
			}
			if (reported < REPORT_AT_MS.length && sinceMs >= REPORT_AT_MS[reported]) {
				reported++;
				log("[Nova] 화면 멈춤 " + (sinceMs / 1000L) + "초째 - 렌더 스레드 위치(" + reported + "/"
						+ REPORT_AT_MS.length + "):\n" + stackOf(render));
			}
		}
	}

	private static String stackOf(Thread t) {
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] st = t.getStackTrace();
		int n = Math.min(st.length, MAX_FRAMES);
		for (int i = 0; i < n; i++) {
			sb.append("\tat ").append(st[i]).append('\n');
		}
		if (st.length > n) {
			sb.append("\t... ").append(st.length - n).append("줄 더\n");
		}
		sb.append("\tstate=").append(t.getState());
		return sb.toString();
	}

	private static void log(String msg) {
		try {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn(msg);
		} catch (Throwable ignored) {
			System.out.println(msg);
		}
	}
}
