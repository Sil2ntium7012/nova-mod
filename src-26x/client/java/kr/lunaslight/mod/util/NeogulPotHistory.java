package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-682차(사용자: "모드로 받아서 모두 보내는 식으로 누적에"): 지난 피로회복제 사용 수를 게임 기록(logs)에서 세어 LUNARIS에 한 번 보낸다.
 *
 * <p>봇이 피로회복제를 세기 전에 쓴 것을 누적에 넣기 위한 것. 기록 원문은 보내지 않고 날짜별 개수만 보낸다:
 * <pre>{"ev":"pot_hist","v":1,"days":{"2026-11-08":27,"2026-11-11":16},"files":31}</pre>
 * <ul>
 *   <li>센 줄: 시스템 줄 "…섬 피로도가 회복되었습니다. (0/125)", "토벌[던전] 피로도가 회복되었습니다. (0/1200)".
 *       남이 쓴 글(전체, 귓속말, 확성기, 섬 채팅)은 뺀다.</li>
 *   <li>날짜: 기록 파일이 끝난 시각(수정 시각 2분 전)에서 자정을 넘은 횟수만큼 거슬러 올라간 날이 시작일.
 *       기록 줄의 시각이 1시간 넘게 뒤로 가면 자정을 넘은 것.</li>
 *   <li>오늘은 빼고 보낸다(오늘 것은 지금 신호의 피로회복제 수가 센다). 봇은 이미 센 날은 빼고 더한다.</li>
 *   <li>연동 코드(uid, 세대)마다 한 번. 보낸 코드는 config/lunaris-pot-hist.txt에 적어 둔다.</li>
 * </ul>
 */
public final class NeogulPotHistory {
	private NeogulPotHistory() {
	}

	private static final Pattern TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");
	private static final Pattern GZ = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d+\\.log\\.gz");
	private static final int MAX_DAYS = 180;

	private static volatile boolean running;
	private static volatile String doneKey;

	/** 연동 코드를 새로 붙여 넣었을 때(다른 사람 코드일 수 있음). */
	public static void relink() {
		doneKey = null;
	}

	/** 아직 이 코드로 안 보냈으면 뒤에서 세고, 다 세면 sink에 신호를 넘긴다(게임 스레드에서 부름). */
	public static void maybeSend(String key, Consumer<JsonObject> sink) {
		if (running || key == null || key.equals(doneKey)) {
			return;
		}
		Path flag = flagFile();
		try {
			if (flag != null && Files.exists(flag) && Files.readAllLines(flag).contains(key)) {
				doneKey = key;
				return;
			}
		} catch (Throwable ignored) {
		}
		running = true;
		Thread th = new Thread(() -> {
			try {
				Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
				int[] files = new int[1];
				Map<String, Integer> days = scan(dir, LocalDate.now(), files);
				JsonObject days0 = new JsonObject();
				days.forEach(days0::addProperty);
				JsonObject e = new JsonObject();
				e.addProperty("ev", "pot_hist");
				e.addProperty("v", 1);
				e.add("days", days0);
				e.addProperty("files", files[0]);
				net.minecraft.client.Minecraft.getInstance().execute(() -> {
					sink.accept(e);
					doneKey = key;
					try {
						if (flag != null) {
							Files.createDirectories(flag.getParent());
							Files.writeString(flag, key + "\n", StandardCharsets.UTF_8,
									java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
						}
					} catch (Throwable t) {
						LunaCompat.warnOnce("pothist:flag", t);
					}
				});
			} catch (Throwable t) {
				LunaCompat.warnOnce("pothist:scan", t);
				doneKey = key;   // 이번 실행에서는 다시 시도하지 않음
			} finally {
				running = false;
			}
		}, "Nova-PotHistory");
		th.setDaemon(true);
		th.start();
	}

	private static Path flagFile() {
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaris-pot-hist.txt");
		} catch (Throwable t) {
			return null;
		}
	}

	/** 날짜(오늘 전)별 피로회복제 사용 수. */
	static Map<String, Integer> scan(Path dir, LocalDate today, int[] files) throws java.io.IOException {
		TreeMap<String, Integer> out = new TreeMap<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		List<Path> list = new ArrayList<>();
		try (var ls = Files.list(dir)) {
			ls.forEach(list::add);
		}
		for (Path f : list) {
			String name = f.getFileName().toString();
			boolean gz = GZ.matcher(name).matches();
			if (!gz && !name.equals("latest.log")) {
				continue;
			}
			if (Files.size(f) > 300L * 1024 * 1024) {
				continue;
			}
			try {
				long mtime = Files.getLastModifiedTime(f).toMillis();
				LocalDate end = Instant.ofEpochMilli(mtime - 120_000).atZone(ZoneId.systemDefault()).toLocalDate();
				if (end.isBefore(today.minusDays(MAX_DAYS))) {
					continue;
				}
				countFile(f, gz, end, today, out);
				files[0]++;
			} catch (Throwable t) {
				LunaCompat.warnOnce("pothist:file", t);
			}
		}
		return out;
	}

	private static void countFile(Path f, boolean gz, LocalDate end, LocalDate today, Map<String, Integer> out) throws java.io.IOException {
		List<Integer> hitWraps = new ArrayList<>();
		int wraps = 0;
		int prev = -1;
		try (InputStream in0 = Files.newInputStream(f);
				InputStream in = gz ? new java.util.zip.GZIPInputStream(in0) : in0;
				BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				Matcher m = TIME.matcher(line);
				if (!m.find()) {
					continue;
				}
				int t = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
				if (prev >= 0 && t < prev - 3600) {
					wraps++;
				}
				prev = t;
				if (isPotion(line)) {
					hitWraps.add(wraps);
				}
			}
		}
		LocalDate start = end.minusDays(wraps);
		for (int w : hitWraps) {
			LocalDate d = start.plusDays(w);
			if (!d.isBefore(today)) {
				continue;
			}
			out.merge(d.toString(), 1, Integer::sum);
		}
	}

	static boolean isPotion(String line) {
		if (!line.contains("[CHAT]") || !line.contains("피로도가 회복되었습니다")) {
			return false;
		}
		return !(line.contains("[전체]") || line.contains("[귓속말]") || line.contains("[확성기]") || line.contains("[섬]")
				|| line.contains("명)") || line.contains("[질문]") || line.contains("[답변]"));
	}
}
