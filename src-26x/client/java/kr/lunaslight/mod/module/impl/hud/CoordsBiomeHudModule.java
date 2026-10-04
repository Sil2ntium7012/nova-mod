package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * ⚠️ 컴파일 확인 필요: client.world.getBiome(BlockPos)는 1.21.1(월드 리팩터 이후)에서
 * RegistryEntry<Biome>를 반환합니다. 여기서는 RegistryEntry#getKey()(Optional<RegistryKey<Biome>>)를
 * 사용해 registry key의 path를 꺼내는 방식으로 작성했습니다. 만약 실제 반환 타입/메서드명이
 * 다르면(예: getKeyOrValue 등) 이 부분만 손보면 됩니다.
 *
 * 35차: getBiome(BlockPos)의 반환 형태가 1.18+(RegistryEntry<Biome>)와 1.16~1.17.1(Biome 직접
 * 반환) 사이에서도 갈리는 게 확인돼(claude/nova-mod-todo.md 33차), RegistryEntry/RegistryKey를
 * 컴파일 타임 타입으로 쓰지 않고 LunaCompat.getBiomeName(world, pos)로 완전히 이관.
 */
public class CoordsBiomeHudModule extends Module {

	private final PositionSetting position;
	private final ColorSetting textColor;
	private final BooleanSetting showBiome;
	private final BooleanSetting showCoords;

	public CoordsBiomeHudModule() {
		super("coords_biome_hud", "좌표", ModuleCategory.HUD, "좌표와 바이옴");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 106)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		showBiome = register(new BooleanSetting("show_biome", "바이옴", "바이옴 이름을 함께 표시합니다.", true));
		showCoords = register(new BooleanSetting("show_coords", "좌표", "좌표를 표시합니다.", true));
		enableHudStyle();
	}

	/**
	 * 49-32차(사용자: "바이옴도 영어로 돼있다"): 마인크래프트 번역 키(biome.minecraft.<이름>)로 먼저
	 * 옮기고, 그 언어에 번역이 없을 때만 예전처럼 영어 이름을 다듬어 쓴다.
	 */
	private String formatBiomeName(String path) {
		String key = "biome.minecraft." + path;
		String translated = LunaCompat.translate(key);
		if (translated != null && !translated.isEmpty() && !translated.equals(key)) {
			return translated;
		}
		String[] parts = path.split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(" ");
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		boolean sample = client.player == null || client.level == null;
		if (sample && !isPreview()) {
			return;
		}
		if (!showCoords.get() && !showBiome.get()) {
			return;
		}

		// 49-34차(사용자: "좌표랑 바이옴 한줄 말고 위아래로"): 좌표 한 줄, 바이옴 한 줄로 쌓는다.
		java.util.List<String> lines = new java.util.ArrayList<>(2);
		if (showCoords.get()) {
			// 49-26차: 월드 밖 미리보기는 예시 좌표로
			if (sample) {
				lines.add("X: 128 Y: 71 Z: -240");
			} else {
				BlockPos pos = client.player.blockPosition();
				lines.add("X: " + pos.getX() + " Y: " + pos.getY() + " Z: " + pos.getZ());
			}
		}
		if (showBiome.get()) {
			// 49-49차(사용자: "좌표에 바이옴 표시하는 거 바이옴: 이런식으로"): 좌표 줄이 "X: … Y: …"인데
			// 바이옴 줄만 이름만 덩그러니 있어 무슨 값인지 안 보였다 - 같은 어법으로 "바이옴: " 접두.
			if (sample) {
				lines.add("바이옴: 평원");
			} else {
				String biomePath = LunaCompat.getBiomeName(client.level, client.player.blockPosition());
				lines.add("바이옴: " + (biomePath != null ? formatBiomeName(biomePath) : "알 수 없음"));
			}
		}
		if (lines.isEmpty()) {
			return;
		}

		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), hudLinesWidth(lines));
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), hudLinesHeight(lines));
		drawHudLines(context, lines, x, y, textColor.getArgb());
	}
}
