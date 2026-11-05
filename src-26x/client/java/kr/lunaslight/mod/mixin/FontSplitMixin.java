package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FontSplitHook;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * 49-157차: [내 리소스팩 우선]의 [기호는 서버 것] - 글꼴 JSON을 읽는 한 자리에서 내 팩 글꼴의 기호를 뺀다
 * ({@link FontSplitHook}). 26.x는 FontManager#loadResourceStack(List&lt;Resource&gt;, Identifier).
 */
@Mixin(net.minecraft.client.gui.font.FontManager.class)
public abstract class FontSplitMixin {

	@Redirect(method = "loadResourceStack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/Resource;openAsReader()Ljava/io/BufferedReader;"), require = 0)
	private static BufferedReader lunaslight$fontReader(Resource resource)
			throws IOException {
		return FontSplitHook.filter(resource.openAsReader(), resource, null);
	}

	/**
	 * 49-566차: [글꼴만]일 때 팩 순서는 바닐라(서버 팩 맨 위)로 두므로, 글꼴 하나(예: minecraft:default)의 JSON 목록에서만 내 팩(file/…) 것을
	 * 맨 뒤로 옮긴다. 이 목록은 아래 팩 → 위 팩 순이고 바닐라가 나중에 뒤집어 위 팩부터 찾으므로, 맨 뒤 = 가장 먼저 쓰인다.
	 */
	@ModifyVariable(method = "loadResourceStack", at = @At("HEAD"), argsOnly = true, require = 0)
	private static java.util.List<Resource> lunaslight$fontFirst(java.util.List<Resource> stack) {
		try {
			if (stack == null || stack.size() < 2 || !kr.lunaslight.mod.util.PackUiGuard.fontFirst()) {
				return stack;
			}
			java.util.List<Resource> mine = new java.util.ArrayList<>();
			java.util.List<Resource> out = new java.util.ArrayList<>(stack.size());
			for (Resource r : stack) {
				String id = r.sourcePackId();
				if (id != null && id.startsWith("file/")) {
					mine.add(r);
				} else {
					out.add(r);
				}
			}
			if (mine.isEmpty()) {
				return stack;
			}
			out.addAll(mine);
			return out;
		} catch (Throwable t) {
			return stack;
		}
	}
}
