# shoplite-service-starter

Thư viện Spring Boot starter nội bộ (`com.shoplite:shoplite-service-starter`) dùng chung cho các backend service của ShopLite, phát hành lên GitHub Packages (Maven).

**Ranh giới cứng (ADR 0003):** chỉ chứa hạ tầng kỹ thuật cắt ngang (security/JWT, access log, erasure tombstone, transactional outbox, Kafka DLT error handler, audit API). Không chứa DTO nghiệp vụ, không chứa base entity, không chứa logic gửi email.

## Có gì (v0.1.0)

Mọi `@AutoConfiguration` nằm trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, bật theo `@ConditionalOnClass` + property `shoplite.*`, và mọi bean đều có `@ConditionalOnMissingBean` để service ghi đè khi cần.

| Gói | AutoConfiguration | Property bật/tắt | Chức năng |
|---|---|---|---|
| `com.shoplite.starter.security` | `ShopliteSecurityAutoConfiguration` | `shoplite.security.enabled` (`true`) | Stateless JWT resource server cho 3 chế độ: chỉ khách (`ROLE_*`), chỉ nhân viên (`STAFF_*`), hoặc cả 2 realm theo `iss`; kiểm `aud` (`shoplite.security.audiences`); tự gắn `SubjectCaptureFilter` và `ErasedSubjectFilter` |
| `com.shoplite.starter.erasure` | `ShopliteErasureAutoConfiguration` | `shoplite.erasure.enabled` (`false`) | `ErasedSubjects` (advisory lock + tombstone `erased_subjects`), `ErasedSubjectFilter` (403 RFC 9457), `erasureListenerContainerFactory` (retry exponential + `<topic>.<service>.dlt`) |
| `com.shoplite.starter.web` | `ShopliteWebAutoConfiguration` | `shoplite.web.access-log.enabled` (`true`) | `AccessLogFilter`: 1 dòng log JSON mỗi request (`http_method`, `http_route` template, `http_status`, `duration_ms`, `sub` mờ), tự bỏ qua `/actuator/**` |
| `com.shoplite.starter.observability` | `ShopliteObservabilityAutoConfiguration` | tự động | `MdcTaskDecorator`: truyền `trace_id`/`span_id` qua `@Async` |
| `com.shoplite.starter.outbox` | `ShopliteOutboxAutoConfiguration` | `shoplite.outbox.enabled` (`false`) | `OutboxStore` (`FOR UPDATE SKIP LOCKED`), `OutboxRelay` (dừng lượt ở lỗi đầu tiên), `OutboxMaintenance` (reap `SENDING` treo + xóa `SENT` cũ), `OutboxMetrics` (`outbox_*`) |
| `com.shoplite.starter.kafka` | helper | — | `EventEnvelope<T>`, `ShopliteKafkaErrorHandlers` (DLT `<topic>.<service>.dlt`) |
| `com.shoplite.starter.audit` | `ShopliteAuditAutoConfiguration` | tự động | `AuditEvent` (chặn key PII), `AuditRecorder`, `StructuredLogAuditRecorder` (móc nối cho task 5.6) |

## Dùng trong một service

```xml
<dependency>
    <groupId>com.shoplite</groupId>
    <artifactId>shoplite-service-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Cấu hình `application.yaml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI:http://auth.localhost:8000/realms/shoplite}

shoplite:
  # Tùy chọn: thêm realm nhân viên (nhận cả 2 realm theo iss)
  staff-oidc:
    issuer-uri: ${STAFF_OIDC_ISSUER_URI:}
    jwk-set-uri: ${STAFF_OIDC_JWK_SET_URI:}
  # Tùy chọn: kiểm claim aud (ADR 0001 A3)
  security:
    audiences: []
  # Bật tombstone + Kafka listener container factory cho ADR 0002
  erasure:
    enabled: true
```

Nếu service có các route public riêng (ví dụ `product-service` mở `GET /api/products/**`), chỉ cần khai báo một bean `ShopliteAuthorizeRequestsCustomizer`:

```java
@Bean
ShopliteAuthorizeRequestsCustomizer publicCatalogRoutes() {
    return auth -> auth.requestMatchers(HttpMethod.GET, "/api/products/**").permitAll();
}
```

## Chạy và test

```bash
./mvnw verify -DargLine=-Duser.timezone=UTC
```
