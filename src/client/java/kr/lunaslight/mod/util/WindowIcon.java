package kr.lunaslight.mod.util;

import kr.lunaslight.mod.LunaClientMod;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-23차: "마크 윈도우 아이콘을 루나 아이콘으로" - 게임 창(제목 표시줄/작업 표시줄) 아이콘 교체.
 * 49-24차: "런처랑 헷갈려서 - 일반 마크 모양에 런처 모양 약간만" → 바닐라 잔디 블록 아이콘(assets 인덱스의
 * icons/icon_*.png) 오른쪽 아래에 Luna 로고 배지(약 42%, 어두운 둥근 바탕)를 얹은 합성본. 바닐라가 MinecraftClient 생성 시 잔디 블록 아이콘을 넣은 뒤(CLIENT_STARTED 이후)
 * GLFW.glfwSetWindowIcon으로 덮어쓴다. 픽셀은 PNG 디코딩 없이 미리 만들어 둔 원시 RGBA(assets/lunaslight/icon/
 * 16·24·32·48·64·128.rgba, 런처 build/icon.png에서 리샘플)를 그대로 올린다 - 어떤 버전의 NativeImage API에도
 * 기대지 않음. GLFW가 호출 중 복사하므로 버퍼는 바로 해제. macOS는 창 아이콘 개념이 없어 GLFW가 무시한다.
 */
public final class WindowIcon {
	private WindowIcon() {
	}

	private static final int[] SIZES = {16, 24, 32, 48, 64, 128};
	private static boolean applied;

	public static void apply(MinecraftClient client) {
		if (applied || client == null) {
			return;
		}
		applied = true;
		List<ByteBuffer> buffers = new ArrayList<>();
		GLFWImage.Buffer heapImages = null;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			long handle = WindowAccess.of(client).getHandle();
			GLFWImage.Buffer images = stackImages(SIZES.length, stack);
			if (images == null) {
				// 스택용 이름을 둘 다 못 찾은 경우 - 힙에 잡고 끝나고 직접 해제(아래 finally)
				images = GLFWImage.malloc(SIZES.length);
				heapImages = images;
			}
			int n = 0;
			for (int size : SIZES) {
				byte[] data = read("/assets/lunaslight/icon/" + size + ".rgba");
				if (data == null || data.length != size * size * 4) {
					continue;
				}
				ByteBuffer buf = MemoryUtil.memAlloc(data.length);
				buf.put(data).flip();
				buffers.add(buf);
				images.position(n).width(size).height(size).pixels(buf);
				n++;
			}
			if (n == 0) {
				return;
			}
			images.position(0);
			images.limit(n);
			GLFW.glfwSetWindowIcon(handle, images);
			LunaClientMod.LOGGER.info("[Nova] 창 아이콘 적용(" + n + "개 크기)");
		} catch (Throwable t) {
			LunaClientMod.LOGGER.warn("[Nova] 창 아이콘 적용 실패", t);
		} finally {
			if (heapImages != null) {
				heapImages.free();
			}
			for (ByteBuffer b : buffers) {
				MemoryUtil.memFree(b);
			}
		}
	}

	/**
	 * GLFWImage 버퍼를 MemoryStack에 잡는다. <b>이름이 LWJGL 세대마다 달라서</b> 리플렉션으로 고른다
	 * (창 아이콘은 게임 한 번 켤 때 딱 한 번 도는 코드라 리플렉션 비용이 문제되지 않는다):
	 *
	 * <pre>
	 *   LWJGL 3.2  (1.15.2~1.18.2) : mallocStack(int, MemoryStack)만 있음
	 *   LWJGL 3.3  (1.19~1.21.x)   : 둘 다 있음 (mallocStack은 deprecated)
	 *   그 이후    (26.1~26.2)      : mallocStack이 **삭제됨** - malloc(int, MemoryStack)만 있음
	 * </pre>
	 *
	 * 49-36차엔 "두 시대 모두 있는 mallocStack"이 정답이었는데, 26.x가 LWJGL을 올리면서 그게 사라져
	 * 26.1/26.1.1/26.1.2/26.2 네 개가 전부 이 한 줄에서 깨졌다(49-52차, 40개 전체 빌드에서 발견).
	 * 이제는 어느 쪽 이름이든 있으면 쓰고, 둘 다 없으면 호출부가 힙 할당으로 넘어간다.
	 *
	 * <p>⚠️ 이 자리는 로컬 javac 검증으로 못 잡는다 - 컨테이너엔 진짜 LWJGL jar가 없어서 손으로 만든
	 * 스텁(org/lwjgl/glfw/GLFWImage.java)으로 컴파일하기 때문에, 스텁에 있는 메서드는 전부 "있는" 것이 된다.
	 * 스텁은 <b>가장 빡빡한 세대(26.x = mallocStack 없음)</b>에 맞춰 두어 이런 게 다시 새지 않게 한다.
	 */
	private static GLFWImage.Buffer stackImages(int count, MemoryStack stack) {
		for (String name : new String[]{"malloc", "mallocStack"}) {
			try {
				java.lang.reflect.Method m = GLFWImage.class.getMethod(name, int.class, MemoryStack.class);
				if (m.invoke(null, count, stack) instanceof GLFWImage.Buffer buffer) {
					return buffer;
				}
			} catch (Throwable ignored) {
				// 다음 후보 이름으로
			}
		}
		return null;
	}

	private static byte[] read(String path) {
		try (InputStream in = WindowIcon.class.getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		} catch (Throwable t) {
			return null;
		}
	}
}
