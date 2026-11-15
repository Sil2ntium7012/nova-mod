package kr.lunaslight.mod.mixin;

import net.minecraft.util.HttpUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

/**
 * 49-658차(다른 유저 로그: 핑이 튀어 재접속 → 서버 리소스팩 무한 로딩): 그 유저 기록에 "Mismatched hash … Existing file not found or had
 * mismatched hash"(핑 튈 때 받다 끊긴 팩) 뒤로 받기 끝/실패 줄이 끝까지 없음 = 다시 받기가 멈춤. 바닐라 HttpUtil.downloadFile은 연결에
 * 시간 제한을 안 걸어서, 네트워크가 끊긴 채 멈추면 영원히 기다린다. 연결 15초, 읽기 30초 제한을 건다 - 멈추면 실패로 끝나고
 * (팩 받기 실패 = 다시 들어가거나 ServerPackRescue가 다시 받기) 무한 로딩에 갇히지 않는다. 정상 다운로드는 그대로(데이터가 오는 동안엔 안 끊김).
 */
@Mixin(HttpUtil.class)
public abstract class HttpTimeoutMixin {

	@Redirect(method = "downloadFile", at = @At(value = "INVOKE", target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;"), require = 0)
	private static URLConnection lunaslight$timeouts(URL url, Proxy proxy) throws IOException {
		URLConnection c = url.openConnection(proxy);
		try {
			if (c.getConnectTimeout() <= 0) {
				c.setConnectTimeout(15_000);
			}
			if (c.getReadTimeout() <= 0) {
				c.setReadTimeout(30_000);
			}
		} catch (Throwable ignored) {
		}
		return c;
	}
}
