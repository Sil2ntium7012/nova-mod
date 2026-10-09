package kr.lunaslight.mod.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 49-313차: 내장 한글 입력(HangulInput)이 조합한 글자와 백스페이스를 게임의 키보드 처리로 다시 보내는 길.
 * 26.1~26.2는 private, 26.3은 public이지만 이름과 인자가 같다. 26.x 믹스인 목록은 파일 이름이 Mixin으로 끝나는 것만 모으므로 이 이름.
 */
@Mixin(KeyboardHandler.class)
public interface KeyboardInvokerMixin {

	@Invoker("keyPress")
	void lunaslight$keyPress(long window, int action, KeyEvent event);

	@Invoker("charTyped")
	void lunaslight$charTyped(long window, CharacterEvent event);
}
