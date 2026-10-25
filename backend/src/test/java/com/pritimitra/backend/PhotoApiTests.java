package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import com.pritimitra.backend.photos.PhotoStorage;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 4 profile photo behaviour. Storage is mocked, so no AWS resources or credentials are needed. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PhotoApiTests {

	private static final String ADULT_DOB = "2000-01-01";
	private static final byte[] PNG_BYTES = concat(
			new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, new byte[32]);
	private static final byte[] JPEG_BYTES = concat(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
			new byte[32]);
	private static final String PHOTOS = "/api/v1/profile/photos";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@MockitoBean
	private PhotoStorage storage;

	@Test
	void unverifiedAccountCannotUploadOrList() throws Exception {
		String sub = newSub();
		mvc.perform(multipart(PHOTOS).file(png()).with(auth(sub))).andExpect(status().isForbidden());
		mvc.perform(get(PHOTOS).with(auth(sub))).andExpect(status().isForbidden());
		verify(storage, never()).put(anyString(), any(), anyString());
	}

	@Test
	void uploadStoresValidatedImageWithServerGeneratedKeyAndReturnsNoStorageDetails() throws Exception {
		String sub = newActiveUser();
		mvc.perform(multipart(PHOTOS).file(png()).with(auth(sub)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.contentType").value("image/png"))
				.andExpect(jsonPath("$.sizeBytes").value(PNG_BYTES.length))
				.andExpect(jsonPath("$.reviewStatus").value("PENDING_REVIEW"))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andExpect(jsonPath("$.bucket").doesNotExist());

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).put(key.capture(), any(), anyString());
		assertEquals(true, key.getValue().startsWith("profiles/"));
	}

	@Test
	void spoofedContentTypeIsRejectedByMagicBytes() throws Exception {
		String sub = newActiveUser();
		MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png",
				"<html>not an image</html>".getBytes(StandardCharsets.UTF_8));
		mvc.perform(multipart(PHOTOS).file(fake).with(auth(sub)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PHOTO"));
		verify(storage, never()).put(anyString(), any(), anyString());
	}

	@Test
	void oversizedPhotoIsRejectedWith413() throws Exception {
		String sub = newActiveUser();
		byte[] big = new byte[(int) (5L * 1024 * 1024 + 1)];
		System.arraycopy(JPEG_BYTES, 0, big, 0, JPEG_BYTES.length);
		MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", big);
		mvc.perform(multipart(PHOTOS).file(file).with(auth(sub)))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
	}

	@Test
	void seventhPhotoIsRejectedWithLimitError() throws Exception {
		String sub = newActiveUser();
		for (int i = 0; i < 6; i++) {
			mvc.perform(multipart(PHOTOS).file(jpeg()).with(auth(sub))).andExpect(status().isCreated());
		}
		mvc.perform(multipart(PHOTOS).file(jpeg()).with(auth(sub)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PHOTO_LIMIT_REACHED"));
	}

	@Test
	void ownerContentIsPrivateAndOtherUsersSeeNotFound() throws Exception {
		String owner = newActiveUser();
		String other = newActiveUser();
		String photoId = uploadAndReturnId(owner);
		when(storage.get(anyString())).thenReturn(PNG_BYTES);

		mvc.perform(get(PHOTOS + "/" + photoId + "/content").with(auth(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Type", "image/png"));

		mvc.perform(get(PHOTOS + "/" + photoId + "/content").with(auth(other)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
	}

	@Test
	void listShowsOnlyTheCallersPhotos() throws Exception {
		String owner = newActiveUser();
		String other = newActiveUser();
		uploadAndReturnId(owner);
		mvc.perform(get(PHOTOS).with(auth(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get(PHOTOS).with(auth(other)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void ownerCanDeleteAndOtherUsersCannot() throws Exception {
		String owner = newActiveUser();
		String other = newActiveUser();
		String photoId = uploadAndReturnId(owner);

		mvc.perform(delete(PHOTOS + "/" + photoId).with(auth(other)))
				.andExpect(status().isNotFound());
		verify(storage, never()).delete(anyString());

		mvc.perform(delete(PHOTOS + "/" + photoId).with(auth(owner)))
				.andExpect(status().isNoContent());
		verify(storage).delete(anyString());

		mvc.perform(get(PHOTOS).with(auth(owner)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void storageFailureDoesNotCreatePhotoRecord() throws Exception {
		String sub = newActiveUser();
		org.mockito.Mockito.doThrow(new com.pritimitra.backend.common.ApiException(
				org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_STORAGE_UNAVAILABLE", "down"))
				.when(storage).put(anyString(), any(), anyString());
		mvc.perform(multipart(PHOTOS).file(png()).with(auth(sub)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("PHOTO_STORAGE_UNAVAILABLE"));
		mvc.perform(get(PHOTOS).with(auth(sub)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	private String uploadAndReturnId(String sub) throws Exception {
		String body = mvc.perform(multipart(PHOTOS).file(png()).with(auth(sub)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String newActiveUser() throws Exception {
		String sub = newSub();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json")
				.content("""
						{"consents":[
						  {"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
						  {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
						""".formatted(legal.termsVersion(), legal.privacyVersion())))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Photo Tester\",\"goals\":[\"DATING\"]}"))
				.andExpect(status().isOk());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/v1/onboarding/complete").with(auth(sub)))
				.andExpect(status().isOk());
		return sub;
	}

	private static MockMultipartFile png() {
		return new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES);
	}

	private static MockMultipartFile jpeg() {
		return new MockMultipartFile("file", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_BYTES);
	}

	private static String newSub() {
		return "sub-" + UUID.randomUUID();
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = new byte[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
