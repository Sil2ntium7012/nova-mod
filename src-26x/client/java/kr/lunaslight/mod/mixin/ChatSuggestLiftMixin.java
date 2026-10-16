package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaChatTabs;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-379차: 채팅 탭 상자가 보일 땐 입력칸이 INPUT_LIFT만큼 위에 있다. 명령어 추천 목록(showSuggestions)과
 * 사용법 줄(extractUsage)은 화면 높이 - 12를 기준으로 자리를 잡아서, 그 높이를 같이 줄여 입력칸 위에 붙게 한다.
 * 클릭 판정도 같은 목록 자리(Rect2i)를 써서 같이 옮겨진다.
 */
@Mixin(CommandSuggestions.class)
public abstract class ChatSuggestLiftMixin {
	@Redirect(method = {"showSuggestions", "extractUsage"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = 180 /* GETFIELD */), require = 0)
	private int lunaslight$liftHeight(Screen s) {
		if (s instanceof ChatScreen && LunaChatTabs.isActive()) {
			return s.height - LunaChatTabs.INPUT_LIFT;
		}
		return s.height;
	}
}
