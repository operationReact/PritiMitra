package com.pritimitra.backend.photos;

/** Port for private object storage. Implementations must never make objects publicly readable. */
public interface PhotoStorage {

	void put(String key, byte[] bytes, String contentType);

	byte[] get(String key);

	void delete(String key);
}
