package com.shoplite.starter.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * Auto-configures {@link MdcTaskDecorator} so Spring's {@code @Async} executor preserves MDC keys
 * across thread boundaries.
 */
@AutoConfiguration(beforeName = {
		"org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",
		"org.springframework.boot.task.autoconfigure.TaskExecutionAutoConfiguration" })
public class ShopliteObservabilityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(TaskDecorator.class)
	public TaskDecorator shopliteMdcTaskDecorator() {
		return new MdcTaskDecorator();
	}

}
