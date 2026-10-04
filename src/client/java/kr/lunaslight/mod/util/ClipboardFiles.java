package kr.lunaslight.mod.util;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 49-90차(8-19): 스크린샷을 <b>클립보드로 바로 복사</b>.
 *
 * <p>글자 복사(GLFW)와 달리 그림·파일은 AWT 클립보드뿐이다. 마인크래프트는 시작할 때
 * {@code java.awt.headless=true}를 박아 두므로 AWT를 처음 깨우기 전에 false로 돌려놓는다(다른 모드들도 같은
 * 방법을 쓴다). 복사할 내용은 <b>그림(imageFlavor) + 파일(javaFileListFlavor)</b> 둘 다 실어서 그림판·디스코드
 * 어디에 붙여도 된다. 영상 클립은 파일로만.
 *
 * <p>macOS는 메인 스레드가 아닌 곳에서 AWT를 깨우면 GLFW와 엉켜 멈출 수 있어 시도하지 않고 파일 경로만
 * 글자로 복사한다. 전부 <b>백그라운드 스레드</b>에서 하고(PNG 푸는 데 수십 ms), 실패해도 게임엔 영향이 없다.
 */
public final class ClipboardFiles {

	private ClipboardFiles() {
	}

	/** 결과: null이면 성공(그림+파일), 아니면 실패 사유(짧은 한국어). */
	public static CompletableFuture<String> copy(Path file, boolean image) {
		return CompletableFuture.supplyAsync(() -> doCopy(file, image), r -> {
			Thread t = new Thread(r, "luna-clipboard");
			t.setDaemon(true);
			t.start();
		});
	}

	private static String doCopy(Path file, boolean image) {
		String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
		if (os.contains("mac")) {
			return "macOS에서는 파일 복사를 지원하지 않습니다";
		}
		try {
			System.setProperty("java.awt.headless", "false");
			java.awt.image.BufferedImage img = null;
			if (image) {
				try {
					img = javax.imageio.ImageIO.read(file.toFile());
				} catch (Throwable ignored) {
				}
			}
			final java.awt.image.BufferedImage picture = img;
			final List<java.io.File> files = List.of(file.toFile());
			java.awt.datatransfer.Transferable t = new java.awt.datatransfer.Transferable() {
				@Override
				public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
					return picture != null
						? new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor,
							java.awt.datatransfer.DataFlavor.javaFileListFlavor}
						: new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor};
				}

				@Override
				public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
					return (picture != null && java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor))
						|| java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
						throws java.awt.datatransfer.UnsupportedFlavorException {
					if (picture != null && java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor)) {
						return picture;
					}
					if (java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor)) {
						return files;
					}
					throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
				}
			};
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(t, null);
			return null;
		} catch (Throwable e) {
			LunaCompat.warnOnce("clipboard:file", e);
			return "복사하지 못했습니다";
		}
	}
}
