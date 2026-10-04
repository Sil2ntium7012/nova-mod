package kr.lunaslight.mod.util;

import net.minecraft.util.Identifier;

/**
 * 49-68차(4-9): <b>거슬리는 소리 끄기</b>의 판정부. 믹스인이 소리 하나마다 부르는 자리라
 * <b>꺼져 있을 때의 비용이 0에 가까워야 한다</b>({@link #off}가 첫 줄에서 막는다).
 *
 * <h3>왜 "재생 취소"가 아니라 "소리 크기 0"인가</h3>
 * 처음엔 {@code SoundSystem#play}를 취소할 생각이었는데, 실측해 보니 <b>1.21.8에서 그 메서드의
 * 반환형이 void → {@code PlayResult} 열거형으로 바뀌었다</b>. 취소하려면 그 시대에는 "무엇을
 * 돌려줄지"를 정해야 하는데, 그 열거형은 옛 버전에 아예 없어서 공유 소스에서 값을 적을 수가 없다
 * (실행 중에는 상수 이름도 난독화되어 이름으로 찾지도 못한다).
 *
 * <p>대신 {@code AbstractSoundInstance#getVolume()}을 잡는다. 이 메서드는 <b>1.15.2부터
 * 1.21.11까지, 26.2까지 {@code public float getVolume()} 그대로다</b>(javap 실측). 그리고
 * 마인크래프트 자신이 <b>소리 크기가 0이면 재생을 건너뛴다</b>("Skipped playing sound {}, volume was
 * zero." — 이 문구가 1.15.2·1.20.4·1.21.11 상수 풀에 전부 있다). 즉 0을 돌려주면 바닐라가 스스로
 * 안 튼다 - 우리가 흐름을 끊지 않아도 된다.
 *
 * <h3>무엇을 끌지 고르는 법</h3>
 * 소리 id({@code minecraft:ambient.cave} 같은 것)의 <b>앞부분이 맞으면</b> 끈다. 앞부분으로 맞추면
 * {@code entity.villager}만 적어도 주민이 내는 소리가 전부 들어간다 - 하나하나 세어 적을 수 없다.
 */
public final class SoundFilterHook {
	private SoundFilterHook() {
	}

	/** 켜져 있는 동안에만 false. 모듈이 꺼지면 믹스인은 이 한 줄만 읽고 빠져나간다. */
	public static volatile boolean off = true;

	/** 끌 소리 id의 앞부분들(소문자, 네임스페이스 없이도 맞게 처리). 없으면 null. */
	public static volatile String[] prefixes;

	/**
	 * 이 소리를 끌지. id를 못 읽으면 <b>끄지 않는다</b> - 모르는 것을 지우는 쪽보다 놔두는 쪽이 낫다.
	 */
	public static boolean muted(net.minecraft.client.sound.SoundInstance sound) {
		String[] list = prefixes;
		if (off || list == null || sound == null) {
			return false;
		}
		try {
			// ⚠️ 49-68차: 이 한 줄이 26.2 빌드를 깼었다(`getId`가 `getIdentifier`로 안 바뀜).
			// 원인은 이름 규칙이 아니라 **번역기가 이 파일을 아예 못 읽은 것**이었다 - Fabric API가
			// SoundInstance에 FabricSoundInstance를 주입해서, 그 클래스가 없으면 javac가 상위 타입을
			// 못 읽고 파일 전체의 타입 분석이 실패한다(그러면 번역기는 그 파일을 손대지 못한다).
			// 49-62차의 RenderDataBlockEntity와 **똑같은 구멍**이라, 그때처럼 스텁을 넣어 막았다.
			Identifier id = sound.getId();
			if (id == null) {
				return false;
			}
			String path = id.getPath();                 // "ambient.cave"
			String full = id.toString();                // "minecraft:ambient.cave"
			for (String p : list) {
				if (path.startsWith(p) || full.startsWith(p)) {
					return true;
				}
			}
		} catch (Throwable ignored) {
			// 이 버전에 없는 모양의 소리 - 건드리지 않는다
		}
		return false;
	}

	/** 쉼표로 적은 것을 잘라 소문자 배열로. 빈 것은 null(믹스인이 첫 줄에서 빠져나가게). */
	public static String[] parse(String raw, String... presets) {
		java.util.List<String> out = new java.util.ArrayList<>(8);
		for (String p : presets) {
			if (p != null && !p.isEmpty() && !out.contains(p)) {
				out.add(p);
			}
		}
		if (raw != null && !raw.isBlank()) {
			for (String w : raw.split(",")) {
				String t = w.trim().toLowerCase(java.util.Locale.ROOT);
				if (!t.isEmpty() && !out.contains(t)) {
					out.add(t);
				}
			}
		}
		return out.isEmpty() ? null : out.toArray(new String[0]);
	}
}
