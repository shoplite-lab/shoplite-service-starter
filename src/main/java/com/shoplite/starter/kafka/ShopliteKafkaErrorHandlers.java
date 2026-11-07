package com.shoplite.starter.kafka;

import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Builds Kafka listener error handlers that retry with exponential back-off and then park failed
 * records on the consumer service's own dead-letter topic {@code <source-topic>.<service-name>.dlt}
 * (ADR 0002, ADR 0003).
 */
public final class ShopliteKafkaErrorHandlers {

	private ShopliteKafkaErrorHandlers() {
	}

	@SafeVarargs
	public static DefaultErrorHandler deadLetterErrorHandler(KafkaTemplate<Object, Object> template,
			String serviceName,
			Duration initialInterval,
			double multiplier,
			Duration maxInterval,
			Duration maxElapsedTime,
			Class<? extends Exception>... notRetryable) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
				(record, ex) -> new TopicPartition(record.topic() + "." + serviceName + ".dlt", -1));
		ExponentialBackOff backOff = new ExponentialBackOff(initialInterval.toMillis(), multiplier);
		backOff.setMaxInterval(maxInterval.toMillis());
		backOff.setMaxElapsedTime(maxElapsedTime.toMillis());
		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
		if (notRetryable != null && notRetryable.length > 0) {
			handler.addNotRetryableExceptions(notRetryable);
		}
		return handler;
	}

}
