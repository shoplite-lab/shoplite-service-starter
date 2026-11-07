package com.shoplite.starter.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures {@link StructuredLogAuditRecorder} as the default {@link AuditRecorder}.
 */
@AutoConfiguration
public class ShopliteAuditAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AuditRecorder.class)
	public AuditRecorder shopliteAuditRecorder() {
		return new StructuredLogAuditRecorder();
	}

}
