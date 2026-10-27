package com.pritimitra.backend.photos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class PhotoStorageConfig {

	@Bean(destroyMethod = "close")
	S3Client s3Client(@Value("${pritimitra.photos.region}") String region) {
		return S3Client.builder().region(Region.of(region)).build();
	}
}
