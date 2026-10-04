package kr.lunaslight.mod.util;

import kr.lunaslight.mod.LunaClientMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-23차: "마크 윈도우 아이콘을 루나 아이콘으로" - 게임 창(제목 표시줄/작업 표시줄) 아이콘 교체.
 * 49-24차: "런처랑 헷갈려서 - 일반 마크 모양에 런처 모양 약간만" → 바닐라 잔디 블록 아이콘 오른쪽 아래에 로고 배지를
 * 얹은 합성본. 픽셀은 PNG 디코딩 없이 미리 만들어 둔 원시 RGBA(assets/lunaslight/icon/16·24·32·48·64·128.rgba)를
 * 그대로 올린다 - 어떤 버전의 NativeImage API에도 기대지 않음.
 * 49-215차: 26.3은 GLFW가 없고 SDL이다 - 실제로 창에 올리는 건 {@link LunaInput#setWindowIcon}이 버전에 맞게
 * (GLFW 이미지 묶음 / SDL 표면) 한다. 호출 중 복사하므로 버퍼는 바로 해제.
 */
public final class WindowIcon {
	private WindowIcon() {
	}

	private static final int[] SIZES = {16, 24, 32, 48, 64, 128};
	private static boolean applied;

	public static void apply(Minecraft client) {
		if (applied || client == null) {
			return;
		}
		applied = true;
		List<ByteBuffer> buffers = new ArrayList<>();
		List<int[]> sizes = new ArrayList<>();
		try {
			for (int size : SIZES) {
				byte[] data = read("/assets/lunaslight/icon/" + size + ".rgba");
				if (data == null || data.length != size * size * 4) {
					continue;
				}
				ByteBuffer buf = MemoryUtil.memAlloc(data.length);
				buf.put(data).flip();
				buffers.add(buf);
				sizes.add(new int[]{size});
			}
			if (!sizes.isEmpty() && LunaInput.setWindowIcon(client, sizes, buffers)) {
				LunaClientMod.LOGGER.info("[Nova] 창 아이콘 적용(" + sizes.size() + "개 크기)");
			}
		} catch (Throwable t) {
			LunaClientMod.LOGGER.warn("[Nova] 창 아이콘 적용 실패", t);
		} finally {
			for (ByteBuffer b : buffers) {
				MemoryUtil.memFree(b);
			}
		}
	}

	private static byte[] read(String path) {
		try (InputStream in = WindowIcon.class.getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		} catch (Throwable t) {
			return null;
		}
	}
}
