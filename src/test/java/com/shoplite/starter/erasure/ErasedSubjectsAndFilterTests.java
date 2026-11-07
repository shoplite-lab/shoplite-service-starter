package com.shoplite.starter.erasure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shoplite.starter.security.ShopliteSecurityAutoConfiguration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ErasedSubjectsAndFilterTests.TestApp.class, properties = {
		"shoplite.erasure.enabled=true",
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=http://auth.test/realms/shoplite" })
@AutoConfigureMockMvc
class ErasedSubjectsAndFilterTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ErasedSubjects erasedSubjects;

	@Autowired
	MockMvc mvc;

	private final Set<String> tombstones = ConcurrentHashMap.newKeySet();

	@BeforeEach
	void stubJdbc() {
		tombstones.clear();
		when(jdbc.query(eq("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))"), any(ResultSetExtractor.class), any()))
			.thenReturn(null);
		when(jdbc.update(eq("INSERT INTO erased_subjects (sub) VALUES (?) ON CONFLICT (sub) DO NOTHING"), any(Object.class)))
			.thenAnswer(inv -> {
				tombstones.add(inv.getArgument(1, String.class));
				return 1;
			});
		when(jdbc.queryForObject(eq("SELECT EXISTS (SELECT 1 FROM erased_subjects WHERE sub = ?)"), eq(Boolean.class), any()))
			.thenAnswer(inv -> tombstones.contains(inv.getArgument(2, String.class)));
	}

	@Test
	void lockRequiresAnActiveTransaction() {
		assertThatThrownBy(() -> erasedSubjects.lock(UUID.randomUUID().toString()))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void recordsTombstoneIdempotentlyAndFilterRejectsErasedSubjectWith403ProblemDetail() throws Exception {
		String erasedSub = UUID.randomUUID().toString();
		String activeSub = UUID.randomUUID().toString();

		erasedSubjects.record(erasedSub);
		erasedSubjects.record(erasedSub);

		assertThat(erasedSubjects.isErased(erasedSub)).isTrue();
		assertThat(erasedSubjects.isErased(activeSub)).isFalse();
		assertThat(erasedSubjects.isErased(null)).isFalse();
		verify(jdbc, org.mockito.Mockito.times(2))
			.query(eq("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))"), any(ResultSetExtractor.class), eq(erasedSub));

		mvc.perform(get("/api/ping").with(jwt().jwt(j -> j.subject(erasedSub))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(ErasedSubjects.DELETED_DETAIL));

		mvc.perform(get("/api/ping").with(jwt().jwt(j -> j.subject(activeSub))))
			.andExpect(status().isOk());
	}

	@Configuration(proxyBeanMethods = false)
	@Import(PingController.class)
	@ImportAutoConfiguration({
			TransactionAutoConfiguration.class,
			WebMvcAutoConfiguration.class,
			DispatcherServletAutoConfiguration.class,
			ServletWebSecurityAutoConfiguration.class,
			OAuth2ResourceServerAutoConfiguration.class,
			ShopliteErasureAutoConfiguration.class,
			ShopliteSecurityAutoConfiguration.class })
	static class TestApp {

		@Bean
		JdbcTemplate jdbcTemplate() {
			return mock(JdbcTemplate.class);
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			return new AbstractPlatformTransactionManager() {
				@Override
				protected Object doGetTransaction() {
					return new Object();
				}

				@Override
				protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
				}

				@Override
				protected void doCommit(DefaultTransactionStatus status) {
				}

				@Override
				protected void doRollback(DefaultTransactionStatus status) {
				}
			};
		}

	}

	@RestController
	static class PingController {

		@GetMapping("/api/ping")
		String ping() {
			return "pong";
		}

	}

}
