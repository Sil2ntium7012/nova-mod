package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FontSplitHook;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
}
