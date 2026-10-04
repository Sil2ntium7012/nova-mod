package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import kr.lunaslight.mod.util.VoiceState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 49-50차: 음성 채팅 - 지금 말하는 사람을 루나 스타일로 보여 준다.
 *
 * <p><b>범위를 분명히 해 둔다.</b> 루나가 자체 음성 서버를 돌리는 게 아니다(그러려면 중계 서버가 필요하고,
 * 그건 지금 없다). 대신 마인크래프트에서 사실상 표준인 <b>Simple Voice Chat</b>의 공개 애드온 API에
 * 붙어서, 그 모드가 이미 주고받는 소리의 "누가 말하는 중인지"만 우리 HUD로 그린다.
 * 그래서 이 기능은 <b>서버와 나 양쪽에 Simple Voice Chat이 깔려 있을 때만</b> 켤 수 있다
 * (없으면 기능 카드에 "Simple Voice Chat 모드 설치 필요"로 잠긴 채 뜬다 - 있는 척하지 않는다).
 *
 * <p>그리는 것:
 * <ul>
 *   <li>그룹에 들어가 있으면 맨 위에 그룹 이름 한 줄</li>
 *   <li>내 마이크가 꺼져 있으면 "마이크 꺼짐" 한 줄(붉게)</li>
 *   <li>지금 말하는 사람들 - 왼쪽에 음량 막대, 오른쪽에 이름(속삭이면 흐리게)</li>
 * </ul>
 * 아무도 말하지 않고 알릴 것도 없으면 <b>아무것도 그리지 않는다</b>(가만히 있는 HUD 상자를 화면에 남기지 않음).
 *
 * <p><b>성능.</b> 매 프레임 하는 일은 이미 만들어 둔 줄을 그리는 것뿐이다. 말하는 사람 목록은 2틱(0.1초)마다
 * 한 번만 다시 만들고(주변에 로드된 플레이어만 훑는다), 이름은 UUID별로 캐시해 둔다. 음량은 SVC 오디오
 * 스레드가 패킷 받을 때 계산해 둔 값을 읽기만 한다({@link VoiceState}).
 */
public class VoiceHudModule extends Module {

	/** 막대 폭/높이 - 아이템 칸 느낌의 얇은 세로 막대. */
	private static final int BAR_W = 2;
	private static final int BAR_H = 7;
	/** 막대와 이름 사이 여백. */
	private static final int NAME_X = BAR_W + 4;
	/** 마지막 소리 이후 이 시간 안쪽이면 "말하는 중"으로 본다. */
	private static final long AUDIO_WINDOW_MS = 700L;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 175)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "말하는 사람 이름의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	private final IntSetting maxPlayers = register(new IntSetting(
			"max_players", "최대 인원", "한 번에 보여 줄 사람 수입니다. 넘으면 최근에 말한 사람부터 보여 줍니다.",
			5, 1, 8, 1));
	private final BooleanSetting micWarn = register(new BooleanSetting(
			"mic_warn", "마이크 꺼짐 알림", "내 마이크가 음소거일 때 알려 줍니다.", true));
	private final BooleanSetting showGroup = register(new BooleanSetting(
			"show_group", "그룹 이름", "음성 채팅 그룹에 들어가 있으면 그룹 이름을 같이 보여 줍니다.", true));

	/** 재사용 버퍼 - 프레임/틱마다 새로 만들지 않는다. */
	private final List<UUID> audioIds = new ArrayList<>();
	private final List<UUID> speakers = new ArrayList<>();
	private final Map<UUID, String> nameCache = new HashMap<>();

	private Object lastWorld;
	private int tickCount;

	public VoiceHudModule() {
		super("voice_hud", "음성 채팅", ModuleCategory.HUD, "지금 말하는 사람");
		requiresMod("voicechat", "Simple Voice Chat");
		enableHudStyle();
	}

	@Override
	protected void onDisable() {
		speakers.clear();
		audioIds.clear();
	}

	// ==================== 목록 만들기(2틱마다) ====================

	@Override
	public void onTick() {
		if (client.level != lastWorld) {
			lastWorld = client.level;
			nameCache.clear();
			speakers.clear();
			VoiceState.clear();
		}
		if (++tickCount % 2 != 0) {
			return;
		}
		if (!VoiceState.connected() || client.level == null) {
			speakers.clear();
			return;
		}
		speakers.clear();
		// ① SVC가 직접 알려주는 "말하는 중" - 주변에 로드된 플레이어만 훑는다(보통 수십 명 이하)
		try {
			for (net.minecraft.world.entity.player.Player player : client.level.players()) {
				UUID id = player.getUUID();
				if (id != null && VoiceState.talking(id) && !speakers.contains(id)) {
					speakers.add(id);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("voice:players", t);
		}
		// ② 방금 소리가 온 사람(그룹 통화처럼 멀리 있어 월드에 없는 경우까지)
		VoiceState.collect(audioIds, AUDIO_WINDOW_MS);
		for (int i = 0; i < audioIds.size(); i++) {
			UUID id = audioIds.get(i);
			if (!speakers.contains(id)) {
				speakers.add(id);
			}
		}
		// 이름 순으로 고정 - 프레임마다 줄이 위아래로 튀지 않게
		speakers.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
	}

	/** UUID → 보여 줄 이름. 월드에 있으면 거기서, 없으면 탭 목록에서(둘 다 실패하면 UUID 앞 8자리). */
	private String nameOf(UUID id) {
		String cached = nameCache.get(id);
		if (cached != null) {
			return cached;
		}
		String name = null;
		try {
			net.minecraft.world.entity.player.Player player =
					client.level == null ? null : client.level.getPlayerByUUID(id);
			if (player != null) {
				name = player.getName().getString();
			}
		} catch (Throwable ignored) {
			// 아래 탭 목록으로
		}
		if (name == null || name.isEmpty()) {
			name = fromPlayerList(id);
		}
		if (name == null || name.isEmpty()) {
			name = id.toString().substring(0, 8);
		}
		nameCache.put(id, name);
		return name;
	}

	/** 탭 목록(ClientPlayNetworkHandler#getPlayerListEntry)에서 이름 찾기 - 버전마다 이름이 갈려 리플렉션. */
	private String fromPlayerList(UUID id) {
		try {
			Object handler = LunaCompat.getMethodCompat(client.getClass(), "getNetworkHandler").invoke(client);
			if (handler == null) {
				return null;
			}
			Object entry = LunaCompat.getMethodCompat(handler.getClass(), "getPlayerListEntry", UUID.class)
					.invoke(handler, id);
			if (entry == null) {
				return null;
			}
			Object profile = LunaCompat.getMethodCompat(entry.getClass(), "getProfile").invoke(entry);
			if (profile == null) {
				return null;
			}
			// GameProfile은 마인크래프트 클래스가 아니라(authlib) 난독화되지 않는다
			Object name = profile.getClass().getMethod("getName").invoke(profile);
			return name == null ? null : name.toString();
		} catch (Throwable ignored) {
			return null;
		}
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview()) {
			drawRows(context, showGroup.get() ? "우리 팀" : null, micWarn.get(), previewRows());   // 49-88차(8-12)
			return;
		}
		if (!VoiceState.connected()) {
			return;
		}
		String group = showGroup.get() ? VoiceState.groupName() : null;
		boolean micOff = micWarn.get() && VoiceState.micOff();
		int shown = Math.min(speakers.size(), maxPlayers.get());
		if (group == null && !micOff && shown == 0) {
			return; // 알릴 게 없으면 빈 상자도 안 남긴다
		}
		drawRows(context, group, micOff, shown);
	}

	private int previewRows() {
		return Math.min(2, maxPlayers.get());
	}

	/**
	 * 그룹 줄 + 마이크 줄 + 말하는 사람 {@code count}명을 한 상자에 그린다.
	 * 상자 크기 계산은 다른 여러 줄 HUD(좌표/바이옴)와 같은 글리프 실측 밴드 기준.
	 */
	private void drawRows(GuiGraphicsExtractor context, String group, boolean micOff, int count) {
		int rows = (group != null ? 1 : 0) + (micOff ? 1 : 0) + count;
		if (rows <= 0) {
			return;
		}
		// 폭 = 가장 긴 줄
		int textW = 0;
		if (group != null) {
			textW = Math.max(textW, LunaCompat.getTextWidth(client.font, group));
		}
		if (micOff) {
			textW = Math.max(textW, LunaCompat.getTextWidth(client.font, "마이크 꺼짐"));
		}
		for (int i = 0; i < count; i++) {
			textW = Math.max(textW, LunaCompat.getTextWidth(client.font, rowName(i)));
		}
		int w = NAME_X + textW;

		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		int totalH = client.font.lineHeight + (rows - 1) * hudLineH();
		int x = position.get().resolveX(screenW, w);
		int y = position.get().resolveY(screenH, totalH);

		float bandTop = LunaCompat.textBandTop();
		float bandBottom = LunaCompat.textBandBottom();
		int boxTop = y + Math.round(bandTop) - 3;
		int boxH = Math.round(bandBottom - bandTop) + 6 + (rows - 1) * hudLineH();
		drawHudPanel(context, x, boxTop + 4, w, boxH - 8);

		int row = 0;
		if (group != null) {
			LunaCompat.drawHudText(context, client.font, group, x + NAME_X, y, LunaTheme.TEXT_SECONDARY);
			row++;
		}
		if (micOff) {
			LunaCompat.drawHudText(context, client.font, "마이크 꺼짐",
					x + NAME_X, y + row * hudLineH(), LunaTheme.DANGER);
			row++;
		}
		for (int i = 0; i < count; i++, row++) {
			int ry = y + row * hudLineH();
			drawLevelBar(context, x, ry, rowLevel(i));
			boolean whisper = !isPreview() && i < speakers.size() && VoiceState.whispering(speakers.get(i));
			int color = whisper ? LunaTheme.TEXT_SECONDARY : textColor.getArgb();
			LunaCompat.drawHudText(context, client.font, rowName(i), x + NAME_X, ry, color);
		}
	}

	private String rowName(int index) {
		if (isPreview()) {
			return index == 0 ? "하이" : "노바";
		}
		return index < speakers.size() ? nameOf(speakers.get(index)) : "";
	}

	/**
	 * 그 줄의 음량(0~1). 실제 값이 없는 경우(내 목소리는 되돌아오지 않으므로 수신 패킷이 없다)에는
	 * 0으로 두지 않고 기본 높이로 둔다 - 줄이 떠 있다는 것 자체가 "말하는 중"이라는 뜻이라서,
	 * 막대가 비어 있으면 고장 난 것처럼 보인다.
	 */
	private float rowLevel(int index) {
		if (isPreview()) {
			return index == 0 ? 0.85f : 0.4f;
		}
		if (index >= speakers.size()) {
			return 0.35f;
		}
		return Math.max(0.35f, VoiceState.level(speakers.get(index)));
	}

	private void drawLevelBar(GuiGraphicsExtractor context, int x, int y, float level) {
		int top = y + 1;
		context.fill(x, top, x + BAR_W, top + BAR_H, 0x40FFFFFF);
		int filled = Math.max(1, Math.round(BAR_H * Math.max(0f, Math.min(1f, level))));
		context.fill(x, top + BAR_H - filled, x + BAR_W, top + BAR_H, LunaTheme.ACCENT);
	}
}
