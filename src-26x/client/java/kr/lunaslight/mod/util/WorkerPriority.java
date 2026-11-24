package kr.lunaslight.mod.util;

/**
 * 49-728차(사용자: "리팩 다운할 때 컴터 렉 걸림, 다른 분들도 나도"): 서버 리소스팩(33MB)을 받으면 마크가 모델, 그림을 다시 읽느라
 * 작업 스레드(Worker-Main, 코어 수 - 1개)가 13초쯤 모든 코어를 꽉 채운다(로그 11-25 04:10:36~49). 그동안 디스코드, 브라우저까지 버벅였다.
 * 작업 스레드와 다운로드 스레드의 우선순위를 "보통보다 낮음"으로 낮춰 다른 프로그램이 먼저 돌게 한다(게임 화면 스레드는 그대로).
 * 접속할 때마다 마크가 작업 스레드를 새로 만들어서 2초마다 새 스레드만 낮춘다.
 */
public final class WorkerPriority {
	private WorkerPriority() {
	}

	private static final int LOW = Thread.NORM_PRIORITY - 2;   // 윈도우: 보통보다 낮음
	private static int tick;
	private static ThreadGroup root;
	private static Thread[] buf = new Thread[256];
	private static boolean failed;

	public static void tick() {
		if (failed || ++tick % 40 != 0) {
			return;
		}
		try {
			if (root == null) {
				ThreadGroup g = Thread.currentThread().getThreadGroup();
				while (g.getParent() != null) {
					g = g.getParent();
				}
				root = g;
			}
			int n = root.enumerate(buf, true);
			if (n >= buf.length) {
				buf = new Thread[buf.length * 2];
				return;
			}
			for (int i = 0; i < n; i++) {
				Thread t = buf[i];
				buf[i] = null;
				if (t == null || t.getPriority() <= LOW) {
					continue;
				}
				String name = t.getName();
				if (name.startsWith("Worker-Main") || name.startsWith("Download-")) {
					t.setPriority(LOW);
				}
			}
		} catch (Throwable t) {
			failed = true;
			LunaCompat.warnOnce("workerPriority", t);
		}
	}
}
