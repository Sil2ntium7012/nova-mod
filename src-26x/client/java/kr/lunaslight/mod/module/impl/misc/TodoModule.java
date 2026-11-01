package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.TodoList;

/**
 * 49-473차: <b>할 일 목록</b>({@link TodoList}). 켜고 끄는 기능이 아니다(늘 켜짐) - 상자를 누르면 {@code TodoScreen}이 열린다.
 * 행동 세기는 여기서 매 틱. 화면 표시는 {@link TodoHudModule}.
 */
public class TodoModule extends Module {

	public TodoModule() {
		super("todo_list", "할 일 목록", ModuleCategory.TODO, "할 일 추가 | 행동하면 저절로 체크");
		alwaysOn();
		ChatState.addPlainListener(TodoList::onChat);
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.TodoScreen((net.minecraft.client.gui.screens.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("todo:open", t);
			return false;
		}
	}

	@Override
	public void onTick() {
		try {
			TodoList.tick(client);
		} catch (Throwable t) {
			LunaCompat.warnOnce("todo:tick", t);
		}
	}
}
