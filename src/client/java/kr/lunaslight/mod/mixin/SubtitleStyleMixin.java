package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SubtitleHook;
import net.minecraft.client.gui.hud.SubtitlesHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 49-48차: 자막 색 바꿔 끼우기(SubtitleHook 주석 참고 - 다시 그리지 않고 색 인자만 바꾼다).
 *
 * <p>49-53차: <b>버전별 실제 호출을 javap로 다시 전부 재 봤다.</b> 49-48차에 적어 둔 대상이 세 군데나
 * 틀려 있어서(require = 0이라 조용히 빠지기만 했다) 1.15.2~1.19.4에서는 이 기능이 아무것도 안 하고
 * 있었다. 실측 결과(SubtitlesHud.render 안에서 부르는 것):
 *
 * <pre>
 *  1.15.2        fill(IIIII)                            TextRenderer.draw(String,FFI)           ← 화살표·글자 전부 String
 *  1.16 ~ 1.19.2 fill(MatrixStack,IIIII)                TextRenderer.draw(MatrixStack,String/Text,FFI)
 *  1.19.3~1.19.4 fill(MatrixStack,IIIII)                drawTextWithShadow(MatrixStack,TextRenderer,String/Text,III)
 *  1.20 ~ 1.21.11 DrawContext.fill(IIIII)               DrawContext.drawTextWithShadow(TextRenderer,String/Text,III)
 * </pre>
 *
 * <p>중요: fill·drawTextWithShadow는 DrawableHelper에서 물려받은 것을 <b>이름만 써서</b> 부르기 때문에
 * 바이트코드에 찍힌 소유 클래스가 DrawableHelper가 아니라 <b>SubtitlesHud</b>다(상수 풀 실측). 믹스인은
 * 소유 클래스까지 같아야 맞으므로 SubtitlesHud 쪽을 먼저 적고, 혹시 모를 환경을 위해 DrawableHelper
 * 쪽도 같이 남겨 둔다 - 둘 다 맞아도 색 계산이 멱등(알파는 유지, RGB만 교체)이라 문제없다.
 * 반대로 TextRenderer.draw 는 소유 클래스가 확실해서 구버전에서 가장 믿을 만한 대상이다.
 *
 * <p>글자 색 인자 바로 앞에 있는 Text 인자도 가로채(index 1 / 1.19.4는 2) 그 줄이 무슨 소리인지
 * 기록한다 - 종류별 색은 이 값으로 정해진다. 전부 require = 0이라 자기 버전에 없는 건 조용히 빠진다.
 */
@Mixin(SubtitlesHud.class)
public abstract class SubtitleStyleMixin {

	// ==================== 1.20 ~ 1.21.11 (DrawContext) ====================

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
	private int lunaslight$bg(int color) {
		return SubtitleHook.background(color);
	}

	@ModifyArg(method = "render", index = 1, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"))
	private Text lunaslight$note120(Text value) {
		return SubtitleHook.note(value);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"))
	private int lunaslight$text120(int color) {
		return SubtitleHook.text(color);
	}

	@ModifyArg(method = "render", index = 1, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private Text lunaslight$note121(Text value) {
		return SubtitleHook.note(value);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private int lunaslight$text121(int color) {
		return SubtitleHook.text(color);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"))
	private int lunaslight$arrow120(int color) {
		return SubtitleHook.arrow(color);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private int lunaslight$arrow121(int color) {
		return SubtitleHook.arrow(color);
	}

	// ==================== 1.16 ~ 1.19.2 (TextRenderer.draw) ====================

	@ModifyArg(method = "render", index = 1, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Text;FFI)I"))
	private Text lunaslight$noteLegacy(Text value) {
		return SubtitleHook.note(value);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Text;FFI)I"))
	private int lunaslight$textLegacy(int color) {
		return SubtitleHook.text(color);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I"))
	private int lunaslight$arrowLegacy(int color) {
		return SubtitleHook.arrow(color);
	}

	// 1.15.2 - MatrixStack이 없던 시절. 화살표와 자막 글자가 둘 다 String이라 종류를 알 수 없고,
	// 셋 다 [글자 색]으로 나온다(그래서 1.15.2에서는 [종류별 색] 설정 자체를 만들지 않는다).
	@ModifyArg(method = "render", index = 3, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;FFI)I"))
	private int lunaslight$text1152(int color) {
		return SubtitleHook.arrow(color);
	}

	// ==================== 1.19.3 ~ 1.19.4 (DrawableHelper.drawTextWithShadow) ====================

	@ModifyArg(method = "render", index = 2, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/hud/SubtitlesHud;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private Text lunaslight$note194(Text value) {
		return SubtitleHook.note(value);
	}

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/hud/SubtitlesHud;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private int lunaslight$text194(int color) {
		return SubtitleHook.text(color);
	}

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/hud/SubtitlesHud;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private int lunaslight$arrow194(int color) {
		return SubtitleHook.arrow(color);
	}

	@ModifyArg(method = "render", index = 2, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawableHelper;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private Text lunaslight$note194b(Text value) {
		return SubtitleHook.note(value);
	}

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawableHelper;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	private int lunaslight$text194b(int color) {
		return SubtitleHook.text(color);
	}

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawableHelper;drawTextWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private int lunaslight$arrow194b(int color) {
		return SubtitleHook.arrow(color);
	}

	// ==================== 배경(1.15.2 ~ 1.19.4) ====================

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/hud/SubtitlesHud;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V"))
	private int lunaslight$bgLegacy(int color) {
		return SubtitleHook.background(color);
	}

	@ModifyArg(method = "render", index = 5, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawableHelper;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V"))
	private int lunaslight$bgLegacyB(int color) {
		return SubtitleHook.background(color);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/hud/SubtitlesHud;fill(IIIII)V"))
	private int lunaslight$bg1152(int color) {
		return SubtitleHook.background(color);
	}

	@ModifyArg(method = "render", index = 4, require = 0,
			at = @At(value = "INVOKE",
				target = "Lnet/minecraft/client/gui/DrawableHelper;fill(IIIII)V"))
	private int lunaslight$bg1152b(int color) {
		return SubtitleHook.background(color);
	}
}
