package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatFaces;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.StringRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * 49-82차(4-48 후속): 채팅 얼굴 - <b>1.16 · 1.16.1</b>. compat/chatface-legacy(1.16.2~1.19.4)와 같은 그림인데,
 * 이 두 버전은 OrderedText가 아직 없어 채팅 줄이 {@code StringRenderable}로 그려진다
 * ({@code drawWithShadow(MatrixStack, StringRenderable, float, float, int)} - 1.16/1.16.1 javap 실측; render 안에
 * 이 호출이 둘인데 다른 하나는 "밀린 메시지 N개" 줄이라 띄우개가 없어 그냥 지나간다).
 * 첫 글자는 {@code visit(StyledVisitor, Style.EMPTY)}로 본다 - 첫 조각에서 값을 돌려주면 거기서 끝난다.
 */
@Mixin(ChatHud.class)
public abstract class ChatFaceLegacyMixin {

	@Redirect(method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/StringRenderable;FFI)I"),
			require = 0)
	private int lunaslight$faceBeforeLine(TextRenderer tr, MatrixStack matrices, StringRenderable text, float x, float y, int color) {
		if (ChatFaces.enabled && text != null) {
			try {
				Optional<String> found = text.visit((style, part) -> {
					if (part == null || part.isEmpty()) {
						return Optional.empty();   // 빈 조각은 건너뛰고 다음 조각
					}
					String name = part.codePointAt(0) == ChatFaces.SPACER && style != null
						? ChatFaces.nameFromInsertion(style.getInsertion()) : null;
					return Optional.of(name == null ? "" : name);   // 첫 글자를 봤으면 끝
				}, net.minecraft.text.Style.EMPTY);
				if (found.isPresent() && !found.get().isEmpty()) {
					ChatFaces.draw(new DrawContext(matrices), found.get(), Math.round(x), Math.round(y), (color >>> 24) & 0xFF);
				}
			} catch (Throwable ignored) {
				// 얼굴은 덤이다 - 무슨 일이 있어도 글자는 그린다
			}
		}
		return tr.drawWithShadow(matrices, text, x, y, color);
	}
}
