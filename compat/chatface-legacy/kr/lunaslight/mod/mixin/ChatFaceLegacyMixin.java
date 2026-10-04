package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ChatFaces;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 49-82차(4-48 후속): 채팅 얼굴 - <b>1.16.2 ~ 1.19.4</b>. 이 시대의 {@code ChatHud#render}는 줄을
 * {@code TextRenderer#drawWithShadow(MatrixStack, OrderedText, float, float, int)}로 그린다(1.16.5·1.17.1·1.18.2·1.19.4
 * javap 실측 - 이 시그니처 호출이 render 안에 딱 하나). 그 호출을 Redirect로 감싸 <b>그리기 직전에 첫 글자를 보고</b>
 * 띄우개면 같은 (x, y)에 얼굴을 그린 뒤 원래대로 글자를 그린다. 얼굴은 shim DrawContext(compat/legacy-era1)의
 * drawTexture(49-82차 신설)로 그리는데, 같은 MatrixStack을 쓰므로 채팅 배율·스크롤 변환을 그대로 따라간다.
 *
 * <p>1.16 · 1.16.1은 OrderedText가 없고(StringRenderable) → compat/chatface-legacy16의 같은 이름 파일.
 * 1.20+는 진짜 DrawContext에 거는 {@link ChatFaceMixin}. 이 파일은 loom-common.gradle이 시대에 맞춰 얹는다.
 *
 * <p>비용: 채팅에 보이는 줄 수만큼(보통 10~20) 줄 글자를 한 번 훑는다(49-208차: 이름마다 얼굴이라 첫 글자만으로는 모자람).
 * 기능이 꺼져 있거나 띄우개가 한 번도 안 붙었으면 첫 줄에서 빠져나간다.
 */
@Mixin(ChatHud.class)
public abstract class ChatFaceLegacyMixin {

	private static final int[] COUNT = new int[1];
	private static final java.util.List<Integer> HIT_AT = new java.util.ArrayList<>();
	private static final java.util.List<String> HIT_NAME = new java.util.ArrayList<>();

	@Redirect(method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/OrderedText;FFI)I"),
			require = 0)
	private int lunaslight$faceBeforeLine(TextRenderer tr, MatrixStack matrices, OrderedText text, float x, float y, int color) {
		if (ChatFaces.enabled && ChatFaces.anyDecorated && text != null) {
			try {
				// 49-208차: 띄우개가 줄 안 어디에나(플레이어 이름마다) 있다 - 줄 전체를 훑어 띄우개마다 앞 글자 폭만큼 옮겨 그린다
				COUNT[0] = 0;
				HIT_AT.clear();
				HIT_NAME.clear();
				text.accept((index, style, codePoint) -> {
					if (codePoint == ChatFaces.SPACER && style != null) {
						String name = ChatFaces.nameFromInsertion(style.getInsertion());
						if (name != null) {
							HIT_AT.add(COUNT[0]);
							HIT_NAME.add(name);
						}
					}
					COUNT[0]++;
					return true;
				});
				if (!HIT_AT.isEmpty()) {
					DrawContext ctx = new DrawContext(matrices);
					for (int i = 0; i < HIT_AT.size(); i++) {
						int k = HIT_AT.get(i);
						int dx = k == 0 ? 0 : tr.getWidth(lunaslight$prefix(text, k));
						ChatFaces.draw(ctx, HIT_NAME.get(i), Math.round(x) + dx, Math.round(y), (color >>> 24) & 0xFF);
					}
				}
			} catch (Throwable ignored) {
				// 얼굴은 덤이다 - 무슨 일이 있어도 글자는 그린다
			}
		}
		return tr.drawWithShadow(matrices, text, x, y, color);
	}

	/** text의 앞 count글자만 보여 주는 OrderedText(폭 재기용). */
	private static OrderedText lunaslight$prefix(OrderedText text, int count) {
		return visitor -> {
			int[] n = {0};
			return text.accept((index, style, codePoint) -> n[0]++ < count && visitor.accept(index, style, codePoint));
		};
	}
}
