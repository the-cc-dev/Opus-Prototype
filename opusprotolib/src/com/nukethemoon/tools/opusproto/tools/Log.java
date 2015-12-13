package com.nukethemoon.tools.opusproto.tools;

public class Log {

	public static Out out = null;

	public static void i(Class source, String message) {
		if (out != null) {
			out.logInfo(source.getSimpleName(), message);
		}
	}
	public static void d(Class source, String message) {
		if (out != null) {
			out.logDebug(source.getSimpleName(), message);
		}
	}
	public static void e(Class source, String message) {
		if (out != null) {
			out.logError(source.getSimpleName(), message);
		}
	}

	public interface Out {
		void logError(String tag, String message);
		void logInfo(String tag, String message);
		void logDebug(String tag, String message);
	}


}
