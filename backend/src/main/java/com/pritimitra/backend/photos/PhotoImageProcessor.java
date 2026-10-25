package com.pritimitra.backend.photos;

import com.pritimitra.backend.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises profile photos with the JDK image codecs.
 *
 * <p>Only JPEG and PNG are accepted because the JDK can decode them completely. Dimensions and pixel counts are read
 * from the header before any pixel data is allocated. Accepted images are re-encoded from decoded pixels, so EXIF,
 * GPS and other container metadata is not carried over. Error messages are generic and never include paths or keys.
 */
@Component
class PhotoImageProcessor {

	static final int MAX_DIMENSION = 4096;
	static final long MAX_PIXELS = 12_000_000L;

	ProcessedPhoto process(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw invalid();
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (input == null) {
				throw invalid();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw invalid();
			}
			ImageReader reader = readers.next();
			try {
				return decodeAndNormalise(reader, input);
			} finally {
				reader.dispose();
			}
		} catch (ApiException ex) {
			throw ex;
		} catch (IOException | RuntimeException ex) {
			throw invalid();
		}
	}

	private static ProcessedPhoto decodeAndNormalise(ImageReader reader, ImageInputStream input) throws IOException {
		Format format = Format.of(reader.getFormatName());
		if (format == null) {
			throw invalid();
		}
		reader.setInput(input, true, true);

		int width = reader.getWidth(0);
		int height = reader.getHeight(0);
		if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_DIMENSIONS_TOO_LARGE",
					"Photos must be at most " + MAX_DIMENSION + " pixels on each side");
		}
		if ((long) width * height > MAX_PIXELS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_DIMENSIONS_TOO_LARGE",
					"Photos must be at most " + MAX_PIXELS + " pixels in total");
		}

		// Decoders report recoverable damage such as truncated data as warnings, which must reject the file.
		AtomicBoolean corrupt = new AtomicBoolean(false);
		reader.addIIOReadWarningListener((source, message) -> corrupt.set(true));
		BufferedImage decoded = reader.read(0);
		if (decoded == null || corrupt.get()) {
			throw invalid();
		}

		BufferedImage normalised = new BufferedImage(width, height,
				format.hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = normalised.createGraphics();
		try {
			graphics.drawImage(decoded, 0, 0, null);
		} finally {
			graphics.dispose();
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(normalised, format.writerName, out)) {
			throw invalid();
		}
		return new ProcessedPhoto(out.toByteArray(), format.contentType);
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO",
				"Only complete JPEG and PNG images are accepted");
	}

	record ProcessedPhoto(byte[] bytes, String contentType) {
	}

	private enum Format {
		JPEG("jpeg", "image/jpeg", false),
		PNG("png", "image/png", true);

		private final String writerName;
		private final String contentType;
		private final boolean hasAlpha;

		Format(String writerName, String contentType, boolean hasAlpha) {
			this.writerName = writerName;
			this.contentType = contentType;
			this.hasAlpha = hasAlpha;
		}

		static Format of(String readerFormatName) {
			return switch (readerFormatName.toLowerCase(Locale.ROOT)) {
				case "jpeg" -> JPEG;
				case "png" -> PNG;
				default -> null;
			};
		}
	}
}
