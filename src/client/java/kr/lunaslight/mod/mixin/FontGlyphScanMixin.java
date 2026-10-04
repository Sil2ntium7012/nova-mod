package kr.lunaslight.mod.mixin;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import kr.lunaslight.mod.util.FontGlyphScan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-159차: 서버 리소스팩 적용 끝의 85~96초 멈춤(playfarm.kr). 자세한 까닭은 {@link FontGlyphScan}.
 * FontStorage가 글자마다 글꼴 목록 전체를 훑는 intSet.forEach(람다) 한 곳만 바꿔 끼운다.
 * 1.20.5 이상은 applyFilters, 1.20 ~ 1.20.4는 setFonts 안에 있다. 지역 변수 타입이 IntSet인지 IntOpenHashSet인지
 * 버전마다 다를 수 있어 두 모양을 다 걸고 둘 다 require = 0(안 맞으면 조용히 빠짐, 바닐라 그대로).
 * 핸들러는 대상 메서드 인자를 안 받는다(49-153차 교훈). fastutil 8.5의 IntConsumer를 쓰므로 1.20+만 넣는다(loom-common.gradle).
 */
@Mixin(targets = "net.minecraft.client.font.FontStorage")
public abstract class FontGlyphScanMixin {

	@Redirect(method = {"applyFilters", "setFonts"}, at = @At(value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/ints/IntSet;forEach(Lit/unimi/dsi/fastutil/ints/IntConsumer;)V"), require = 0)
	private void lunaslight$scanSet(IntSet codePoints, IntConsumer consumer) {
		FontGlyphScan.forEach(codePoints, consumer);
	}

	@Redirect(method = {"applyFilters", "setFonts"}, at = @At(value = "INVOKE",
			target = "Lit/unimi/dsi/fastutil/ints/IntOpenHashSet;forEach(Lit/unimi/dsi/fastutil/ints/IntConsumer;)V"), require = 0)
	private void lunaslight$scanOpenSet(IntOpenHashSet codePoints, IntConsumer consumer) {
		FontGlyphScan.forEach(codePoints, consumer);
	}
}
