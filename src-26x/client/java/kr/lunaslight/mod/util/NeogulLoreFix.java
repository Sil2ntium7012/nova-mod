package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 49-533차(사용자: 요리용 석탄 설명 상자 - "위키 보고 아이템 사용처 제대로 다시 써 놔"): 너굴마을 아이템 설명의 "사용처" 아래 줄("- …")을
 * 노바 자료(items.json의 "uses", 위키 기준)로 바꿔 쓴다. 그 아이템에 "uses"가 없으면 서버 설명 그대로. 글자 모양(색)은 서버 첫 줄 것을 따른다.
 */
public final class NeogulLoreFix {
	private NeogulLoreFix() {
	}

	private static boolean registered;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		LunaCompat.registerTooltipCallback(() -> {
			Minecraft mc = Minecraft.getInstance();
			return mc != null && mc.player != null && NeogulData.onNeogul(mc);
		}, NeogulLoreFix::fix);
	}

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)).forEach(sb::appendCodePoint);
		return sb.toString().trim();
	}

	private static void fix(ItemStack stack, List<Component> lines) {
		try {
			List<String> uses = NeogulData.uses(NeogulData.keyOf(stack));
			if (uses == null || uses.isEmpty()) {
				return;
			}
			int head = -1;
			for (int i = 0; i < lines.size(); i++) {
				if (plain(lines.get(i).getString()).equals("사용처")) {
					head = i;
					break;
				}
			}
			if (head < 0) {
				return;
			}
			int end = head + 1;
			Style style = Style.EMPTY;
			boolean first = true;
			while (end < lines.size() && plain(lines.get(end).getString()).startsWith("-")) {
				if (first) {
					style = styleOf(lines.get(end));
					first = false;
				}
				end++;
			}
			List<Component> repl = new java.util.ArrayList<>();
			for (String u : uses) {
				repl.add(Component.literal("- " + u).withStyle(style));
			}
			lines.subList(head + 1, end).clear();
			lines.addAll(head + 1, repl);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulLoreFix", t);
		}
	}

	/** 줄에서 글자가 처음 나오는 조각의 모양(색). */
	private static Style styleOf(Component c) {
		Style[] out = {c.getStyle()};
		c.visit((st, s) -> {
			if (!plain(s).isEmpty()) {
				out[0] = st;
				return java.util.Optional.of(Boolean.TRUE);
			}
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return out[0];
	}
}
