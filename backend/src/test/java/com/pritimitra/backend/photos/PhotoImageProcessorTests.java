package com.pritimitra.backend.photos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pritimitra.backend.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

class PhotoImageProcessorTests {

	private static final String LOCATION_MARKER = "GPS-51.5074,-0.1278";

	private final PhotoImageProcessor processor = new PhotoImageProcessor();

	@Test
	void reencodedPngDropsTextMetadataThatCouldCarryLocation() throws IOException {
		byte[] original = pngWithTextChunk(LOCATION_MARKER);
		assertTrue(contains(original, LOCATION_MARKER), "fixture must contain the metadata marker");

		PhotoImageProcessor.ProcessedPhoto result = processor.process(original);

		assertEquals("image/png", result.contentType());
		assertFalse(contains(result.bytes(), LOCATION_MARKER));
		assertFalse(contains(result.bytes(), "tEXt"));
	}

	@Test
	void validImageIsReencodedToSameFormatWithDecodableOutput() throws IOException {
		byte[] original = encode("png", new BufferedImage(10, 6, BufferedImage.TYPE_INT_ARGB));

		PhotoImageProcessor.ProcessedPhoto result = processor.process(original);

		assertEquals("image/png", result.contentType());
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
		assertEquals(10, decoded.getWidth());
		assertEquals(6, decoded.getHeight());
	}

	@Test
	void emptyAndNonImageInputsAreRejectedWithoutDetail() {
		ApiException empty = assertThrows(ApiException.class, () -> processor.process(new byte[0]));
		assertEquals("INVALID_PHOTO", empty.getCode());

		ApiException text = assertThrows(ApiException.class,
				() -> processor.process("not an image".getBytes(StandardCharsets.UTF_8)));
		assertEquals("INVALID_PHOTO", text.getCode());
		assertFalse(text.getMessage().contains("/"));
	}

	@Test
	void dimensionLimitIsCheckedFromTheHeader() throws IOException {
		byte[] tooWide = encode("png", new BufferedImage(PhotoImageProcessor.MAX_DIMENSION + 1, 2,
				BufferedImage.TYPE_BYTE_GRAY));
		ApiException ex = assertThrows(ApiException.class, () -> processor.process(tooWide));
		assertEquals("PHOTO_DIMENSIONS_TOO_LARGE", ex.getCode());
	}

	private static boolean contains(byte[] haystack, String needle) {
		byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
		outer:
		for (int i = 0; i <= haystack.length - n.length; i++) {
			for (int j = 0; j < n.length; j++) {
				if (haystack[i + j] != n[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	private static byte[] encode(String format, BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private static byte[] pngWithTextChunk(String text) throws IOException {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);

		IIOMetadataNode textEntry = new IIOMetadataNode("tEXtEntry");
		textEntry.setAttribute("keyword", "Comment");
		textEntry.setAttribute("value", text);
		IIOMetadataNode textChunk = new IIOMetadataNode("tEXt");
		textChunk.appendChild(textEntry);
		IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
		root.appendChild(textChunk);
		metadata.mergeTree("javax_imageio_png_1.0", root);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, metadata), param);
		} finally {
			writer.dispose();
		}
		return out.toByteArray();
	}
}
