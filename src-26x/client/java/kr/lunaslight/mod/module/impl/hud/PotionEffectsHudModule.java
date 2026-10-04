package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 효과 HUD.
 *
 * 19차/35차: StatusEffectInstance#getEffectType()이 RegistryEntry<StatusEffect>(신형)/StatusEffect(구형)를
 * 반환 - value() 메서드 존재 여부로 리플렉션 덕타이핑(resolveEffect).
 *
 * 49-22차 재작성(사용자: "아이콘·시간·단계 예쁘게, 위치 오른쪽 중간"):
 *  - 효과마다 한 줄 카드: [바닐라 효과 아이콘 18px] [이름 + 단계(로마자)] / [남은 시간]. 아이콘은
 *    textures/mob_effect/<이름>.png(모드 효과는 그 모드 네임스페이스) - 번역 키(effect.<ns>.<name>)에서 유도.
 *  - 기본 위치 화면 오른쪽 세로 가운데(HudPosition.RIGHT_CENTER, 49-22차 신설). 오른쪽 정렬이라 이름
 *    길이가 달라도 아이콘 줄이 맞는다.
 *  - 남은 시간 10초 미만이면 주황, 3초 미만이면 깜빡임. 이름/아이콘 경로는 효과 객체별로 캐시(리플렉션 1회).
 */
public class PotionEffectsHudModule extends Module {

	private final PositionSetting position;
	private final ColorSetting textColor;
	private final BooleanSetting showDuration;
	private final BooleanSetting showIcons;
	private final BooleanSetting compact;

	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private static final int ICON = 18;
	private static final int ROW_H = 24;
	private static final int ROW_GAP = 3;

	public PotionEffectsHudModule() {
		super("potion_effects_hud", "효과", ModuleCategory.HUD, "걸린 포션 효과와 남은 시간");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 112)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		showDuration = register(new BooleanSetting("show_duration", "남은 시간", "효과의 남은 시간을 표시합니다.", true));
		showIcons = register(new BooleanSetting("show_icons", "아이콘", "효과 아이콘을 표시합니다.", true));
		compact = register(new BooleanSetting("compact", "한 줄", "이름과 시간을 한 줄에 표시합니다.", false));
		enableHudStyle();
	}

	private static String romanFor(int amplifier) {
		int level = amplifier + 1;
		if (level >= 0 && level < ROMAN.length) {
			return ROMAN[level];
		}
		return String.valueOf(level);
	}

	/**
	 * 49-47차(사용자: "버프창 뜨는 거 무한인데 계속 깜빡깜빡거려"): 1.19.4+는 무한 지속을 <b>-1</b>로 준다.
	 * 그런데 "남은 시간 3초 미만이면 깜빡임" 판정이 {@code ticks < 60}이라 -1이 걸려 무한 효과가
	 * 계속 깜빡이고 색도 빨갛게 나왔다. 무한은 따로 가려낸다.
	 */
	private static boolean isInfinite(int ticks) {
		return ticks < 0 || ticks > 20 * 60 * 60 * 24;
	}

	private static String formatDuration(int ticks) {
		if (isInfinite(ticks)) {
			return "무한"; // 49-23차: 뫼비우스 기호 대신 글자로
		}
		int totalSeconds = ticks / 20;
		if (totalSeconds >= 60) {
			return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
		}
		return totalSeconds + "s";
	}

	/** 효과 객체별 [이름, 아이콘 Identifier(없으면 null)] 캐시 - 매 프레임 리플렉션/Text 생성 제거. */
	private static final Map<Object, Object[]> INFO = new IdentityHashMap<>();

	private static Object[] infoFor(Object effectType) {
		Object[] cached = INFO.get(effectType);
		if (cached != null) {
			return cached;
		}
		Object effect = effectType;
		try {
			java.lang.reflect.Method value = LunaCompat.findNoArgMethod(effectType.getClass(), "value");
			if (value != null) {
				effect = value.invoke(effectType);
			}
		} catch (Throwable ignored) {
		}
		String name;
		Identifier icon = null;
		try {
			name = ((MobEffect) effect).getDisplayName().getString();
		} catch (Throwable t) {
			name = "?";
		}
		try {
			String key = ((MobEffect) effect).getDescriptionId(); // effect.minecraft.speed
			String[] parts = key.split("\\.");
			if (parts.length >= 3) {
				icon = LunaCompat.identifier(parts[1], "textures/mob_effect/" + parts[parts.length - 1] + ".png");
			}
		} catch (Throwable ignored) {
		}
		Object[] info = {name, icon};
		if (INFO.size() > 256) {
			INFO.clear();
		}
		INFO.put(effectType, info);
		return info;
	}

	/** 한 줄 표시 데이터. */
	private record Row(String title, String time, Identifier icon, int durationTicks) {
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		List<Row> rows = new ArrayList<>();
		if (isPreview()) {
			rows.add(new Row("신속 II", "2:30", LunaCompat.identifier("minecraft", "textures/mob_effect/speed.png"), 3000));
			rows.add(new Row("재생", "0:08", LunaCompat.identifier("minecraft", "textures/mob_effect/regeneration.png"), 160));
		} else {
			if (client.player == null) {
				return;
			}
			// 49-47차(사용자: "조합법 가이드 창 뒤에 버프 뜨는데 버프 위치 옮겨줘"): HUD는 화면보다 먼저
			// 그려지므로, 인벤토리/작업대 화면을 열면 효과 카드가 그 뒤에 깔려 가장자리만 삐져나온다
			// (제작 도우미 패널이 바로 그 자리다). 화면이 열려 있는 동안은 아예 그리지 않는다 -
			// 인벤토리 HUD가 49-41차에 같은 이유로 택한 방식과 동일.
			if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
				return;
			}
			Collection<MobEffectInstance> effects = client.player.getActiveEffects();
			if (effects.isEmpty()) {
				return;
			}
			for (MobEffectInstance instance : effects) {
				Object[] info = infoFor(instance.getEffect());
				String level = romanFor(instance.getAmplifier());
				String title = info[0] + (level.isEmpty() ? "" : " " + level);
				rows.add(new Row(title, formatDuration(instance.getDuration()), (Identifier) info[1], instance.getDuration()));
			}
		}

		boolean icons = showIcons.get();
		boolean dur = showDuration.get();
		boolean oneLine = compact.get() || !dur;
		int rowH = oneLine ? 20 : ROW_H;
		int maxW = 0;
		for (Row r : rows) {
			int w = LunaCompat.getTextWidth(client.font, r.title);
			if (oneLine && dur) {
				w += 6 + LunaCompat.getTextWidth(client.font, r.time);
			} else if (!oneLine) {
				w = Math.max(w, LunaCompat.getTextWidth(client.font, r.time));
			}
			maxW = Math.max(maxW, w);
		}
		int rowW = (icons ? ICON + 5 : 0) + maxW + 10;
		int totalH = rows.size() * rowH + (rows.size() - 1) * ROW_GAP;
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), rowW);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), totalH);

		long now = System.currentTimeMillis();
		int textCol = textColor.getArgb();
		for (Row r : rows) {
			drawHudPanelExact(context, x, y, rowW, rowH);
			int tx = x + 5;
			if (icons) {
				if (r.icon == null || !LunaGfx.drawTex(context, r.icon, x + 3, y + (rowH - ICON) / 2, ICON, ICON, 0, 0, 18, 18, 18, 0xFFFFFFFF)) {
					kr.lunaslight.mod.gui.LunaDraw.roundRect(context, x + 4, y + (rowH - 16) / 2, 16, 16, 3, 0x3AFFFFFF);
				}
				tx = x + 3 + ICON + 5;
			}
			boolean endless = isInfinite(r.durationTicks);
			int timeCol = endless ? ((0xC8 << 24) | (textCol & 0x00FFFFFF))
				: (r.durationTicks < 60 ? 0xFFFF8A65 : (r.durationTicks < 200 ? 0xFFFFC46B : ((0xC8 << 24) | (textCol & 0x00FFFFFF))));
			boolean blink = !endless && r.durationTicks < 60 && (now / 300) % 2 == 0;
			if (oneLine) {
				LunaCompat.drawHudText(context, client.font, r.title, tx, y + Math.round(rowH / 2f - LunaCompat.textVisualCenter()), textCol);
				if (dur && !blink) {
					int tw = LunaCompat.getTextWidth(client.font, r.title);
					LunaCompat.drawHudText(context, client.font, r.time, tx + tw + 6, y + Math.round(rowH / 2f - LunaCompat.textVisualCenter()), timeCol);
				}
			} else {
				// 49-41차: 두 줄(이름/시간)을 아이콘 높이(24) 안에서 글꼴별 시각 중심으로 위아래 대칭
				int mid = Math.round(LunaCompat.textVisualCenter());
				LunaCompat.drawHudText(context, client.font, r.title, tx, y + 7 - mid, textCol);
				if (!blink) {
					LunaCompat.drawHudText(context, client.font, r.time, tx, y + 17 - mid, timeCol);
				}
			}
			y += rowH + ROW_GAP;
		}
	}

	/** 행 하나의 배경(공용 HUD 스타일 설정 사용, 패딩 없이 정확한 크기). */
	private void drawHudPanelExact(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		drawHudPanel(context, x + 4, y + 4, w - 8, h - 8);
	}
}
