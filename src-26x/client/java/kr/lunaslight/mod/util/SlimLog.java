package kr.lunaslight.mod.util;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-768~769차(사용자: "평소에 저장할 때 쓸데없는 건 빼고 나눠서 저장", "하루 안 지나면 하나에만 쌓이게",
 * "로그 채팅 빼면 안 돼, 안 건든 원본도 남게, 다른 폴더에 카테고리 폴더로 나누든가"): <b>날짜별, 종류별 기록</b>.
 *
 * <p>게임이 쓰는 logs/latest.log를 2초마다 따라 읽어 logs/nova-logs 아래 세 폴더에 하루 한 파일로 이어 적는다
 * (게임을 여러 번 켜도 같은 날이면 같은 파일). 게임이 만드는 원래 파일(latest.log, 날짜-N.log.gz)은 건드리지 않는다.
 * <ul>
 *   <li><b>원본</b>: 한 줄도 빼거나 고치지 않은 그대로. 8MB를 넘으면 날짜_2.log로 나눈다(디스코드에 올라가는 크기). 14일.</li>
 *   <li><b>채팅</b>: [CHAT] 줄만(이어지는 줄 포함). 2MB마다 나눔. 30일.</li>
 *   <li><b>문제</b>: 경고와 오류 줄(뒤따르는 오류 내용 40줄까지), 접속과 끊김, 리소스팩, 시작과 끝, [Nova] 줄, 5분마다 메모리.
 *       같은 줄이 연달아 오면 한 번만 적고 몇 번 더 왔는지 적는다. 1MB마다 나눔. 14일.</li>
 * </ul>
 * 게임 코드를 건드리지 않는다(파일만 읽고 씀). 실패해도 게임에는 영향이 없다.
 */
public final class SlimLog {
	private SlimLog() {
	}

	private static final Pattern HEAD = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})\\] \\[[^\\]]*/(INFO|WARN|ERROR|FATAL|DEBUG|TRACE)\\]");
	/**
	 * 49-816차(11/28 티모 튕김 기록: 7,843줄 가운데 7,059줄이 "Ignoring player info update for unknown player"): 문제 기록에서 빼는 줄.
	 * 서버가 탭 목록을 고칠 때마다 뜨는 경고와 리소스팩 그림, 모델이 없다는 경고는 튕김과 상관이 없고 기록만 키운다(원본 기록에는 그대로 남는다).
	 */
	private static final String[] NOISE = {
		"Ignoring player info update for unknown player", "Missing textures in model", "Missing texture references in model", "Missing block model",
		"Missing resource ", "Received passengers for unknown entity", "Non [a-z0-9_.-] character in namespace",
	};

	private static boolean noise(String line) {
		for (String k : NOISE) {
			if (line.contains(k)) {
				return true;
			}
		}
		return false;
	}

	private static final String[] KEEP = {
		"[Nova]", "Connecting to", "onnect", "Stopping!", "Stopping ", "timed out", "Timed out", "Kicked", "kicked",
		"resource pack", "Resource pack", "server pack", "Reloading ResourceManager", "OutOfMemory", "Out of memory",
		"Loading Minecraft", "Setting user", "Backend library", "OpenGL", "Crash", "crash", "Watchdog", "GL error",
	};
	private static String RAW = "원본", CHAT = "채팅", ISSUE = "문제";
	private static volatile boolean started;

	// ==================== 49-770차: 튕김, 비정상 종료 알리기 ====================
	// 사용자: "유저들 튕겼을 때 루나리스 로그에 튕긴 로그랑 해서 올려 주면 좋을 듯, 고치게".
	//  - 튕김: 기록에 "disconnected with reason" / "lost connection" 줄이 오면 3초 뒤(뒤따르는 줄까지) 그 사유와 최근 문제 줄을 묶는다.
	//  - 비정상 종료: 켤 때 지난 실행의 문제 기록에 "Stopping!"(정상 종료 줄)이 없으면 그 끝부분과 충돌 보고서 머리를 묶는다.
	//  - 너굴마을(mcng)에 붙어 있던 것만. 2분에 한 번. 채팅은 들어가지 않는다(문제 줄만).
	// 묶은 것은 여기 줄에 쌓아 두고 LunarisLinkModule이 연동 신호 {"ev":"issue"}로 보낸다. 나갈 때 뜨는 평범한 끊김은 봇이 거른다.
	private static final java.util.concurrent.ConcurrentLinkedQueue<com.google.gson.JsonObject> REPORTS = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private static final Pattern DC = Pattern.compile("(?i)(?:disconnected with reason|lost connection)\\s*:?\\s*(.*)$");
	private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();
	private static String host = "";
	private static long dcAt, reportAt;
	private static String dcWhy = "";

	/** LunarisLinkModule이 틱마다: 보낼 알림이 있으면 하나. */
	public static com.google.gson.JsonObject pollReport() {
		return REPORTS.poll();
	}

	private static String tail(java.util.Collection<String> lines, int max) {
		java.util.ArrayDeque<String> out = new java.util.ArrayDeque<>();
		int len = 0;
		java.util.List<String> list = new ArrayList<>(lines);
		for (int i = list.size() - 1; i >= 0; i--) {
			String l = list.get(i);
			if (l.length() > 170) {
				l = l.substring(0, 170) + " …";
			}
			if (len + l.length() + 1 > max) {
				break;
			}
			out.addFirst(l);
			len += l.length() + 1;
		}
		return String.join("\n", out);
	}

	private static volatile Path issueFile, crashFile;

	private static void report(String kind, String why, String text) {
		try {
			if (REPORTS.size() >= 4) {
				return;
			}
			com.google.gson.JsonObject e = new com.google.gson.JsonObject();
			e.addProperty("ev", "issue");
			e.addProperty("k", System.currentTimeMillis());
			e.addProperty("kind", kind);
			e.addProperty("why", why.length() > 200 ? why.substring(0, 200) : why);
			e.addProperty("host", host.length() > 60 ? host.substring(0, 60) : host);
			Runtime rt = Runtime.getRuntime();
			e.addProperty("mem", ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20));
			e.addProperty("txt", text);
			if (sureNext) {
				e.addProperty("sure", 1);   // 49-789차: 지난 실행의 게임 기록 전체에 정상 종료 줄이 없음을 확인함
				sureNext = false;
			}
			// 49-770차(사용자: "사진 + 파일 업로드 해라"): 문제 기록 파일(과 충돌 보고서)을 신호에 붙여 올린다. "_"로 시작하는 것은 보낼 때 떼어 낸다.
			Path f = issueFile;
			if (f != null) {
				e.addProperty("_file", f.toString());
			}
			Path c = crashFile;
			if (c != null) {
				e.addProperty("_file2", c.toString());
				crashFile = null;
			}
			REPORTS.add(e);
		} catch (Throwable ignored) {
		}
	}

	private static volatile boolean sureNext;

	/**
	 * 49-789차(LUNARIS 세션: 창을 X로 닫으면 게임은 "Stopping!"을 남기는데, 이 기록은 2초마다 읽는 터라 그 줄을 못 읽고 꺼져 비정상 종료로 올라감):
	 * 지난 실행의 게임 기록 전체(게임이 켜질 때 latest.log를 날짜-번호.log.gz로 묶어 둔 것)에서 정상 종료 줄을 직접 찾는다.
	 * 1 = 있음(정상 종료), 0 = 끝까지 읽었는데 없음, -1 = 그 파일을 못 찾았거나 못 읽음.
	 */
	private static int prevLogClean(Path logs, long lastWrite) {
		try {
			long now = System.currentTimeMillis();
			Path newest = null;
			for (int wait = 0; wait < 60; wait++) {
				newest = null;
				long nt = 0;
				boolean packing = false;
				try (var ls = Files.list(logs)) {
					for (Path f : ls.toList()) {
						String n = f.getFileName().toString();
						if (n.startsWith("debug") || !Files.isRegularFile(f)) {
							continue;
						}
						if (n.matches("\\d{4}-\\d{2}-\\d{2}-\\d+\\.log")) {
							packing = true;   // 아직 묶는 중(묶기 전 이름)
						}
						long t = Files.getLastModifiedTime(f).toMillis();
						if (n.endsWith(".log.gz") && t > nt) {
							nt = t;
							newest = f;
						}
					}
				}
				// 이번에 켜지면서 묶인 것이어야 한다: 지난 실행의 마지막 기록보다 뒤, 지금부터 15분 안
				if (newest != null && (nt < lastWrite - 60_000 || now - nt > 15 * 60_000)) {
					newest = null;
				}
				if (newest != null && !packing) {
					long a = Files.size(newest);
					Thread.sleep(1500);
					if (a == Files.size(newest)) {
						break;
					}
				} else {
					if (wait >= 10 && !packing) {
						break;   // 10초를 기다려도 없으면 묶는 설정이 아닌 런처
					}
					Thread.sleep(1000);
				}
			}
			if (newest == null) {
				return -1;
			}
			try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
					new java.util.zip.GZIPInputStream(Files.newInputStream(newest), 65536), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					if (line.contains("Stopping!")) {
						return 1;
					}
				}
			}
			return 0;
		} catch (Throwable t) {
			return -1;
		}
	}

	/** 켤 때: 지난 실행이 정상 종료 줄 없이 끝났으면 알린다. */
	private static void checkLastRun(Path dir) {
		try {
			Path sub = dir.resolve(ISSUE);
			if (!Files.isDirectory(sub)) {
				return;
			}
			Path newest = null;
			long nt = 0;
			try (var ls = Files.list(sub)) {
				for (Path f : ls.toList()) {
					long t = Files.getLastModifiedTime(f).toMillis();
					if (f.getFileName().toString().endsWith(".log") && t > nt) {
						nt = t;
						newest = f;
					}
				}
			}
			if (newest == null || System.currentTimeMillis() - nt > 36L * 3600_000) {
				return;
			}
			String all = Files.readString(newest, StandardCharsets.UTF_8);
			int at = all.lastIndexOf("===== 게임 시작");
			if (at < 0) {
				return;
			}
			String block = all.substring(at);
			issueFile = newest;
			if (block.contains("Stopping!") || !block.contains("mcng")) {
				return;
			}
			int full = prevLogClean(dir.getParent(), nt);   // 49-789차: 게임 기록 전체에서 다시 확인
			if (full == 1) {
				return;
			}
			List<String> lines = new ArrayList<>(java.util.Arrays.asList(block.split("\n")));
			String crash = "";
			try {
				Path cr = dir.getParent().getParent().resolve("crash-reports");
				if (Files.isDirectory(cr)) {
					Path cf = null;
					long ct = 0;
					try (var ls = Files.list(cr)) {
						for (Path f : ls.toList()) {
							long t = Files.getLastModifiedTime(f).toMillis();
							if (t > ct) {
								ct = t;
								cf = f;
							}
						}
					}
					if (cf != null && Math.abs(ct - nt) < 10 * 60_000) {
						List<String> cl = Files.readAllLines(cf, StandardCharsets.UTF_8);
						StringBuilder sb = new StringBuilder();
						for (String l : cl) {
							if (l.startsWith("Description:") || l.startsWith("\tat ") && sb.length() < 500 || l.contains("Exception") || l.contains("Error")) {
								sb.append(l.length() > 160 ? l.substring(0, 160) : l).append('\n');
							}
							if (sb.length() > 600) {
								break;
							}
						}
						crash = "[충돌 보고서 " + cf.getFileName() + "]\n" + sb;
						crashFile = cf;
					}
				}
			} catch (Throwable ignored) {
			}
			host = "mcng";
			String t = tail(lines, crash.isEmpty() ? 1100 : 500);
			sureNext = full == 0;
			report("crash", "지난 실행이 정상 종료 줄 없이 끝남", crash.isEmpty() ? t : crash + t);
		} catch (Throwable ignored) {
		}
	}

	public static void start() {
		if (started) {
			return;
		}
		started = true;
		try {
			Thread t = new Thread(SlimLog::run, "Nova-SlimLog");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		} catch (Throwable ignored) {
		}
	}

	/** 간추린 기록 폴더(logs/nova-slim). */
	public static Path folder() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve("nova-logs");
	}

	private static void run() {
		try {
			Path src = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
			Path dir = folder();
			try {
				dir.resolve(RAW);
			} catch (Throwable bad) {
				// 한글 폴더 이름을 못 쓰는 환경(글자 설정이 다른 운영체제)이면 영어 이름으로
				RAW = "original";
				CHAT = "chat";
				ISSUE = "issue";
			}
			checkLastRun(dir);
			sweep(dir.resolve(RAW), 14);
			sweep(dir.resolve(CHAT), 30);
			sweep(dir.resolve(ISSUE), 14);
			String mark = "\n===== 게임 시작 " + LocalDate.now() + " " + LocalTime.now().withNano(0) + " =====\n";
			write(dir, RAW, mark, 8L * 1024 * 1024);
			write(dir, CHAT, mark, 2L * 1024 * 1024);
			write(dir, ISSUE, mark, 1024 * 1024);
			boolean chatting = false;
			long pos = 0;
			StringBuilder part = new StringBuilder();
			boolean keeping = false;
			int extra = 0;
			String last = null;
			int repeat = 0;
			long memAt = System.currentTimeMillis() + 60_000;
			while (true) {
				List<String> out = new ArrayList<>();
				List<String> chat = new ArrayList<>();
				try {
					long size = Files.exists(src) ? Files.size(src) : 0;
					if (size < pos) {
						pos = 0;   // 파일이 새로 만들어짐
						part.setLength(0);
					}
					if (size > pos) {
						byte[] buf;
						try (RandomAccessFile f = new RandomAccessFile(src.toFile(), "r")) {
							f.seek(pos);
							int n = (int) Math.min(size - pos, 4L * 1024 * 1024);
							buf = new byte[n];
							f.readFully(buf);
							pos += n;
						}
						// 줄 끝(\n)에서만 자른다 - UTF-8 글자가 중간에 잘리지 않게 줄이 끝나지 않은 뒷부분은 다음에 다시 읽는다
						int cut = buf.length;
						while (cut > 0 && buf[cut - 1] != '\n') {
							cut--;
						}
						pos -= buf.length - cut;
						if (cut == 0 && buf.length >= 4 * 1024 * 1024) {
							pos += buf.length;   // 줄 하나가 4MB를 넘음 - 건너뜀
						}
						String text = new String(buf, 0, cut, StandardCharsets.UTF_8);
						if (cut > 0) {
							// 원본: 읽은 그대로(글자를 다시 만들지 않고 바이트 그대로 붙인다)
							writeBytes(dir, RAW, java.util.Arrays.copyOf(buf, cut), 8L * 1024 * 1024);
						}
						for (String line : text.split("\n")) {
							if (line.endsWith("\r")) {
								line = line.substring(0, line.length() - 1);
							}
							if (line.isEmpty()) {
								continue;
							}
							Matcher h = HEAD.matcher(line);
							boolean keep;
							boolean head = h.find();
							if (head) {
								chatting = line.contains("[CHAT]");
							}
							if (chatting) {
								chat.add(line);
							}
							if (head) {
								String lv = h.group(2);
								keep = !line.contains("[CHAT]") && !noise(line) && (lv.equals("WARN") || lv.equals("ERROR") || lv.equals("FATAL") || wanted(line));
								keeping = keep;
								extra = 0;
							} else {
								keep = keeping && ++extra <= 40;   // 오류 내용(이어지는 줄)
							}
							if (!keep) {
								continue;
							}
							if (line.length() > 600) {
								line = line.substring(0, 600) + " …";
							}
							String body = h.reset().find() ? line.substring(h.end()) : line;
							if (body.equals(last)) {
								repeat++;
								continue;
							}
							if (repeat > 0) {
								out.add("    (같은 줄 " + repeat + "번 더)");
								repeat = 0;
							}
							last = body;
							out.add(line);
							RECENT.addLast(line);
							while (RECENT.size() > 40) {
								RECENT.pollFirst();
							}
							if (head) {
								int ci = line.indexOf("Connecting to ");
								if (ci >= 0) {
									host = line.substring(ci + 14).trim();
								}
								Matcher dm = DC.matcher(line);
								if (dm.find() && dcAt == 0) {
									dcAt = System.currentTimeMillis();
									dcWhy = dm.group(1).trim();
								}
							}
						}
					}
					long now = System.currentTimeMillis();
					if (dcAt > 0 && now - dcAt >= 3000) {
						if (host.contains("mcng") && now - reportAt > 120_000) {
							reportAt = now;
							report("dc", dcWhy, tail(RECENT, 1100));
						}
						dcAt = 0;
					}
					if (now >= memAt) {
						memAt = now + 5 * 60_000;
						Runtime rt = Runtime.getRuntime();
						long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
						out.add("[" + LocalTime.now().withNano(0) + "] [Nova 간추린 기록] 메모리 " + used + " / " + max + "MB");
					}
					if (!out.isEmpty()) {
						write(dir, ISSUE, String.join("\n", out) + "\n", 1024 * 1024);
					}
					if (!chat.isEmpty()) {
						write(dir, CHAT, String.join("\n", chat) + "\n", 2L * 1024 * 1024);
					}
				} catch (Throwable ignored) {
				}
				Thread.sleep(2000);
			}
		} catch (Throwable ignored) {
		}
	}

	private static boolean wanted(String line) {
		for (String k : KEEP) {
			if (line.contains(k)) {
				return true;
			}
		}
		return false;
	}

	/** 그 종류 폴더의 오늘 파일에 이어 적는다. 크기를 넘으면 날짜_2.log, 날짜_3.log … */
	private static void write(Path dir, String kind, String text, long maxBytes) throws java.io.IOException {
		writeBytes(dir, kind, text.getBytes(StandardCharsets.UTF_8), maxBytes);
	}

	private static void writeBytes(Path dir, String kind, byte[] data, long maxBytes) throws java.io.IOException {
		Path sub = dir.resolve(kind);
		Files.createDirectories(sub);
		String day = LocalDate.now().toString();
		Path f = sub.resolve(day + ".log");
		for (int k = 2; k < 200 && Files.exists(f) && Files.size(f) >= maxBytes; k++) {
			f = sub.resolve(day + "_" + k + ".log");
		}
		Files.write(f, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		if (kind.equals(ISSUE)) {
			issueFile = f;
		}
	}

	private static void sweep(Path dir, int keepDays) {
		try (var ls = Files.list(dir)) {
			String cut = LocalDate.now().minusDays(keepDays).toString();
			for (Path f : ls.toList()) {
				String n = f.getFileName().toString();
				if (n.matches("\\d{4}-\\d{2}-\\d{2}(_\\d+)?\\.log") && n.substring(0, 10).compareTo(cut) < 0) {
					Files.deleteIfExists(f);
				}
			}
		} catch (Throwable ignored) {
		}
	}
}
