package org.slf4j;

/** 35차: Logger.java 참고 - log4j LogManager로 그대로 위임하는 최소 shim. */
public class LoggerFactory {
	private LoggerFactory() {
	}

	public static Logger getLogger(String name) {
		return new Logger(org.apache.logging.log4j.LogManager.getLogger(name));
	}

	public static Logger getLogger(Class<?> clazz) {
		return new Logger(org.apache.logging.log4j.LogManager.getLogger(clazz));
	}
}
