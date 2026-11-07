package com.shoplite.starter.erasure;

import com.shoplite.starter.kafka.ShopliteKafkaErrorHandlers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.core.JacksonException;

/**
 * Auto-configures {@link ErasedSubjects} and the dedicated {@code erasureListenerContainerFactory}
 * when {@code shoplite.erasure.enabled=true} (ADR 0002, ADR 0003).
 */
@AutoConfiguration(afterName = {
		"org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
		"org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
		"org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration" })
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(name = "shoplite.erasure.enabled", havingValue = "true")
@EnableConfigurationProperties(ShopliteErasureProperties.class)
public class ShopliteErasureAutoConfiguration {

	public static final String CONTAINER_FACTORY = "erasureListenerContainerFactory";

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	@ConditionalOnMissingBean(ErasedSubjects.class)
	public ErasedSubjects erasedSubjects(JdbcTemplate jdbc) {
		return new ErasedSubjects(jdbc);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ KafkaTemplate.class, ConcurrentKafkaListenerContainerFactory.class })
	static class ErasureKafkaListenerConfiguration {

		@Bean(CONTAINER_FACTORY)
		@ConditionalOnBean({ ConcurrentKafkaListenerContainerFactoryConfigurer.class, ConsumerFactory.class,
				KafkaTemplate.class })
		@ConditionalOnMissingBean(name = CONTAINER_FACTORY)
		public ConcurrentKafkaListenerContainerFactory<Object, Object> erasureListenerContainerFactory(
				ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
				ConsumerFactory<Object, Object> consumerFactory,
				KafkaTemplate<Object, Object> template,
				ShopliteErasureProperties properties,
				@Value("${spring.application.name:service}") String serviceName) {
			ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
			configurer.configure(factory, consumerFactory);
			ShopliteErasureProperties.Retry retry = properties.getRetry();
			factory.setCommonErrorHandler(ShopliteKafkaErrorHandlers.deadLetterErrorHandler(
					template,
					serviceName,
					retry.getInitialInterval(),
					retry.getMultiplier(),
					retry.getMaxInterval(),
					retry.getMaxElapsedTime(),
					JacksonException.class,
					InvalidUserDeletedEventException.class));
			return factory;
		}

	}

}
