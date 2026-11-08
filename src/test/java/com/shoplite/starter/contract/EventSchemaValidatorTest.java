package com.shoplite.starter.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventSchemaValidatorTest {

	@Test
	void validatesValidOrderCreatedEventSuccessfully() {
		String validJson = """
				{
				  "eventId": "0b1f5c3e-8a4d-3c2e-9f6a-1d2e3f4a5b6c",
				  "eventType": "order.created",
				  "schemaVersion": "1.0",
				  "occurredAt": "2026-11-08T10:00:00Z",
				  "aggregateId": 49,
				  "payload": {
				    "orderId": 49,
				    "userId": "user-uuid-123",
				    "username": "alice",
				    "items": [
				      { "productId": 1, "productName": "Keyboard", "quantity": 1, "unitPrice": 50.00 }
				    ],
				    "totalAmount": 50.00
				  }
				}
				""";

		EventSchemaValidator.ValidationResult result = EventSchemaValidator.validate("order.created", 1, validJson);
		assertThat(result.isValid()).isTrue();
		assertThat(result.errors()).isEmpty();

		// assertValid should not throw
		EventSchemaValidator.assertValid("order.created", 1, validJson);
	}

	@Test
	void catchesMissingRequiredFieldsAndInvalidTypes() {
		String invalidJson = """
				{
				  "eventId": "not-a-uuid",
				  "eventType": "order.created",
				  "occurredAt": "2026-11-08T10:00:00Z",
				  "payload": {
				    "orderId": 49,
				    "userId": "user-uuid-123",
				    "username": "alice",
				    "totalAmount": -10.00
				  }
				}
				""";

		EventSchemaValidator.ValidationResult result = EventSchemaValidator.validate("order.created", 1, invalidJson);
		assertThat(result.isValid()).isFalse();
		assertThat(result.errors()).isNotEmpty();

		assertThatThrownBy(() -> EventSchemaValidator.assertValid("order.created", 1, invalidJson))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Event validation failed for order.created v1");
	}

	@Test
	void validatesPaymentSucceededEvent() {
		String validJson = """
				{
				  "eventId": "0b1f5c3e-8a4d-3c2e-9f6a-1d2e3f4a5b6c",
				  "eventType": "payment.succeeded",
				  "occurredAt": "2026-11-08T10:00:00Z",
				  "payload": {
				    "paymentId": 101,
				    "paymentReference": "PAY-REF-101",
				    "orderId": 49,
				    "userId": "user-1",
				    "provider": "VNPAY",
				    "amount": 100000.0,
				    "pspTransactionNo": "145892",
				    "reason": null
				  }
				}
				""";

		EventSchemaValidator.assertValid("payment.succeeded", 1, validJson);
	}

	@Test
	void validatesInventoryReservedEvent() {
		String validJson = """
				{
				  "eventId": "0b1f5c3e-8a4d-3c2e-9f6a-1d2e3f4a5b6c",
				  "eventType": "inventory.reserved",
				  "occurredAt": "2026-11-08T10:00:00Z",
				  "payload": {
				    "orderId": 49,
				    "reservationId": 12,
				    "expiresAt": "2026-11-08T10:15:00Z",
				    "items": [
				      { "productId": 1, "quantity": 2 }
				    ]
				  }
				}
				""";

		EventSchemaValidator.assertValid("inventory.reserved", 1, validJson);
	}

	@Test
	void validatesAuditEvent() {
		String validJson = """
				{
				  "eventId": "0b1f5c3e-8a4d-3c2e-9f6a-1d2e3f4a5b6c",
				  "eventType": "audit.recorded",
				  "occurredAt": "2026-11-08T10:00:00Z",
				  "actorSub": "staff-sub-1",
				  "action": "ORDER_REFUND",
				  "outcome": "SUCCESS",
				  "details": {
				    "reason": "customer requested"
				  }
				}
				""";

		EventSchemaValidator.assertValid("audit-event", 1, validJson);
	}

}
