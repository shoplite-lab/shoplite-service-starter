package com.shoplite.starter.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Auto-configures read/write {@link DataSource} splitting backed by
 * {@link DynamicRoutingDataSource} and {@link LazyConnectionDataSourceProxy}.
 */
@AutoConfiguration(beforeName = {
		"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration" })
@ConditionalOnClass({ AbstractRoutingDataSource.class, HikariDataSource.class })
@ConditionalOnProperty(prefix = "shoplite.datasource.routing", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ShopliteDataSourceRoutingProperties.class)
public class ShopliteDataSourceRoutingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public StickyWriteContext stickyWriteContext() {
		return new StickyWriteContext();
	}

	@Bean
	@Primary
	@ConditionalOnMissingBean(DataSource.class)
	public ClosingLazyRoutingDataSource dataSource(ShopliteDataSourceRoutingProperties props,
			StickyWriteContext stickyWriteContext) {
		Assert.hasText(props.getWriterUrl(), "shoplite.datasource.routing.writer-url must not be blank");
		String readerJdbcUrl = StringUtils.hasText(props.getReaderUrl()) ? props.getReaderUrl() : props.getWriterUrl();

		HikariDataSource writer = buildPool("shoplite-writer-pool", props.getWriterUrl(), props,
				props.getWriterMaxPoolSize(), false);
		HikariDataSource reader = buildPool("shoplite-reader-pool", readerJdbcUrl, props, props.getReaderMaxPoolSize(),
				StringUtils.hasText(props.getReaderUrl()));

		DynamicRoutingDataSource routing = new DynamicRoutingDataSource(stickyWriteContext, props.getStickyDuration());
		routing.setTargetDataSources(Map.of(
				DataSourceRoute.WRITER, writer,
				DataSourceRoute.READER, reader));
		routing.setDefaultTargetDataSource(writer);
		routing.afterPropertiesSet();

		return new ClosingLazyRoutingDataSource(routing, writer, reader);
	}

	private static HikariDataSource buildPool(String poolName, String jdbcUrl,
			ShopliteDataSourceRoutingProperties props, int maxPoolSize, boolean readOnlyPool) {
		HikariConfig config = new HikariConfig();
		config.setPoolName(poolName);
		config.setJdbcUrl(jdbcUrl);
		config.setDriverClassName(props.getDriverClassName());
		if (StringUtils.hasText(props.getUsername())) {
			config.setUsername(props.getUsername());
		}
		if (props.getPassword() != null) {
			config.setPassword(props.getPassword());
		}
		config.setMaximumPoolSize(maxPoolSize);
		config.setReadOnly(readOnlyPool);
		return new HikariDataSource(config);
	}

	/**
	 * {@link LazyConnectionDataSourceProxy} wrapper that ensures Spring evaluates transaction
	 * read-only flags before acquiring a connection and cleanly shuts down both pools.
	 */
	static final class ClosingLazyRoutingDataSource extends LazyConnectionDataSourceProxy implements DisposableBean {

		private final HikariDataSource writer;

		private final HikariDataSource reader;

		ClosingLazyRoutingDataSource(DataSource targetDataSource, HikariDataSource writer, HikariDataSource reader) {
			super(targetDataSource);
			this.writer = writer;
			this.reader = reader;
		}

		@Override
		public void destroy() {
			writer.close();
			reader.close();
		}

	}

}
