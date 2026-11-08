package com.shoplite.starter.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shoplite.openapi")
public class ShopliteOpenApiProperties {

	private boolean enabled = true;

	private String title;

	private String description = "ShopLite Microservice REST API Specification";

	private String version = "v1";

	private String contactName = "ShopLite Engineering";

	private String contactUrl = "https://github.com/shoplite-lab";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getContactName() {
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	public String getContactUrl() {
		return contactUrl;
	}

	public void setContactUrl(String contactUrl) {
		this.contactUrl = contactUrl;
	}

}
