package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.misc.TabListLimitModule;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 49-42차: 탭 목록 인원 제한.
 *
 * <p>49-55차(4-46, 사용자: "탭 목록 인원 제한이 안 되는 것 같음"): 자르는 자리를 <b>바닐라가 80명으로
 * 자르는 그 숫자</b>로 옮겼다. 바이트코드 실측으로 버전마다 그 80이 어디 있는지 다시 찾았다:
 *
 * <pre>
 *  1.15.2 ~ 1.19.2   render(…)               안에 bipush 80        (목록을 render 안에서 바로 만든다)
 *  1.19.4 ~ 1.21.11  collectPlayerEntries()  안에 ldc2_w 80L  →  Stream.limit(J)
 * </pre>
 *
 * 각 버전에서 그 80은 <b>딱 한 번</b>만 나온다(전수 확인). 상수를 바꿔치기하는 게 가장 확실하고,
 * 덤으로 <b>구버전 17개에서도 제한이 걸린다</b> - 예전에는 1.19.4 이상만 됐다.
 *
 * <p>아래 collectPlayerEntries RETURN 가로채기는 그대로 둔다(둘 다 걸려도 이미 줄어든 목록이라 무해).
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

	/**
	 * 1.19.4+ : {@code .limit(80L)}. 49-124차(페이지): 바닐라가 80에서 자르면 81번째부터 영영 못 본다.
	 * 켜져 있으면 <b>자르지 않게 풀고</b>(아주 큰 수), 아래 RETURN에서 지금 페이지만큼만 잘라 돌려준다.
	 */
	@ModifyConstant(method = "getPlayerInfos", constant = @Constant(longValue = 80L), require = 0)
	private long lunaslight$cap(long original) {
		return TabListLimitModule.isActive() ? 100_000L : original;
	}

	/**
	 * 1.15.2 ~ 1.19.2 : render 안에서 목록을 80명으로 자르는 자리. 이 시대엔 collectPlayerEntries가 없어
	 * 페이지 자르기를 못 하므로, 풀지 않고 예전처럼 [한 페이지 인원]까지만 보이게 둔다(화면이 넘치지 않게).
	 */
	@ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 80), require = 0)
	private int lunaslight$capLegacy(int original) {
		return TabListLimitModule.isActive() ? Math.min(original, TabListLimitModule.perPage()) : original;
	}

	/**
	 * 49-195차: 페이지 표시를 바닥글 맨 아래 줄로 - 그리는 동안만 바닥글에 붙였다가(HEAD) 원래대로 되돌린다(RETURN).
	 * 인자는 안 받는다(버전마다 render 인자가 다르다). 없는 버전은 require = 0이라 조용히 빠진다.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void lunaslight$pageFooter(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		TabListLimitModule.beforeRender(this);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void lunaslight$pageFooterRestore(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		TabListLimitModule.afterRender(this);
	}

	/** 49-124차: 전체 목록에서 지금 페이지(page × perPage) 구간만 돌려준다. 전체 인원은 모듈에 알려 페이지 수를 만든다. */
	@Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$pageEntries(CallbackInfoReturnable<List<?>> cir) {
		if (!TabListLimitModule.isActive()) {
			return;
		}
		List<?> list = cir.getReturnValue();
		if (list == null) {
			return;
		}
		TabListLimitModule.setTotal(list.size());
		int per = TabListLimitModule.perPage();
		int from = Math.min(list.size(), TabListLimitModule.page() * per);
		int to = Math.min(list.size(), from + per);
		if (from != 0 || to != list.size()) {
			cir.setReturnValue(new java.util.ArrayList<>(list.subList(from, to)));
		}
	}

	/**
	 * 49-124차(사용자: "탭리스트에 루나 쓰는 사람 별표 - 페더처럼"): 같은 서버에서 루나를 쓰는 플레이어의 이름 앞에
	 * 루나 별을 붙인다. 누가 루나 유저인지는 site_presence(런처가 올림)로 알아낸다({@link LunaSocial}).
	 *
	 * <p>이름을 돌려주는 {@code getPlayerName}(Text 반환)에 붙였다 - 핑 아이콘 쪽에 그리려면 DrawContext가
	 * 필요한데 그 타입은 1.20 미만엔 없어 40버전 빌드가 깨진다. Text는 전 버전에 있어 안전하다. 메서드가 없는
	 * 아주 옛 버전에선(require=0) 조용히 안 붙는다.
	 */
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$lunaBadge(net.minecraft.client.multiplayer.PlayerInfo entry,
			CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
		try {
			// 49-124차: GameProfile은 1.21.9+에서 record로 바뀌어 getName()→name()이다. 리플렉션으로 둘 다 시도.
			Object prof = entry == null ? null : entry.getProfile();
			Object nm = prof == null ? null : kr.lunaslight.mod.util.LunaCompat.invokeNoArg(prof, "getName");
			if (nm == null && prof != null) {
				nm = kr.lunaslight.mod.util.LunaCompat.invokeNoArg(prof, "name");
			}
			String name = nm instanceof String ? (String) nm : null;
			if (name == null) {
				return;
			}
			// 49-201차: 자리 비움이면 이름 뒤에 회색 Zzz(나 자신은 이 컴퓨터의 자리 비움, 남은 루나 접속 정보)
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			boolean self = mc != null && mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString());
			boolean afk = self ? kr.lunaslight.mod.module.impl.render.AfkModule.isAfkNow()
					: kr.lunaslight.mod.util.LunaSocial.isAfkLunaPlayer(name);
			boolean luna = kr.lunaslight.mod.util.LunaSocial.isLunaPlayerOnServer(name);
			net.minecraft.network.chat.Component original = cir.getReturnValue();
			// 49-209차(사용자: "클라들끼리 별 뜨는 게 안 보이는 경우가 있어"): 탭 꾸미기 플러그인 서버는 칸마다 가짜 프로필
			// (이름이 " 001" 같은 자리표시)을 쓰고 진짜 닉네임은 표시 이름에만 넣는다 - 프로필 이름으로는 못 찾으니
			// 표시 글자 안에 루나 유저 닉네임이 낱말로 들어 있으면 그 사람으로 본다.
			if (!luna && !self && original != null) {
				String shown = kr.lunaslight.mod.util.LunaSocial.lunaNameIn(original.getString());
				if (shown != null) {
					name = shown;
					luna = true;
					afk = afk || kr.lunaslight.mod.util.LunaSocial.isAfkLunaPlayer(shown);
				}
			}
			if (original == null || (!luna && !afk)) {
				return;
			}
			if (afk) {
				original = kr.lunaslight.mod.util.LunaCompat.join(original,
						kr.lunaslight.mod.util.LunaCompat.coloredText(" Zzz", 0xAAAAAA));
				if (!luna) {
					cir.setReturnValue(original);
					return;
				}
			}
			// 49-136차(사용자: "별모양으로 해달라니까 왜 다이아몬드야, 너무 커"): 로고 글리프(icons.ttf, 크게 나옴) 대신
			// 49-191차(사용자: "우리 로고 별 쓰라고 - 흰색에 너무 크지 않게"): 로고 가운데의 네 갈래 반짝이 별을 흰색으로
			// (lunastar.png 32×32를 글자 높이 7로 줄여 그림 - 대문자 높이와 같다). 한 글자(\uE100).
			net.minecraft.network.chat.Component star = null;
			try {
				star = kr.lunaslight.mod.util.LunaCompat.styledText("\uE100",
						kr.lunaslight.mod.util.LunaCompat.styleWithFontNamed("lunastar"));
			} catch (Throwable ignored) {
				// 아래 글자 별로
			}
			if (star == null) {
				star = kr.lunaslight.mod.util.LunaCompat.coloredText("\u2726", 0xFFFFFFFF);   // ✦(글꼴을 못 쓰는 옛 버전)
			}
			// 49-203차: 칭호/접두가 있으면 별을 닉네임 바로 앞에(별 뒤 한 칸). 못 찾으면 예전처럼 맨 앞.
			net.minecraft.network.chat.Component placed = kr.lunaslight.mod.util.PlayerStateHook.badgeBeforeName(original, name, star);
			cir.setReturnValue(placed != null ? placed : kr.lunaslight.mod.util.LunaCompat.join(
					star, kr.lunaslight.mod.util.LunaCompat.textLiteral(" "), original));
		} catch (Throwable ignored) {
		}
	}
}
