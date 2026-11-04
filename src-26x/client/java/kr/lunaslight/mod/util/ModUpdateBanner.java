package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.gui.LunaDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 49-523차(사용자: "모드 업데이트 안 하고 남아 있으면 상단 창에 계속 떠 있게, 못 없애게"): 지금 쓰는 노바 모드 jar가 릴리스 "latest"의
 * 같은 마크 버전 jar(builtin-mod-&lt;마크버전&gt;-…jar)와 다르면 화면 맨 위에 안내 줄을 늘 띄운다. 닫는 버튼이 없다(게임을 다시 켜면 런처가 받는다).
 *
 * <p>비교: GitHub 릴리스 자산의 digest(sha256)와 내 jar의 sha256이 다르고, 그 자산이 내 jar 파일보다 나중에 올라왔을 때만
 * (직접 빌드해서 아직 안 올린 개발용 jar는 내 것이 더 새것이라 안 뜬다). 시작 30초 뒤, 그 뒤 10분마다 다른 스레드에서 묻는다.
 */
public final class ModUpdateBanner {
	private ModUpdateBanner() {
	}

	private static final String API = "https://api.github.com/repos/Sil2ntium7012/nova-mod/releases/tags/latest";
	private static final long FIRST_MS = 30_000L, EVERY_MS = 10 * 60_000L;

	private static volatile boolean outdated;
	private static volatile boolean busy;
	private static long startedAt, lastCheck;
	private static String mySha;
	private static long myMtime, mySize;
	private static boolean registered;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		startedAt = System.currentTimeMillis();
		try {
			LunaCompat.registerHudRenderCallback((ctx, tick) -> {
				poll();
				draw(ctx, false);
			});
			LunaCompat.registerScreenAfterRender((screen, ctx, mx, my) -> {
				poll();
				draw(ctx, true);
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("updateBanner:init", t);
		}
	}

	public static boolean outdated() {
		return outdated;
	}

	private static void poll() {
		long now = System.currentTimeMillis();
		if (busy || now - startedAt < FIRST_MS || (lastCheck != 0 && now - lastCheck < EVERY_MS)) {
			return;
		}
		lastCheck = now;
		busy = true;
		Thread t = new Thread(ModUpdateBanner::check, "nova-update-check");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	@SuppressWarnings("deprecation")
	private static void check() {
		try {
			if (mySha == null) {
				Path jar = myJar();
				if (jar == null) {
					return;   // 개발 실행(폴더) - 비교할 jar 없음
				}
				mySha = sha256(jar);
				myMtime = Files.getLastModifiedTime(jar).toMillis();
				mySize = Files.size(jar);
			}
			HttpURLConnection c = (HttpURLConnection) URI.create(API).toURL().openConnection();
			c.setConnectTimeout(8000);
			c.setReadTimeout(8000);
			c.setRequestProperty("Accept", "application/vnd.github+json");
			c.setRequestProperty("User-Agent", "NovaClient-Mod");
			if (c.getResponseCode() != 200) {
				return;
			}
			String body;
			try (InputStream in = c.getInputStream()) {
				body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			JsonElement root = new JsonParser().parse(body);
			if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("assets")) {
				return;
			}
			String prefix = "builtin-mod-" + LunaVersion.current() + "-";
			for (JsonElement e : root.getAsJsonObject().getAsJsonArray("assets")) {
				JsonObject a = e.getAsJsonObject();
				String name = a.has("name") ? a.get("name").getAsString() : "";
				if (!name.startsWith(prefix) || !name.endsWith(".jar")) {
					continue;
				}
				String digest = a.has("digest") && !a.get("digest").isJsonNull() ? a.get("digest").getAsString() : null;
				long updated = 0;
				try {
					updated = Instant.parse(a.get("updated_at").getAsString()).toEpochMilli();
				} catch (Throwable ignored) {
				}
				long size = a.has("size") ? a.get("size").getAsLong() : -1;
				// digest(sha256)가 없으면 크기로 대신 본다
				boolean differs = digest != null ? !digest.equalsIgnoreCase("sha256:" + mySha) : (size > 0 && size != mySize);
				// 릴리스 jar가 내 jar보다 1분 넘게 나중에 올라왔고 내용이 다르면 = 새 버전
				outdated = differs && updated > myMtime + 60_000L;
				return;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("updateBanner:check", t);
		} finally {
			busy = false;
		}
	}

	private static Path myJar() {
		try {
			var mc = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(kr.lunaslight.mod.LunaClientMod.MOD_ID).orElse(null);
			if (mc == null) {
				return null;
			}
			for (Path p : mc.getOrigin().getPaths()) {
				if (p.toString().endsWith(".jar") && Files.isRegularFile(p)) {
					return p;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("updateBanner:jar", t);
		}
		return null;
	}

	private static String sha256(Path p) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = Files.newInputStream(p)) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : md.digest()) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/** 화면 맨 위 가운데 줄. 창이 열려 있으면 창 위에서만(HUD 쪽은 건너뜀). */
	private static void draw(GuiGraphicsExtractor ctx, boolean fromScreen) {
		if (!outdated) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		if (!fromScreen && LunaCompat.screenOf(mc) != null) {
			return;
		}
		try {
			ctx.nextStratum();
		} catch (Throwable ignored) {
		}
		String msg = "노바 모드 새 버전이 나왔어요 | 게임을 끄고 런처로 다시 켜면 업데이트돼요";
		int sw = mc.getWindow().getGuiScaledWidth();
		int tw = LunaDraw.width(mc.font, msg);
		int w = tw + 24, h = 14;
		int x = (sw - w) / 2, y = 2;
		float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 400.0);
		// 49-548차(사용자 사진: "테두리랑 내부가 안 맞음"): 채움은 부드러운 모서리 그림, 테두리는 픽셀 원이라 모서리가 어긋났다 -
		// 테두리도 같은 방식으로: 금색 둥근 판을 깔고 그 안에 1칸 들인 판(반지름 -1)을 덮는다.
		LunaDraw.roundRect(ctx, x, y, w, h, 4, 0xFF141821);
		LunaDraw.roundRect(ctx, x, y, w, h, 4, LunaDraw.withAlpha(0xFFFFC857, Math.round(255 * pulse)));
		LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 3, 0xFF141821);
		LunaDraw.roundRect(ctx, x + 5, y + 5, 4, 4, 2, 0xFFFFC857);
		LunaDraw.text(ctx, mc.font, msg, x + 14, y + 3, 0xFFFFE6A8);
	}
}
