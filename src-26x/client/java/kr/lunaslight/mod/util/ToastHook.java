package kr.lunaslight.mod.util;

/**
 * 49-54차(1-3): 토스트(화면 오른쪽 위에 올라오는 알림 상자) 끄기.
 *
 * <p>믹스인은 {@code ToastManager.add(Toast)} 입구에서 이걸 물어보고, 참이면 아예 안 넣는다
 * (그리는 걸 막는 게 아니라 목록에 안 넣으므로 자리도 안 잡고 소리도 안 난다).
 *
 * <p>종류 판별은 <b>클래스</b>로 한다. 토스트 클래스 이름은 프로덕션에서 난독화돼 있으므로
 * {@link LunaCompat#classOrNull}(Yarn 이름 → 실제 이름 표를 들고 있다)로 한 번만 찾아 캐시해 둔다.
 * 네 종류(발전 과제·제작법·튜토리얼·시스템)는 1.15.2~1.21.11·26.x에 전부 있는 것을 확인했다.
 *
 * <p>못 찾은 종류는 <b>막지 않는다</b> - 모르는 건 통과시킨다(잘못 막아 중요한 경고를 숨기는 쪽이
 * 훨씬 나쁘다).
 */
public final class ToastHook {
	private ToastHook() {
	}

	public static volatile boolean active;
	public static volatile boolean hideAdvancement = true;
	public static volatile boolean hideRecipe = true;
	public static volatile boolean hideTutorial = true;
	public static volatile boolean hideSystem;

	private static boolean resolved;
	private static Class<?> advancement;
	private static Class<?> recipe;
	private static Class<?> tutorial;
	private static Class<?> system;

	private static void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;
		advancement = LunaCompat.classOrNull("net.minecraft.client.gui.components.toasts.AdvancementToast");
		recipe = LunaCompat.classOrNull("net.minecraft.client.gui.components.toasts.RecipeToast");
		tutorial = LunaCompat.classOrNull("net.minecraft.client.gui.components.toasts.TutorialToast");
		system = LunaCompat.classOrNull("net.minecraft.client.gui.components.toasts.SystemToast");
	}

	/** 이 토스트를 아예 안 띄울지. */
	public static boolean shouldHide(Object toast) {
		if (!active || toast == null) {
			return false;
		}
		try {
			resolve();
			if (hideAdvancement && advancement != null && advancement.isInstance(toast)) {
				return true;
			}
			if (hideRecipe && recipe != null && recipe.isInstance(toast)) {
				return true;
			}
			if (hideTutorial && tutorial != null && tutorial.isInstance(toast)) {
				return true;
			}
			if (hideSystem && system != null && system.isInstance(toast)) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}
}
