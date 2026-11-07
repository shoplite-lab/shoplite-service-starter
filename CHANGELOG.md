# Changelog

## [0.1.0] - 2026-11-07

- `security`: `ShopliteSecurityAutoConfiguration` và `ShopliteJwtSupport` cho JWT 2 realm (`shoplite` → `ROLE_*`, `shoplite-internal` → `STAFF_*`), chọn theo `iss`, kèm kiểm `aud` (`shoplite.security.audiences`, ADR 0001 A3).
- `erasure`: `ErasedSubjects`, `ErasedSubjectFilter`, `AccountErasedException`, `UserDeletedEvent` và `erasureListenerContainerFactory` (ADR 0002).
- `web`: `AccessLogFilter` ghi đúng 1 dòng access log có cấu trúc mỗi HTTP request (`http_method`, `http_route` template, `http_status`, `duration_ms`, `sub` mờ), mặc định bỏ qua `/actuator/**`.
- `observability`: `MdcTaskDecorator` giữ MDC (`trace_id`, `span_id`) qua `@Async`.
- `outbox` & `kafka`: `EventEnvelope<T>`, `ShopliteKafkaErrorHandlers`, `OutboxStore` (`FOR UPDATE SKIP LOCKED`), `OutboxRelay`, `OutboxMaintenance`, `OutboxMetrics`.
- `audit`: `AuditEvent` (từ chối key chứa dữ liệu cá nhân), `AuditRecorder`, `StructuredLogAuditRecorder`.
