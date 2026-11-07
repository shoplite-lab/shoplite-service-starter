package com.shoplite.starter.observability;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the caller's SLF4J {@link MDC} (such as {@code trace_id} and {@code span_id}) into
 * {@code @Async} worker threads and restores the previous context on exit.
 */
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> callerMdc = MDC.getCopyOfContextMap();
		return () -> {
			Map<String, String> previous = MDC.getCopyOfContextMap();
			try {
				if (callerMdc != null) {
					MDC.setContextMap(callerMdc);
				}
				else {
					MDC.clear();
				}
				runnable.run();
			}
			finally {
				if (previous != null) {
					MDC.setContextMap(previous);
				}
				else {
					MDC.clear();
				}
			}
		};
	}

}
