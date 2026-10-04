package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.FontSplitHook;
import net.minecraft.resource.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * 49-157차: [내 리소스팩 우선]의 [기호는 서버 것] - 글꼴 JSON을 읽는 한 자리에서 내 팩 글꼴의 기호를 뺀다
 * ({@link FontSplitHook}). FontManager#loadFontProviders(List&lt;Resource&gt;, Identifier)는 1.20부터 있다
 * (글꼴 reference 개편). 그 전 버전은 대상이 없어 require = 0으로 조용히 빠진다.
 * 핸들러는 대상 메서드의 인자를 안 받는다(버전마다 인자가 조금이라도 다르면 "Invalid descriptor"로 게임이 깨진다 - 49-153차 교훈).
 * Resource#getReader()를 직접 부르면 1.19.2 이하에서 컴파일이 안 되므로(그땐 없는 메서드) 훅이 리플렉션으로 부른다.
 */
@Mixin(targets = "net.minecraft.client.font.FontManager")
public abstract class FontSplitMixin {

	@Redirect(method = "loadFontProviders", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resource/Resource;getReader()Ljava/io/BufferedReader;"), require = 0)
	private static BufferedReader lunaslight$fontReader(Resource resource)
			throws IOException {
		return FontSplitHook.reader(resource, null);
	}
}
