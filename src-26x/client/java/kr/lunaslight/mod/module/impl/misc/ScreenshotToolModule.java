package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 19차: ScreenshotRecorder.takeScreenshot(...)의 시그니처가 버전마다 다름 - 구버전은
 * `takeScreenshot(Framebuffer)`가 NativeImage를 직접 반환하지만, 1.21.9+에서는
 * `takeScreenshot(Framebuffer, Consumer<NativeImage>)` 콜백 형태로 바뀌었습니다(정적 타입으로
 * 하나만 고정해서 부르면 다른 버전에서 컴파일이 깨짐 - 리플렉션으로 두 형태 다 시도).
 *
 * 21차: NativeImage#getColor(int,int)/setColor(int,int,int)가 (1.21.11뿐 아니라 1.21.4에서도)
 * private으로 바뀌어 직접 호출하면 컴파일 자체가 깨짐 - upscale()에서 두 메서드를 리플렉션 +
 * setAccessible(true)로 우회 호출(모드가 클래스패스에서 도는 한 접근 제어자는 그냥 우회 가능,
 * JPMS strong encapsulation과 무관). 그래도 리플렉션 핸들 자체를 못 찾으면 예외를 던져
 * takeScreenshot()의 try/catch(Throwable upscaleFailed)가 잡아 업스케일 없이 원본 그대로 저장.
 *
 * 35차: 클래스 이름 자체가 1.16.x는 ScreenshotUtils, 1.17+는 ScreenshotRecorder로 다르고
 * (claude/nova-mod-todo.md 35차), NativeImage 저장 메서드명도 1.17까지는 writeFile(File),
 * 1.17.1부터 writeTo(File)로 다름 - 클래스는 LunaCompat.resolveClass(...)로, 저장은
 * LunaCompat.writeImage(...)로 완전히 리플렉션 이관.
 */
public class ScreenshotToolModule extends Module {

	private final KeybindSetting screenshotKey;
	private final IntSetting upscaleFactor;
	private final StringSetting filenamePattern;
	private final BooleanSetting copyToClipboard;
	private final BooleanSetting openAfter;
	// 49-192차(사용자: "스크린샷 기능에 채팅 자동 가리기, 플레이어 닉네임 가리기"): 찍는 순간에만 잠깐 숨긴다
	private final BooleanSetting hideChat;
	private final BooleanSetting hideNames;

	private boolean wasPressed = false;
	/** 숨긴 뒤 찍기까지 남은 틱(-1 = 대기 없음). 숨긴 화면이 한 번 이상 그려진 다음에 찍어야 해서 2틱 기다린다. */
	private int shotIn = -1;
	private java.util.List<Object> chatList;
	private java.util.List<Object> chatSaved;

	public ScreenshotToolModule() {
		super("screenshot_tool", "스크린샷", ModuleCategory.FEATURE, "스크린샷 확대 저장 | 복사 | 열기");

		screenshotKey = register(new KeybindSetting("screenshot_key", "스크린샷 키", "스크린샷을 찍는 키입니다.", InputConstants.KEY_F2));
		upscaleFactor = register(new IntSetting("upscale_factor", "확대 배율", "이미지를 몇 배로 키워 저장할지 정합니다.", 1, 1, 4, 1).unit("배"));
		filenamePattern = register(new StringSetting("filename_pattern", "파일 이름", "쓸 수 있는 표기는 아래 목록에 있습니다.", "screenshot_%time%"));
		copyToClipboard = register(new BooleanSetting("copy_to_clipboard", "클립보드 복사", "찍은 즉시 클립보드에 복사합니다.", true));
		openAfter = register(new BooleanSetting("open_after", "촬영 후 열기", "저장한 뒤 기본 이미지 뷰어로 엽니다.", false));
		hideChat = register(new BooleanSetting("hide_chat", "채팅 가리기", "찍는 순간에만 화면의 채팅을 숨깁니다.", true));
		hideNames = register(new BooleanSetting("hide_names", "닉네임 가리기", "찍는 순간에만 다른 플레이어 머리 위 이름을 숨깁니다.", true));
	}

	@Override
	public void onTick() {
		boolean pressedNow = screenshotKey.isDown(client); // 49-24차: 조합키/마우스 지원
		boolean justPressed = pressedNow && !wasPressed;
		wasPressed = pressedNow;

		if (justPressed && kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && shotIn < 0) {
			if (hideChat.get() || hideNames.get()) {
				beginHide();
				shotIn = 2;
			} else {
				takeScreenshot();
			}
		}
	}

	@Override
	protected void onDisable() {
		if (shotIn > 0) {
			shotIn = -1;
			endHide();
		}
	}

	/** 채팅 줄을 잠깐 비우고(되돌릴 것 보관) 닉네임 표시를 끈다. */
	@SuppressWarnings("unchecked")
	private void beginHide() {
		if (hideNames.get()) {
			kr.lunaslight.mod.module.impl.render.NametagVisibilityModule.shotHidePlayers = true;
		}
		if (hideChat.get()) {
			try {
				Object chat = LunaCompat.callNoArg(LunaCompat.inGameHud(client), "getChatHud");
				java.lang.reflect.Field f = LunaCompat.findField(chat.getClass(), "visibleMessages");
				Object v = f == null ? null : f.get(chat);
				if (v instanceof java.util.List<?> list) {
					chatList = (java.util.List<Object>) list;
					chatSaved = new java.util.ArrayList<>(chatList);
					chatList.clear();
				}
			} catch (Throwable t) {
				chatList = null;
				chatSaved = null;
			}
		}
	}

	/** 숨긴 것을 되돌린다. 그사이 새로 온 채팅은 위(최신 쪽)에 그대로 두고 예전 줄을 뒤에 붙인다. */
	private void endHide() {
		kr.lunaslight.mod.module.impl.render.NametagVisibilityModule.shotHidePlayers = false;
		if (chatList != null && chatSaved != null) {
			try {
				chatList.addAll(chatSaved);
			} catch (Throwable ignored) {
			}
		}
		chatList = null;
		chatSaved = null;
	}

	private void takeScreenshot() {
		if (kr.lunaslight.mod.util.LunaCompat.mainRenderTarget(client) == null) {
			return;
		}

		try {
			NativeImage captured = captureScreenshot();
			if (captured == null) {
				return;
			}

			NativeImage finalImage = captured;
			int factor = upscaleFactor.get();
			if (factor > 1) {
				try {
					finalImage = upscale(captured, factor);
					captured.close();
				} catch (Throwable upscaleFailed) {
					// 1.21.9+에서 NativeImage#getColor/setColor가 private으로 바뀌어 픽셀 단위
					// 업스케일이 불가능한 버전 - 업스케일 없이 원본 그대로 저장.
					finalImage = captured;
				}
			}

			Path screenshotsDir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
			Files.createDirectories(screenshotsDir);

			// 49-212차(사용자: "밖으로 내보내는 파일 이름에 루나/노바 빼"): 옛 기본값(luna_/nova_)으로 저장돼 있던 설정은 새 기본값으로 본다
			String pattern = filenamePattern.get();
			if ("luna_%time%".equals(pattern) || "nova_%time%".equals(pattern)) {
				pattern = "screenshot_%time%";
				filenamePattern.setValue(pattern);
			}
			String fileName = expandPattern(pattern) + ".png";
			Path outputPath = screenshotsDir.resolve(fileName);

			LunaCompat.writeImage(finalImage, outputPath.toFile());

			if (copyToClipboard.get()) {
				tryCopyToClipboard(outputPath.toFile());
			}
			if (openAfter.get()) {
				tryOpenFile(outputPath.toFile());
			}

			finalImage.close();
		} catch (Exception e) {
			// 캡처/저장 중 어떤 예외가 나든 게임이 죽지 않도록 방어
			e.printStackTrace();
		}
	}

	/**
	 * ScreenshotRecorder.takeScreenshot(...)를 리플렉션으로 호출 - 구버전
	 * takeScreenshot(Framebuffer)(NativeImage 직접 반환)과 1.21.9+
	 * takeScreenshot(Framebuffer, Consumer<NativeImage>)(콜백) 두 형태를 다 시도.
	 */
	private NativeImage captureScreenshot() throws Exception {
		Object framebuffer = kr.lunaslight.mod.util.LunaCompat.mainRenderTarget(client);
		Class<?> recorderClass = LunaCompat.resolveClass(
				"net.minecraft.client.Screenshot", "net.minecraft.client.util.ScreenshotUtils");
		if (recorderClass == null) {
			return null;
		}
		for (java.lang.reflect.Method m : recorderClass.getMethods()) {
			// 46차: 프로덕션에서는 메서드 이름이 intermediary라 LunaCompat.nameMatches로 비교
			if (!LunaCompat.nameMatches(recorderClass, "takeScreenshot", m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
				continue;
			}
			Class<?>[] params = m.getParameterTypes();
			try {
				if (params.length == 1) {
					Object result = m.invoke(null, framebuffer);
					if (result instanceof NativeImage image) {
						return image;
					}
				} else if (params.length == 2) {
					NativeImage[] holder = new NativeImage[1];
					java.util.function.Consumer<NativeImage> callback = holder0 -> holder[0] = holder0;
					m.invoke(null, framebuffer, callback);
					if (holder[0] != null) {
						return holder[0];
					}
				}
			} catch (Exception ignored) {
				// 이 오버로드는 안 맞았던 것 - 다음 오버로드 시도.
			}
		}
		return null;
	}

	/**
	 * 단순 nearest-neighbor 업스케일: 새 크기의 NativeImage를 만들어 원본 픽셀을 factor배로 반복 복사.
	 * getColor/setColor가 private인 버전에서도 컴파일되도록 리플렉션 + setAccessible로 호출.
	 */
	private NativeImage upscale(NativeImage source, int factor) {
		int newWidth = source.getWidth() * factor;
		int newHeight = source.getHeight() * factor;
		NativeImage result = new NativeImage(newWidth, newHeight, false);

		java.lang.reflect.Method getColor = findDeclaredMethod(source.getClass(), "getColor", 2);
		java.lang.reflect.Method setColor = findDeclaredMethod(result.getClass(), "setColor", 3);
		if (getColor == null || setColor == null) {
			result.close();
			throw new IllegalStateException("NativeImage getColor/setColor 리플렉션 핸들을 찾지 못함");
		}

		try {
			for (int y = 0; y < source.getHeight(); y++) {
				for (int x = 0; x < source.getWidth(); x++) {
					int color = (int) getColor.invoke(source, x, y);
					for (int dy = 0; dy < factor; dy++) {
						for (int dx = 0; dx < factor; dx++) {
							setColor.invoke(result, x * factor + dx, y * factor + dy, color);
						}
					}
				}
			}
		} catch (Exception e) {
			result.close();
			throw new IllegalStateException(e);
		}
		return result;
	}

	private static java.lang.reflect.Method findDeclaredMethod(Class<?> cls, String name, int paramCount) {
		// 46차: 프로덕션에서는 메서드 이름이 intermediary라 LunaCompat 이름표로 후보를 넓혀 비교
		java.util.List<String> names = LunaCompat.memberNameCandidates(cls, name);
		for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
			if (names.contains(m.getName()) && m.getParameterCount() == paramCount) {
				m.setAccessible(true);
				return m;
			}
		}
		return null;
	}

	private void tryCopyToClipboard(File file) {
		try {
			BufferedImage image = javax.imageio.ImageIO.read(file);
			if (image == null) {
				return;
			}
			Transferable transferable = new Transferable() {
				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[]{DataFlavor.imageFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.imageFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
					if (!isDataFlavorSupported(flavor)) {
						throw new UnsupportedFlavorException(flavor);
					}
					return image;
				}
			};
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(transferable, null);
		} catch (Exception e) {
			// 헤드리스 환경 등에서 실패할 수 있으나, 스크린샷 저장 자체는 이미 완료됐으므로 무시.
		}
	}

	private void tryOpenFile(File file) {
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(file);
			}
		} catch (IOException e) {
			// 파일 열기 실패는 치명적이지 않으므로 무시.
		}
	}

	/**
	 * 49-32차(사용자: "파일 이름에 타임이나 형식 같은 걸 아래 따로 볼 수 있게 해주고 더 스펙트럼 넓혀줘"):
	 * 파일 이름에 쓸 수 있는 표기들. 설정 화면 아래에 그대로 안내로 보여 준다.
	 */
	public static final String[][] NAME_TOKENS = {
		{"%time%", "찍은 시각 (2026-09-09_18.30.05)"},
		{"%date%", "날짜 (2026-09-09)"},
		{"%clock%", "시각만 (18.30.05)"},
		{"%year%", "연도"}, {"%month%", "월"}, {"%day%", "일"},
		{"%hour%", "시"}, {"%minute%", "분"}, {"%second%", "초"},
		{"%name%", "내 닉네임"},
		{"%server%", "서버 주소(싱글이면 월드 이름)"},
		{"%dimension%", "차원 (overworld | the_nether | the_end)"},
		{"%biome%", "바이옴"},
		{"%x%", "X 좌표"}, {"%y%", "Y 좌표"}, {"%z%", "Z 좌표"},
		{"%fps%", "그때 프레임"},
		{"%version%", "마인크래프트 버전"},
	};

	/** 49-32차: 설정 화면 아래에 표기 목록을 그대로 안내로 보여 준다. */
	@Override
	public String[][] helpTable() {
		return NAME_TOKENS;
	}

	@Override
	public String helpTableTitle() {
		return "파일 이름에 쓸 수 있는 표기";
	}

	private String expandPattern(String pattern) {
		java.time.LocalDateTime now = LocalDateTime.now();
		String out = pattern == null || pattern.isBlank() ? "%time%" : pattern;
		out = out.replace("%time%", DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(now));
		out = out.replace("%date%", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(now));
		out = out.replace("%clock%", DateTimeFormatter.ofPattern("HH.mm.ss").format(now));
		out = out.replace("%year%", String.format("%04d", now.getYear()));
		out = out.replace("%month%", String.format("%02d", now.getMonthValue()));
		out = out.replace("%day%", String.format("%02d", now.getDayOfMonth()));
		out = out.replace("%hour%", String.format("%02d", now.getHour()));
		out = out.replace("%minute%", String.format("%02d", now.getMinute()));
		out = out.replace("%second%", String.format("%02d", now.getSecond()));
		try {
			out = out.replace("%name%", client.player != null ? client.player.getName().getString() : "player");
			String server = LunaCompat.currentServerLabel(client);
			out = out.replace("%server%", server == null ? "singleplayer" : server);
			String dim = "overworld";
			try {
				Object w = client.level;
				Object key = w == null ? null : LunaCompat.invokeNoArg(w, "getRegistryKey");
				Object id = key == null ? null : LunaCompat.invokeNoArg(key, "getValue");
				Object path = id == null ? null : LunaCompat.invokeNoArg(id, "getPath");
				if (path instanceof String ps) {
					dim = ps;
				}
			} catch (Throwable ignored) {
			}
			out = out.replace("%dimension%", dim);
			if (out.contains("%biome%") || out.contains("%x%") || out.contains("%y%") || out.contains("%z%")) {
				net.minecraft.world.phys.Vec3 pos = client.player == null ? null : LunaCompat.getPos(client.player);
				int bx = pos == null ? 0 : (int) Math.floor(pos.x);
				int by = pos == null ? 0 : (int) Math.floor(pos.y);
				int bz = pos == null ? 0 : (int) Math.floor(pos.z);
				out = out.replace("%x%", Integer.toString(bx))
						.replace("%y%", Integer.toString(by))
						.replace("%z%", Integer.toString(bz));
				String biome = client.level == null ? null
						: LunaCompat.getBiomeName(client.level, new net.minecraft.core.BlockPos(bx, by, bz));
				out = out.replace("%biome%", biome == null ? "unknown" : biome);
			}
			out = out.replace("%fps%", Integer.toString(kr.lunaslight.mod.util.LunaPerf.fps()));
			out = out.replace("%version%", kr.lunaslight.mod.util.LunaVersion.current());
		} catch (Throwable ignored) {
		}
		// 파일 이름에 못 쓰는 글자 정리
		return out.replaceAll("[\\\\/:*?\"<>|]", "_");
	}
}
