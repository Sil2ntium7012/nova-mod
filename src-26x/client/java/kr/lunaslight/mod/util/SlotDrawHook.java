package kr.lunaslight.mod.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/**
 * 49-42차: HandledScreen이 칸 하나를 그리기 **직전**(배경은 이미 있고 아이템은 아직 안 그린 순간)에 부르는 훅.
 * 좌표는 화면 원점 기준(슬롯 x/y 그대로 쓰면 됨 - 바닐라가 행렬을 (x,y)로 옮겨 놓은 상태).
 * 희귀도 테두리처럼 "아이템 아래·툴팁 아래"에 깔려야 하는 것들에 쓴다. SlotDrawMixin이 부른다.
 */
public final class SlotDrawHook {
	private SlotDrawHook() {
	}

	public interface Handler {
		void beforeSlot(GuiGraphicsExtractor ctx, Slot slot);

		/** 칸 그리기 직후(아이템까지 그려진 뒤). 기본은 아무것도 안 함. */
		default void afterSlot(GuiGraphicsExtractor ctx, Slot slot) {
		}
	}

	private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

	public static void register(Handler h) {
		if (h != null && !HANDLERS.contains(h)) {
			HANDLERS.add(h);
		}
	}

	public static void beforeSlot(GuiGraphicsExtractor ctx, Slot slot) {
		if (HANDLERS.isEmpty() || ctx == null || slot == null) {
			return;
		}
		for (Handler h : HANDLERS) {
			try {
				h.beforeSlot(ctx, slot);
			} catch (Throwable t) {
				LunaCompat.warnOnce("slotDraw", t);
			}
		}
	}

	public static void afterSlot(GuiGraphicsExtractor ctx, Slot slot) {
		if (HANDLERS.isEmpty() || ctx == null || slot == null) {
			return;
		}
		for (Handler h : HANDLERS) {
			try {
				h.afterSlot(ctx, slot);
			} catch (Throwable t) {
				LunaCompat.warnOnce("slotDrawAfter", t);
			}
		}
	}
}
