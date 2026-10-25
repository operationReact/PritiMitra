package com.pritimitra.backend.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.pritimitra.backend.identity.CognitoProperties;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({ LegalProperties.class, CognitoProperties.class, RetentionProperties.class })
class ApplicationConfig {

	/** Injected so date-of-birth and age rules can be tested against a fixed date. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
