package com.shoplite.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shoplite.starter.security.ShopliteSecurityAutoConfiguration;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = AccessLogFilterTests.TestApp.class,
		properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://auth.test/realms/shoplite")
@AutoConfigureMockMvc
class AccessLogFilterTests {

	@Autowired
	MockMvc mvc;

	private ListAppender<ILoggingEvent> appender;

	private Logger accessLogger;

	@BeforeEach
	void attachAppender() {
		accessLogger = (Logger) LoggerFactory.getLogger(AccessLogFilter.LOGGER_NAME);
		appender = new ListAppender<>();
		appender.start();
		accessLogger.addAppender(appender);
	}

	@AfterEach
	void detachAppender() {
		accessLogger.detachAppender(appender);
	}

	@Test
	void logsRouteTemplateOpaqueSubjectAndSkipsActuator() throws Exception {
		mvc.perform(get("/api/items/42").with(jwt().jwt(j -> j.subject("sub-abc-123").claim("email", "secret@example.com"))))
			.andExpect(status().isOk());

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.getFirst();
		Map<String, Object> kvs = keyValues(event);
		assertThat(kvs).containsEntry("http_method", "GET")
			.containsEntry("http_route", "/api/items/{id}")
			.containsEntry("http_status", 200)
			.containsEntry("sub", "sub-abc-123")
			.containsKey("duration_ms");
		assertThat(event.getFormattedMessage()).doesNotContain("secret@example.com").doesNotContain("42");

		appender.list.clear();
		mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		assertThat(appender.list).isEmpty();
	}

	@Test
	void logsUnmatchedForUnknownRoutesAndAnonymousWhenUnauthenticated() throws Exception {
		mvc.perform(get("/api/unknown/999")).andExpect(status().isUnauthorized());

		assertThat(appender.list).hasSize(1);
		Map<String, Object> kvs = keyValues(appender.list.getFirst());
		assertThat(kvs).containsEntry("http_method", "GET")
			.containsEntry("http_route", AccessLogFilter.UNMATCHED_ROUTE)
			.containsEntry("http_status", 401)
			.containsEntry("sub", "anonymous");
	}

	private static Map<String, Object> keyValues(ILoggingEvent event) {
		return event.getKeyValuePairs()
			.stream()
			.collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
	}

	@Configuration(proxyBeanMethods = false)
	@Import(SampleController.class)
	@ImportAutoConfiguration({
			WebMvcAutoConfiguration.class,
			DispatcherServletAutoConfiguration.class,
			ServletWebSecurityAutoConfiguration.class,
			OAuth2ResourceServerAutoConfiguration.class,
			ShopliteSecurityAutoConfiguration.class,
			ShopliteWebAutoConfiguration.class })
	static class TestApp {
	}

	@RestController
	static class SampleController {

		@GetMapping("/api/items/{id}")
		String item(@PathVariable String id) {
			return "item-" + id;
		}

		@GetMapping("/actuator/health/liveness")
		String health() {
			return "UP";
		}

	}

}
