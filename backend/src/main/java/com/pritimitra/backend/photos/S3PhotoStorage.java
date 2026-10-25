package com.pritimitra.backend.photos;

import com.pritimitra.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Component
class S3PhotoStorage implements PhotoStorage {

	private final S3Client s3;
	private final String bucket;

	S3PhotoStorage(S3Client s3, @Value("${pritimitra.photos.bucket:}") String bucket) {
		this.s3 = s3;
		this.bucket = bucket;
	}

	@Override
	public void put(String key, byte[] bytes, String contentType) {
		requireBucket();
		try {
			s3.putObject(PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.contentType(contentType)
					.serverSideEncryption(ServerSideEncryption.AES256)
					.build(), RequestBody.fromBytes(bytes));
		} catch (SdkException ex) {
			throw unavailable();
		}
	}

	@Override
	public byte[] get(String key) {
		requireBucket();
		try {
			ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(
					GetObjectRequest.builder().bucket(bucket).key(key).build());
			return object.asByteArray();
		} catch (SdkException ex) {
			throw unavailable();
		}
	}

	@Override
	public void delete(String key) {
		requireBucket();
		try {
			s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException ex) {
			throw unavailable();
		}
	}

	private void requireBucket() {
		if (bucket == null || bucket.isBlank()) {
			throw unavailable();
		}
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_STORAGE_UNAVAILABLE",
				"Photo storage is not available");
	}
}
