package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindListSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.AltKeys;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-89차(8-6·8-8·8-17): <b>단축키</b> 설정 페이지(그래픽 옆 [키] 탭). 기능 카드가 아니라 클라이언트 설정이다.
 *
 * <h3>① 내 단축키 - 키 하나에 글·명령어 하나 (8-6·8-8)</h3>
 * 예전엔 "명령어 키"(슬롯 10개)와 "내 키"(칸 8개)가 <b>같은 일을 하는 기능 둘</b>로 있었고(사용자: "겹침, 같은 기능임"),
 * 둘 다 번호 칸을 미리 늘어놓는 방식이었다("1~10 등록 방식이 아닌"). 이제 하나로 합치고 <b>[+ 추가]로 하나씩</b> 늘린다.
 * 저장 구조(설정 id로 저장)를 그대로 쓰기 위해 칸은 {@value #POOL}개를 미리 두되 <b>쓰는 만큼만 보인다</b>(나머지 숨김).
 * 키를 풀고 글을 비우면 그 칸은 저장할 때 접힌다. 화면(채팅·인벤토리)이 떠 있으면 키를 안 가로챈다.
 *
 * <h3>② 마인크래프트 키마다 보조 키 (8-17)</h3>
 * 바닐라 키바인딩 하나하나에 두 번째 키(키보드·마우스)를 둔다. 실제 동작은 {@link AltKeys} + KeyBindingAltMixin.
 * 1.15.2·1.16·1.16.1은 KeyBinding 클래스 위치가 달라 믹스인이 안 붙으므로 이 묶음을 만들지 않는다(없는 기능은 안 보인다).
 */
public class ShortcutsModule extends Module {

	private static final int POOL = 24;
	private static final boolean ALT_SUPPORTED = LunaVersion.isWithin("1.16", null);

	private final IntSetting count;
	private final KeybindSetting[] keys = new KeybindSetting[POOL];
	private final StringSetting[] texts = new StringSetting[POOL];
	private final boolean[] wasDown = new boolean[POOL];
	private final List<KeybindSetting> alts = new ArrayList<>();
	private final List<String> altIds = new ArrayList<>();
	private boolean altDirty = true;
	private int serverCheckTicks;

	public ShortcutsModule() {
		super("shortcuts", "키바인드", ModuleCategory.FEATURE, "키 하나로 글/명령어 보내기 | 마인크래프트 보조 키");
		// 49-179차(사용자: "키바인드는 기능이 아니라 전에처럼 설정 밑에 있어야지"): 다시 [키바인드] 설정 페이지로.
		settingsPage(kr.lunaslight.mod.module.SettingsPage.KEYS);
		alwaysOn();
		count = register(new IntSetting("count", "칸 수", "", 0, 0, POOL, 1));
		count.hidden();
		// 49-103차: 항목마다 [명령어/글 + 키 + ×] 한 줄로 보이는 목록. 화면이 직접 그린다(KeybindListSetting).
		// pooled key/text는 저장용으로만 남기고 화면엔 안 보인다(항상 숨김).
		register(new KeybindListSetting("keybinds", "내 키바인드",
				"키 하나에 글이나 명령어를 걸어 둡니다. /로 시작하면 명령어로 나갑니다.", "추가하기", new KeybindListSetting.Backing() {
					@Override public int size() { return Math.max(0, Math.min(POOL, count.get())); }
					@Override public int capacity() { return POOL; }
					@Override public KeybindSetting keyAt(int i) { return keys[i]; }
					@Override public StringSetting textAt(int i) { return texts[i]; }
					@Override public void add() { addSlot(); }
					@Override public void delete(int i) { deleteEntry(i); }
				}));
		for (int i = 0; i < POOL; i++) {
			int n = i + 1;
			keys[i] = register(new KeybindSetting("key" + n, n + "번 키", "누르면 옆 글을 보냅니다.", GLFW.GLFW_KEY_UNKNOWN));
			texts[i] = register(new StringSetting("text" + n, n + "번 글", "보낼 말이나 명령어입니다.", ""));
			keys[i].hidden();
			texts[i].hidden();
		}
		if (ALT_SUPPORTED) {
			try {
				MinecraftClient client = MinecraftClient.getInstance();
				for (LunaCompat.VanillaBinding vb : LunaCompat.vanillaKeyBindings(client)) {
					KeybindSetting k = register(new KeybindSetting("alt:" + vb.id(), vb.label(), "이 동작의 두 번째 키입니다.", GLFW.GLFW_KEY_UNKNOWN));
					k.onChange(() -> altDirty = true);
					alts.add(k);
					altIds.add(vb.id());
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("shortcuts:alts", t);
			}
		}
	}

	private void addSlot() {
		trim();
		if (count.get() < POOL) {
			count.setValue(count.get() + 1);
		}
	}

	/** 끝에서부터 키도 글도 없는 칸을 접는다. */
	private void trim() {
		int n = count.get();
		while (n > 0 && !keys[n - 1].isBound() && (texts[n - 1].get() == null || texts[n - 1].get().isBlank())) {
			n--;
		}
		if (n != count.get()) {
			count.setValue(n);
		}
	}

	/** 49-103차: pooled key/text는 이제 목록(KeybindListSetting)이 대신 그리므로 화면엔 항상 숨김(저장 전용). */
	private void applyVisibility() {
		for (int i = 0; i < POOL; i++) {
			keys[i].setHidden(true);
			texts[i].setHidden(true);
		}
	}

	/** 항목 i 삭제 - 뒤 항목을 한 칸씩 끌어올리고 칸 수를 줄인다. */
	private void deleteEntry(int i) {
		int n = Math.max(0, Math.min(POOL, count.get()));
		if (i < 0 || i >= n) {
			return;
		}
		for (int j = i; j < n - 1; j++) {
			keys[j].setValue(keys[j + 1].getKeyCode());
			texts[j].setValue(texts[j + 1].get());
		}
		keys[n - 1].setValue(GLFW.GLFW_KEY_UNKNOWN);
		texts[n - 1].setValue("");
		count.setValue(n - 1);
	}

	/** 설정을 다 읽은 뒤(LunaClientConfig.load) 한 번 - 저장된 칸을 펴고 보조 키 표를 만든다. */
	public void afterLoad() {
		// 저장된 count가 없던 옛 저장이거나 값보다 채워진 칸이 더 많으면 채워진 만큼 편다
		int filled = 0;
		for (int i = 0; i < POOL; i++) {
			if (keys[i].isBound() || (texts[i].get() != null && !texts[i].get().isBlank())) {
				filled = i + 1;
			}
		}
		if (filled > count.get()) {
			count.setValue(filled);
		}
		applyVisibility();
		altDirty = true;
	}

	/** 옛 "내 키"(keyN/textN)·"명령어 키"(slotN_key/slotN_command) 값을 가져온다 - LunaClientConfig가 부른다. */
	public void importLegacy(int key, String text) {
		if (key < 0 && (text == null || text.isBlank())) {
			return;
		}
		int n = count.get();
		if (n >= POOL) {
			return;
		}
		keys[n].setValue(key);
		texts[n].setValue(text == null ? "" : text);
		count.setValue(n + 1);
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || WindowAccess.of(client) == null) {
			return;
		}
		// 49-125차: 서버에 있는지 20틱마다 갱신 - 공격/사용의 키보드 보조 키는 서버에서 안 먹게(AltKeys.realFor).
		if (++serverCheckTicks % 20 == 0) {
			AltKeys.setServerMode(client.world != null && !LunaCompat.isSinglePlayer(client));
		}
		if (altDirty && ALT_SUPPORTED) {
			altDirty = false;
			Map<String, Integer> map = new HashMap<>();
			for (int i = 0; i < alts.size(); i++) {
				if (alts.get(i).isBound()) {
					map.put(altIds.get(i), alts.get(i).getKeyCode());
				}
			}
			AltKeys.rebuild(map, client);
		}
		boolean blocked = client.currentScreen != null;
		int n = Math.min(POOL, count.get());
		for (int i = 0; i < n; i++) {
			KeybindSetting key = keys[i];
			if (!key.isBound()) {
				wasDown[i] = false;
				continue;
			}
			boolean down = !blocked && key.isDown(client);
			if (down && !wasDown[i]) {
				String text = texts[i].get();
				if (text != null && !text.isBlank()) {
					LunaCompat.sendChatOrCommand(client, text.trim());
				}
			}
			wasDown[i] = down;
		}
	}
}
