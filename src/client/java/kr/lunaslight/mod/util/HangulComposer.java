package kr.lunaslight.mod.util;

/**
 * 49-313차: 두벌식 한글 조합기(모드 내장 한글 입력). 영문 자판 글자(QWERTY)를 받아 한 글자씩 조합한다.
 *
 * <p>{@link #input} 하나마다 "확정된 글자"(앞 글자가 끝났으면 그 글자)와 "조합 중인 글자"(지금 만들고 있는 한 글자)를 돌려준다.
 * 입력칸에는 조합 중인 글자를 진짜 글자로 넣어 두고, 다음 자모가 오면 그 한 글자를 지우고 새로 넣는다(HangulInput).
 * 그래서 조합 중인 글자가 늘 보인다(윈도우 입력기 + 옛 GLFW에서는 끝날 때까지 안 보였다).
 *
 * <p>규칙(표준 두벌식): 초성 → 중성 → 종성, 겹모음(ㅘ ㅙ ㅚ ㅝ ㅞ ㅟ ㅢ), 겹받침(ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ), 받침 뒤에
 * 모음이 오면 받침(겹받침이면 뒤쪽)을 다음 글자 초성으로 넘긴다(각+ㅏ → 가가, 닭+ㅏ → 달가). 백스페이스는 자모 하나씩 되돌린다.
 */
public final class HangulComposer {

	private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
	private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
	/** 0번은 받침 없음. */
	private static final String JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

	/** a~z(소문자) → 자모. */
	private static final String LOWER = "ㅁㅠㅊㅇㄷㄹㅎㅗㅑㅓㅏㅣㅡㅜㅐㅔㅂㄱㄴㅅㅕㅍㅈㅌㅛㅋ";
	/** A~Z(쉬프트) → 자모. 쌍자음 다섯과 ㅒ ㅖ 말고는 소문자와 같다. */
	private static final String UPPER = "ㅁㅠㅊㅇㄸㄹㅎㅗㅑㅓㅏㅣㅡㅜㅒㅖㅃㄲㄴㅆㅕㅍㅉㅌㅛㅋ";

	/** 겹모음: {앞, 뒤, 결과}(JUNG 번호). */
	private static final int[][] JUNG_PAIRS = {
		{8, 0, 9}, {8, 1, 10}, {8, 20, 11},      // ㅗ+ㅏ ㅘ, ㅗ+ㅐ ㅙ, ㅗ+ㅣ ㅚ
		{13, 4, 14}, {13, 5, 15}, {13, 20, 16},  // ㅜ+ㅓ ㅝ, ㅜ+ㅔ ㅞ, ㅜ+ㅣ ㅟ
		{18, 20, 19}};                           // ㅡ+ㅣ ㅢ
	/** 겹받침: {앞, 뒤 자음(CHO 번호), 결과}(앞/결과는 JONG 번호). */
	private static final int[][] JONG_PAIRS = {
		{1, 9, 3},                                                  // ㄱ+ㅅ ㄳ
		{4, 12, 5}, {4, 18, 6},                                     // ㄴ+ㅈ ㄵ, ㄴ+ㅎ ㄶ
		{8, 0, 9}, {8, 6, 10}, {8, 7, 11}, {8, 9, 12}, {8, 16, 13}, {8, 17, 14}, {8, 18, 15},   // ㄹ+ㄱ ㅁ ㅂ ㅅ ㅌ ㅍ ㅎ
		{17, 9, 18}};                                               // ㅂ+ㅅ ㅄ

	// ---- 조합 중인 한 글자 ----
	private int cho = -1;
	private int jung = -1;
	private int jong = 0;
	/** 백스페이스로 되돌릴 이전 상태들(이번 글자 안에서만). [cho, jung, jong] */
	private final int[][] history = new int[8][];
	private int depth;

	/** 결과: 확정된 글자(없으면 "")와 조합 중인 글자(없으면 ""). */
	public static final class Result {
		public final String commit;
		public final String preedit;

		Result(String commit, String preedit) {
			this.commit = commit;
			this.preedit = preedit;
		}
	}

	/** 영문 글자 하나 → 자모(한글이 아니면 0). shift = 쉬프트를 누른 채인지. */
	public static char jamoOf(int codePoint, boolean shift) {
		if (codePoint >= 'a' && codePoint <= 'z') {
			return (shift ? UPPER : LOWER).charAt(codePoint - 'a');
		}
		if (codePoint >= 'A' && codePoint <= 'Z') {
			return (shift ? UPPER : LOWER).charAt(codePoint - 'A');
		}
		return 0;
	}

	public boolean composing() {
		return cho >= 0 || jung >= 0;
	}

	/** 지금 조합 중인 글자(없으면 ""). */
	public String preedit() {
		if (cho >= 0 && jung >= 0) {
			return String.valueOf((char) (0xAC00 + (cho * 21 + jung) * 28 + jong));
		}
		if (cho >= 0) {
			return String.valueOf(CHO.charAt(cho));
		}
		if (jung >= 0) {
			return String.valueOf(JUNG.charAt(jung));
		}
		return "";
	}

	/** 조합을 끝낸다(지금 글자는 그대로 확정 - 입력칸엔 이미 들어가 있다). */
	public void reset() {
		cho = -1;
		jung = -1;
		jong = 0;
		depth = 0;
	}

	private void push() {
		if (depth == history.length) {
			System.arraycopy(history, 1, history, 0, history.length - 1);
			depth--;
		}
		history[depth++] = new int[]{cho, jung, jong};
	}

	/** 자모 하나 넣기. */
	public Result input(char jamo) {
		int c = CHO.indexOf(jamo);
		int v = JUNG.indexOf(jamo);
		if (c < 0 && v < 0) {
			String done = preedit();
			reset();
			return new Result(done + jamo, "");
		}
		return c >= 0 ? consonant(c, jamo) : vowel(v);
	}

	private Result consonant(int c, char jamo) {
		if (cho < 0 && jung < 0) {
			push();
			cho = c;
			return new Result("", preedit());
		}
		if (jung < 0) {
			// 자음만 있는데 또 자음 → 앞 자음 확정, 새 글자
			return startNew("", c, -1);
		}
		if (cho < 0) {
			// 모음만 있는 글자 뒤 자음 → 모음 확정
			return startNew("", c, -1);
		}
		if (jong == 0) {
			int j = JONG.indexOf(jamo);
			if (j > 0) {
				push();
				jong = j;
				return new Result("", preedit());
			}
			return startNew("", c, -1);   // ㄸ ㅃ ㅉ은 받침이 안 된다
		}
		for (int[] p : JONG_PAIRS) {
			if (p[0] == jong && p[1] == c) {
				push();
				jong = p[2];
				return new Result("", preedit());
			}
		}
		return startNew("", c, -1);
	}

	private Result vowel(int v) {
		if (cho < 0 && jung < 0) {
			push();
			jung = v;
			return new Result("", preedit());
		}
		if (jung < 0) {
			push();
			jung = v;
			return new Result("", preedit());
		}
		if (jong == 0) {
			for (int[] p : JUNG_PAIRS) {
				if (p[0] == jung && p[1] == v) {
					push();
					jung = p[2];
					return new Result("", preedit());
				}
			}
			return startNew("", -1, v);
		}
		// 받침 + 모음 → 받침(겹받침이면 뒤쪽)이 다음 글자 첫소리로
		int keep = 0;
		int move = -1;
		for (int[] p : JONG_PAIRS) {
			if (p[2] == jong) {
				keep = p[0];
				move = p[1];
				break;
			}
		}
		if (move < 0) {
			move = CHO.indexOf(JONG.charAt(jong));
			keep = 0;
		}
		jong = keep;
		String done = preedit();
		reset();
		push();
		cho = move;
		push();
		jung = v;
		return new Result(done, preedit());
	}

	/** 지금 글자를 확정하고 새 글자(초성 c 또는 모음 v)를 시작. */
	private Result startNew(String extra, int c, int v) {
		String done = preedit() + extra;
		reset();
		push();
		if (c >= 0) {
			cho = c;
		} else {
			jung = v;
		}
		return new Result(done, preedit());
	}

	/**
	 * 백스페이스: 자모 하나를 되돌린다. 돌려준 글자가 새 조합 글자("")면 이 글자는 다 지워진 것.
	 * 조합 중이 아니었으면 null(그냥 바닐라 백스페이스).
	 */
	public String backspace() {
		if (!composing()) {
			return null;
		}
		if (depth <= 1) {
			reset();
			return "";
		}
		int[] prev = history[--depth];
		cho = prev[0];
		jung = prev[1];
		jong = prev[2];
		return preedit();
	}
}
