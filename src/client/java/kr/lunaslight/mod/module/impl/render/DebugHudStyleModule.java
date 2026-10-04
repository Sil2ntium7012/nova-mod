package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.util.Identifier;
import kr.lunaslight.mod.util.LunaPerf;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-22차: F3 꾸미기(사용자 요청).
 *
 * 바닐라 F3(디버그 화면)은 DebugHudMixin이 통째로 취소하고(차트·"F3 + Q로 도움말" 안내 등 전부 사라짐),
 * 이 모듈이 HUD 패스에서 골라 그린다(rendersOnDebugHud = true - 다른 Luna HUD는 F3 중 숨김 유지).
 *   왼쪽: 마크 버전 · FPS · XYZ · Block · Chunk · Facing · 월드(차원 · 바이옴)
 *   오른쪽: 자바 버전 · 메모리 · CPU 점유율
 * 항목별 on/off, 파스텔 톤 기본 색(라벨/값/배경 변경 가능), 가운데 십자선(축 표시 / 일반 십자) 선택.
 * 값은 틱마다(20회/초) 문자열로 만들어 두고 프레임에서는 그리기만 한다(프레임 드랍 방지).
 */
public class DebugHudStyleModule extends Module {

	public enum Crosshair {
		AXIS, NORMAL;

		@Override
		public String toString() {
			return this == AXIS ? "축 표시(기본)" : "일반 십자";
		}
	}

	private final BooleanSetting showVersion = register(new BooleanSetting("show_version", "버전", "마인크래프트 버전을 표시합니다.", true));
	private final BooleanSetting showFps = register(new BooleanSetting("show_fps", "FPS", "초당 프레임을 표시합니다.", true));
	private final BooleanSetting showXyz = register(new BooleanSetting("show_xyz", "좌표", "정확한 좌표를 표시합니다.", true));
	private final BooleanSetting showBlock = register(new BooleanSetting("show_block", "블록", "블록 좌표를 표시합니다.", true));
	private final BooleanSetting showChunk = register(new BooleanSetting("show_chunk", "청크", "청크 좌표를 표시합니다.", true));
	private final BooleanSetting showFacing = register(new BooleanSetting("show_facing", "방향", "바라보는 방향을 표시합니다.", true));
	private final BooleanSetting showWorld = register(new BooleanSetting("show_world", "월드", "차원과 바이옴을 표시합니다.", true));
	// 49-48차(사용자: "F3에 내가 있는 곳 빛도 뜨게 해줘") - 몹 스폰 여부를 볼 때 쓰는 값이라 눈에 띄게.
	private final BooleanSetting showLight = register(new BooleanSetting("show_light", "밝기", "서 있는 자리의 밝기(하늘/블록)를 표시합니다.", true));
	private final BooleanSetting showJava = register(new BooleanSetting("show_java", "자바", "자바 버전을 표시합니다.", true));
	private final BooleanSetting showMemory = register(new BooleanSetting("show_memory", "메모리", "메모리 사용량을 표시합니다.", true));
	private final BooleanSetting showCpu = register(new BooleanSetting("show_cpu", "CPU", "이 게임의 CPU 점유율을 표시합니다.", true));
	private final EnumSetting<Crosshair> crosshair = register(new EnumSetting<>(
			"crosshair", "십자선", "F3 화면 가운데 표시 방식입니다.", Crosshair.AXIS, Crosshair.class));

	// 파스텔 톤 기본 색
	private final ColorSetting labelColor = register(new ColorSetting("label_color", "항목 색", "항목 이름의 색입니다.", 0xFFB9D4FF));
	private final ColorSetting valueColor = register(new ColorSetting("value_color", "값 색", "값의 색입니다.", 0xFFFFF1B8));
	private final ColorSetting titleColor = register(new ColorSetting("title_color", "제목 색", "첫 줄(버전, 자바)의 색입니다.", 0xFFFFC6DD));
	private final BooleanSetting background = register(new BooleanSetting("background", "배경", "글자 뒤에 반투명 상자를 깝니다.", false).style());
	private final ColorSetting bgColor = register(new ColorSetting("bg_color", "배경 색", "상자의 색입니다.", 0x70000000));

	private static volatile boolean enabledStatic;
	private static volatile boolean normalCrosshairStatic;

	public DebugHudStyleModule() {
		super("debug_hud_style", "F3 화면", ModuleCategory.HUD, "필요한 항목만 보이는 F3 화면");
		defaultEnabled(true);   // 49-121차(사용자): 기본 활성화
		background.withColor(bgColor); // 49-23차: 배경 스위치 옆에 색 견본
	}

	/** DebugHudMixin용: 바닐라 F3 렌더를 취소할지. */
	public static boolean replacesVanilla() {
		return enabledStatic;
	}

	/** CrosshairHideMixin용: F3 중 바닐라 축 십자선 대신 일반 십자를 그릴지. */
	public static boolean wantsNormalCrosshair() {
		return enabledStatic && normalCrosshairStatic;
	}

	@Override
	protected void onEnable() {
		enabledStatic = true;
	}

	@Override
	protected void onDisable() {
		enabledStatic = false;
	}

	@Override
	public boolean rendersOnDebugHud() {
		return true;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	// ==================== 틱 데이터 ====================

	/**
	 * 49-32차: 값 색을 줄마다 따로 줄 수 있게(0이면 공통 값 색). label이 비고 title이 아니면
	 * "한 칸 띄우기"용 빈 줄이다.
	 */
	private record Line(String label, String value, boolean title, int color) {
		Line(String label, String value, boolean title) {
			this(label, value, title, 0);
		}

		static Line gap() {
			return new Line("", "", false, 0);
		}

		boolean isGap() {
			return !title && label.isEmpty() && value.isEmpty();
		}
	}

	private final List<Line> left = new ArrayList<>();
	private final List<Line> right = new ArrayList<>();
	private long lastCpuSampleMs;
	private double cpuLoad = -1;
	private Object osBean;
	private java.lang.reflect.Method cpuMethod;
	private boolean cpuResolved;

	private double sampleCpu() {
		long now = System.currentTimeMillis();
		if (now - lastCpuSampleMs < 1000) {
			return cpuLoad;
		}
		lastCpuSampleMs = now;
		try {
			if (!cpuResolved) {
				cpuResolved = true;
				osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
				for (java.lang.reflect.Method m : osBean.getClass().getMethods()) {
					if (m.getName().equals("getProcessCpuLoad") && m.getParameterCount() == 0) {
						m.setAccessible(true);
						cpuMethod = m;
						break;
					}
				}
				if (cpuMethod == null) {
					Class<?> sun = LunaCompat.classOrNull("com.sun.management.OperatingSystemMXBean");
					if (sun != null && sun.isInstance(osBean)) {
						cpuMethod = sun.getMethod("getProcessCpuLoad");
					}
				}
			}
			if (cpuMethod != null) {
				Object v = cpuMethod.invoke(osBean);
				if (v instanceof Number n && n.doubleValue() >= 0) {
					cpuLoad = n.doubleValue();
				}
			}
		} catch (Throwable ignored) {
			cpuLoad = -1;
		}
		return cpuLoad;
	}

	private static String facing(float yaw) {
		float y = yaw % 360f;
		if (y < 0) {
			y += 360f;
		}
		// 마크 규약: 남=0, 서=90, 북=180, 동=270
		if (y >= 315 || y < 45) {
			return "남 (+Z)";
		} else if (y < 135) {
			return "서 (-X)";
		} else if (y < 225) {
			return "북 (-Z)";
		}
		return "동 (+X)";
	}

	private String dimensionName() {
		try {
			Object key = LunaCompat.findNoArgMethod(client.world.getClass(), "getRegistryKey").invoke(client.world);
			Object value = LunaCompat.findNoArgMethod(key.getClass(), "getValue").invoke(key);
			String s = String.valueOf(value);
			return s.startsWith("minecraft:") ? s.substring(10) : s;
		} catch (Throwable ignored) {
			return "?";
		}
	}

	@Override
	public void onTick() {
		enabledStatic = isEnabled();
		normalCrosshairStatic = crosshair.get() == Crosshair.NORMAL;
		if (!LunaCompat.isDebugHudShown(client) || client.player == null || client.world == null) {
			return;
		}
		buildLines(false);
	}

	private void buildLines(boolean sample) {
		left.clear();
		right.clear();
		if (showVersion.get()) {
			left.add(new Line("마인크래프트 " + LunaVersion.current(), "", true));
		}
		if (showFps.get()) {
			int fps = sample ? 600 : LunaPerf.fps();
			// 49-32차(사용자: "FPS가 높은 건지 낮은 건지 색이 항상 같아서 모르겠다")
			left.add(new Line("FPS", Integer.toString(fps), false, fpsColor(fps)));
		}
		if (!left.isEmpty()) {
			left.add(Line.gap());
		}
		Vec3d pos = sample ? new Vec3d(128.50, 64.00, -256.50) : LunaCompat.getPos(client.player);
		if (pos != null) {
			if (showXyz.get()) {
				// 49-32차: 어느 값이 X·Y·Z인지 보이도록 축 글자를 붙이고 축마다 색을 준다
				// (마인크래프트 F3 축 색과 같은 결: X 빨강 · Y 연두 · Z 파랑).
				left.add(new Line("좌표", String.format("X %.2f  Y %.2f  Z %.2f", pos.x, pos.y, pos.z), false));
			}
			int bx = (int) Math.floor(pos.x), by = (int) Math.floor(pos.y), bz = (int) Math.floor(pos.z);
			if (showBlock.get()) {
				left.add(new Line("블록", bx + " " + by + " " + bz, false));
			}
			if (showChunk.get()) {
				left.add(new Line("청크", (bx & 15) + " " + (by & 15) + " " + (bz & 15)
						+ " | " + (bx >> 4) + " " + (by >> 4) + " " + (bz >> 4), false));
			}
			if (showFacing.get()) {
				float yaw = sample ? 180f : LunaCompat.getYaw(client.player);
				float pitch = sample ? 0f : LunaCompat.getPitch(client.player);
				left.add(new Line("방향", facing(yaw) + String.format(" (%.1f / %.1f)", wrap(yaw), pitch), false));
			}
			if (showWorld.get() && !left.isEmpty()) {
				left.add(Line.gap());
			}
			if (showWorld.get()) {
				String dim = sample ? "오버월드" : koreanDimension(dimensionName());
				String biome = sample ? "평원" : LunaCompat.getBiomeName(client.world, new BlockPos(bx, by, bz));
				if (biome != null && !sample) {
					String ko = LunaCompat.translate("biome.minecraft." + biome); // 게임 언어가 한국어면 한글 이름
					if (ko != null && !ko.isEmpty() && !ko.startsWith("biome.")) {
						biome = ko;
					}
				}
				left.add(new Line("월드", dim + (biome == null ? "" : " | " + biome), false));
			}
			if (showLight.get()) {
				// 49-48차: 합친 밝기 + (하늘/블록). 블록 밝기 0이면 몹이 뜰 수 있는 자리라 주황으로.
				int[] light = sample ? new int[]{15, 15, 0} : LunaCompat.lightLevelsAt(client);
				if (light != null) {
					int color = light[2] <= 0 ? 0xFFFFB870 : 0;
					String value = light[0] + "  §8하늘 " + light[1] + " | 블록 " + light[2];
					left.add(color == 0 ? new Line("밝기", value, false) : new Line("밝기", value, false, color));
				}
			}
		}
		if (showJava.get()) {
			right.add(new Line("자바 " + System.getProperty("java.version") + " " + (is64() ? "64비트" : "32비트"), "", true));
		}
		if (showMemory.get() && !right.isEmpty()) {
			right.add(Line.gap());
		}
		if (showMemory.get()) {
			long used = sample ? 1024 : LunaPerf.usedMemMb();
			long max = sample ? 4096 : LunaPerf.maxMemMb();
			int pct = max <= 0 ? 0 : (int) Math.round(used * 100.0 / max);
			right.add(new Line("메모리", pct + "% " + used + " / " + max + " MB", false));
		}
		if (showCpu.get()) {
			double cpu = sample ? 0.12 : sampleCpu();
			right.add(new Line("CPU", cpu < 0 ? "-" : Math.round(cpu * 100) + "%", false));
		}
	}

	/** 차원 id → 한글(알 수 없으면 그대로). */
	private static String koreanDimension(String dim) {
		if (dim == null) {
			return "?";
		}
		return switch (dim) {
			case "overworld", "minecraft:overworld" -> "오버월드";
			case "the_nether", "minecraft:the_nether" -> "네더";
			case "the_end", "minecraft:the_end" -> "엔드";
			default -> dim;
		};
	}

	private static float wrap(float yaw) {
		float y = yaw % 360f;
		if (y > 180f) {
			y -= 360f;
		} else if (y < -180f) {
			y += 360f;
		}
		return y;
	}

	private static boolean is64() {
		String arch = System.getProperty("sun.arch.data.model", System.getProperty("os.arch", ""));
		return arch.contains("64");
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			buildLines(true);
			int x = previewX() + 6, y = previewY() + 6;
			// 49-88차(8-12): 오른쪽 열(자바·메모리·CPU)도 그려야 그 스위치들이 미리보기에 보인다
			drawColumn(context, left, x, y, false, previewW() / 2 - 8);
			drawColumn(context, right, previewX() + previewW() - 6, y, true, previewW() / 2 - 8);
			return;
		}
		if (!LunaCompat.isDebugHudShown(client) || client.player == null) {
			return;
		}
		int sw = WindowAccess.of(client).getScaledWidth();
		drawColumn(context, left, 4, 4, false, sw / 2);
		drawColumn(context, right, sw - 4, 4, true, sw / 2);
		if (normalCrosshairStatic) {
			drawPlainCrosshair(context, sw, WindowAccess.of(client).getScaledHeight());
		}
	}

	/**
	 * 49-47차(사용자: "F3 크로스헤어 기존 크로스헤어랑 너무 달라, 커스텀 크로스헤어 쓰고 있으면 그걸로,
	 * 아니면 마크 기본인데 위치도 어긋나고 너무 커"):
	 *  · 예전엔 직접 그린 11px 흰 십자였다 - 바닐라(15×15 스프라이트)보다 크고, {@code +1} 비대칭 때문에
	 *    한 픽셀 어긋나 있었다.
	 *  · 커스텀 크로스헤어를 쓰는 중이면 <b>아무것도 그리지 않는다</b> - 그쪽이 자기 걸 그린다(두 개 겹침 방지).
	 *  · 아니면 <b>바닐라 십자 그림 그대로</b>를 바닐라와 같은 자리((폭−15)/2)에 그린다
	 *    (1.20.2+ sprites/hud/crosshair.png, 그 아래 버전은 icons.png의 0,0 자리).
	 */
	private void drawPlainCrosshair(DrawContext context, int sw, int sh) {
		if (customCrosshairActive()) {
			return;
		}
		int x = (sw - 15) / 2;
		int y = (sh - 15) / 2;
		Identifier modern = LunaCompat.identifier("minecraft", "textures/gui/sprites/hud/crosshair.png");
		if (LunaGfx.drawTex(context, modern, x, y, 15, 15, 0, 0, 15, 15, 15, 0xFFFFFFFF)) {
			return;
		}
		Identifier icons = LunaCompat.identifier("minecraft", "textures/gui/icons.png");
		if (LunaGfx.drawTex(context, icons, x, y, 15, 15, 0, 0, 15, 15, 256, 0xFFFFFFFF)) {
			return;
		}
		// 그림을 못 쓰는 환경: 바닐라와 같은 크기(15×15 안의 13px 선, 1px 굵기)로 직접
		context.fill(x + 7, y + 1, x + 8, y + 14, 0xFFFFFFFF);
		context.fill(x + 1, y + 7, x + 14, y + 8, 0xFFFFFFFF);
	}

	/** 커스텀 크로스헤어 기능이 켜져 있고 바닐라를 숨기는 중인가. */
	private static boolean customCrosshairActive() {
		return kr.lunaslight.mod.module.ModuleManager.get().find("custom_crosshair")
			.map(m -> m instanceof kr.lunaslight.mod.module.impl.render.CustomCrosshairModule c && c.shouldHideVanilla())
			.orElse(false);
	}

	private void drawColumn(DrawContext context, List<Line> lines, int x, int y, boolean rightAlign, int maxW) {
		int lh = 11;
		int lc = labelColor.getArgb(), vc = valueColor.getArgb(), tc = titleColor.getArgb();
		for (Line l : lines) {
			if (l.isGap()) {
				y += lh / 2 + 1;   // 묶음 사이 한 칸 띄우기
				continue;
			}
			String label = l.title ? l.label : l.label + ": ";
			String text = label + l.value;
			int w = LunaCompat.getTextWidth(client.textRenderer, text);
			int lx = rightAlign ? x - w : x;
			if (background.get()) {
				context.fill(lx - 2, y - 1, lx + w + 2, y + lh - 1, bgColor.getArgb());
			}
			int labelW = LunaCompat.getTextWidth(client.textRenderer, label);
			LunaCompat.drawHudText(context, client.textRenderer, label, lx, y, l.title ? tc : lc);
			if (!l.value.isEmpty()) {
				int useColor = l.color != 0 ? l.color : vc;
				if ("좌표".equals(l.label)) {
					drawAxisValue(context, l.value, lx + labelW, y);
				} else {
					LunaCompat.drawHudText(context, client.textRenderer, l.value, lx + labelW, y, useColor);
				}
			}
			y += lh;
		}
	}

	/** 프레임 수에 따른 색(높을수록 초록, 낮을수록 빨강). */
	private static int fpsColor(int fps) {
		if (fps >= 144) {
			return 0xFF8BE07A;
		}
		if (fps >= 60) {
			return 0xFFD7E7A8;
		}
		if (fps >= 30) {
			return 0xFFF0C24A;
		}
		return 0xFFE86A5E;
	}

	/** "X 1.00  Y 2.00  Z 3.00"을 축 글자마다 다른 색으로. */
	private void drawAxisValue(DrawContext context, String value, int x, int y) {
		int[] axisColors = {0xFFE86A5E, 0xFF8BE07A, 0xFF7FB6F5};
		String[] parts = value.split("(?=[XYZ] )");
		int cx = x;
		int ai = 0;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			int color = ai < axisColors.length ? axisColors[ai] : valueColor.getArgb();
			LunaCompat.drawHudText(context, client.textRenderer, part, cx, y, color);
			cx += LunaCompat.getTextWidth(client.textRenderer, part);
			ai++;
		}
	}
}
