package com.shoplite.starter.audit;

import com.shoplite.starter.outbox.OutboxStore;
import com.shoplite.starter.outbox.ShopliteOutboxAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configures {@link AuditRecorder}. Defaults to {@link StructuredLogAuditRecorder},
 * and upgrades to {@link CompositeOutboxAuditRecorder} when {@code shoplite.audit.outbox-enabled=true}
 * and an {@link OutboxStore} bean is available.
 */
@AutoConfiguration(after = ShopliteOutboxAutoConfiguration.class)
@EnableConfigurationProperties(ShopliteAuditProperties.class)
public class ShopliteAuditAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AuditRecorder.class)
	public AuditRecorder shopliteAuditRecorder(
			ShopliteAuditProperties properties,
			ObjectProvider<OutboxStore> outboxStoreProvider,
			ObjectProvider<ObjectMapper> objectMapperProvider) {

		StructuredLogAuditRecorder logRecorder = new StructuredLogAuditRecorder();
		OutboxStore outboxStore = outboxStoreProvider.getIfAvailable();
		ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();

		if (properties.isOutboxEnabled() && outboxStore != null && objectMapper != null) {
			return new CompositeOutboxAuditRecorder(logRecorder, outboxStore, objectMapper);
		}
		return logRecorder;
	}

}
