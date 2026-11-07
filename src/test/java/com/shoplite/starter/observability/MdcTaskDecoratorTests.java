package com.shoplite.starter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTests {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void propagatesCallerMdcIntoWorkerThreadAndRestoresAfterwards() throws Exception {
		MdcTaskDecorator decorator = new MdcTaskDecorator();
		MDC.put("trace_id", "trace-123");
		MDC.put("span_id", "span-456");

		AtomicReference<String> workerTraceId = new AtomicReference<>();
		Runnable decorated = decorator.decorate(() -> workerTraceId.set(MDC.get("trace_id")));

		CompletableFuture.runAsync(decorated).get();

		assertThat(workerTraceId.get()).isEqualTo("trace-123");
		assertThat(MDC.get("trace_id")).isEqualTo("trace-123");
	}

}
