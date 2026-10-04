package org.slf4j;

/**
 * 35차(2026-08-28): org.slf4j는 마인크래프트 1.18.2부터 번들됨(그 이전엔 log4j만 있음) - 1.18.1
 * 이하 버전에서 공유 소스(LunaClientMod.LOGGER 등)가 그대로 컴파일되도록 최소 shim을 만들고
 * 실제 로깅은 그 버전에 진짜로 있는 log4j로 그대로 위임합니다. 이 클래스가 실제로 컴파일에
 * 얹히는 서브프로젝트는 gradle.properties에 legacy_no_slf4j=true가 있는 버전뿐입니다
 * (1.18.2+/1.19.3+/1.20+는 진짜 org.slf4j를 그대로 씀 - claude/nova-mod-todo.md 33~35차 참고).
 *
 * 공유 소스에서 실제로 쓰는 시그니처만 구현: info/warn/error(String), warn/error(String, Throwable),
 * 그리고 혹시 있을 SLF4J 스타일 "{}" 자리표시자 로그를 위한 가변인자 버전(log4j Logger가 이 형태를
 * 그대로 지원하므로 그대로 위임).
 */
public class Logger {
	private final org.apache.logging.log4j.Logger delegate;

	Logger(org.apache.logging.log4j.Logger delegate) {
		this.delegate = delegate;
	}

	public void info(String msg) {
		delegate.info(msg);
	}

	public void info(String format, Object... args) {
		delegate.info(format, args);
	}

	public void warn(String msg) {
		delegate.warn(msg);
	}

	public void warn(String msg, Throwable t) {
		delegate.warn(msg, t);
	}

	public void warn(String format, Object... args) {
		delegate.warn(format, args);
	}

	public void error(String msg) {
		delegate.error(msg);
	}

	public void error(String msg, Throwable t) {
		delegate.error(msg, t);
	}

	public void error(String format, Object... args) {
		delegate.error(format, args);
	}

	public void debug(String msg) {
		delegate.debug(msg);
	}

	public void debug(String format, Object... args) {
		delegate.debug(format, args);
	}
}
