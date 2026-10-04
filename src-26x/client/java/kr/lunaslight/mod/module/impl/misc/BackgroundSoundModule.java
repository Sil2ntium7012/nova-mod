package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.sounds.SoundSource;

/**
 * 49-55차(1-2): 백그라운드 음소거 - 마인크래프트 창이 뒤로 가 있으면 소리를 줄인다.
 *
 * <p>다른 창을 보는 동안 게임 소리가 계속 나는 게 이 기능의 대상이다. 완전히 끄지 않고 [배경 볼륨]으로
 * 정하게 했다 - 서버에서 누가 부르는 소리는 작게라도 듣고 싶은 경우가 있어서(기본값은 0 = 완전 무음).
 *
 * <p>마인크래프트 설정 파일(options.txt)은 <b>건드리지 않는다</b> - 지금 재생 중인 소리에 적용되는
 * 카테고리 볼륨만 잠깐 내렸다가, 창으로 돌아오면 설정에 저장된 [마스터] 값을 다시 넣는다. 그래서
 * 게임이 중간에 꺼져도 다음에 켜면 원래 볼륨 그대로다.
 *
 * <p>볼륨을 바꾸는 메서드 이름이 버전마다 다르다(javap 실측) - 1.15.2~1.21.8 {@code updateSoundVolume},
 * 1.21.11 {@code setVolume}, 26.x {@code updateCategoryVolume}. 그래서 리플렉션으로 차례로 찾는다.
 * 반면 {@code GameOptions.getSoundVolume(SoundCategory)}는 1.15.2부터 이름이 그대로라 직접 부른다.
 *
 * <p><b>1.21.9 · 1.21.10에는 그런 메서드가 아예 없다</b>(그 두 버전은 소리마다 볼륨을 다시 계산하는
 * 구조로 바뀌었다가 1.21.11에서 되돌아왔다 - 1.21.10 javap 확인). 버전 번호로 막으면 확인 안 한
 * 버전까지 같이 막히므로, <b>실제로 그 메서드가 있는지를 보고</b> 없으면 기능 카드가 잠긴다.
 * 켜지는데 아무 일도 안 일어나는 것보다는 "이 버전에선 안 됨"이라고 보이는 쪽이 낫다.
 */
public class BackgroundSoundModule extends Module {

	/** 버전마다 다른 "카테고리 볼륨을 지금 바꿔라" 메서드 이름(위 주석 참고). */
	private static final String[] VOLUME_METHODS = {"updateSoundVolume", "setVolume", "updateCategoryVolume"};


	/** 지금 우리가 볼륨을 내려 둔 상태인지 - 포커스가 바뀔 때만 손대려고 들고 있는다. */
	private boolean lowered;

	public BackgroundSoundModule() {
		super("background_sound", "백그라운드 음소거", ModuleCategory.FEATURE, "창이 뒤로 가면 소리 줄임");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 49-56차: 기능이 아니라 [일반] 설정
	}

	// ==================== 이 버전에서 쓸 수 있는지 ====================

	@Override
	public boolean isVersionSupported() {
		return super.isVersionSupported() && volumeMethod() != null;
	}

	@Override
	public String getVersionRequirementLabel() {
		String base = super.getVersionRequirementLabel();
		if (base != null) {
			return base;
		}
		return volumeMethod() == null ? "다른 마인크래프트 버전" : null;
	}

	/** 이 버전의 SoundManager에서 "카테고리 볼륨 지금 바꾸기"에 해당하는 메서드(없으면 null). */
	private java.lang.reflect.Method volumeMethod() {
		try {
			if (client == null) {
				return null;
			}
			Object manager = client.getSoundManager();
			if (manager == null) {
				return null;
			}
			for (String name : VOLUME_METHODS) {
				// LunaCompat이 (클래스, 이름)별로 캐시하므로 - 못 찾은 것도 - 매 프레임 불러도 싸다.
				java.lang.reflect.Method m = LunaCompat.findMethod(manager.getClass(), name,
					SoundSource.class, float.class);
				if (m != null) {
					return m;
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	// ==================== 동작 ====================

	@Override
	public void onTick() {
		if (client == null) {
			return;
		}
		boolean focused = client.isWindowActive();
		if (!focused && !lowered) {
			applyVolume(0f);   // 49-143차(사용자: "음소거인데 음량이 왜 있어"): 배경 볼륨 설정 삭제, 늘 완전 무음
			lowered = true;
		} else if (focused && lowered) {
			restore();
		}
	}

	@Override
	protected void onDisable() {
		if (lowered) {
			restore();
		}
	}

	private void restore() {
		lowered = false;
		try {
			applyVolume(client.options.getFinalSoundSourceVolume(SoundSource.MASTER));
		} catch (Throwable ignored) {
			applyVolume(1f);   // 설정을 못 읽었으면 적어도 소리가 죽은 채로 두지는 않는다
		}
	}

	private void applyVolume(float value) {
		try {
			java.lang.reflect.Method m = volumeMethod();
			if (m != null) {
				m.invoke(client.getSoundManager(), SoundSource.MASTER, value);
			}
		} catch (Throwable ignored) {
		}
	}
}
