package kr.lunaslight.mod.mixin;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import kr.lunaslight.mod.util.FontGlyphScan;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-159차: 서버 리소스팩 적용 끝의 85~96초 멈춤(playfarm.kr). 자세한 까닭은 {@link FontGlyphScan}.
 * FontSet#selectProviders 안에서 글자마다 글꼴 목록 전체를 훑는 IntSet.forEach(람다) 한 곳만 바꿔 끼운다.
 * 핸들러는 대상 메서드 인자를 안 받는다(49-153차 교훈). 안 맞으면 require = 0으로 조용히 빠져 바닐라 그대로.
 */
@Mixin(FontSet.class)
public abstract class FontGlyphScanMixin {

	@Redirect(method = "selectProviders", at = @At(value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/ints/IntSet;forEach(Lit/unimi/dsi/fastutil/ints/IntConsumer;)V"), require = 0)
	private void lunaslight$scanSet(IntSet codePoints, IntConsumer consumer) {
		FontGlyphScan.forEach(codePoints, consumer);
	}
}
