import 'dart:typed_data';

import 'package:http_parser/http_parser.dart';

/// Server-side photo metadata. Storage keys and private URLs are never part of this model.
class ProfilePhoto {
  const ProfilePhoto({
    required this.id,
    required this.contentType,
    required this.sizeBytes,
  });

  factory ProfilePhoto.fromJson(Map<String, dynamic> json) {
    return ProfilePhoto(
      id: json['id'] as String,
      contentType: json['contentType'] as String,
      sizeBytes: json['sizeBytes'] as int,
    );
  }

  final String id;
  final String contentType;
  final int sizeBytes;
}

/// Client-side pre-checks for fast feedback. The backend re-validates every upload.
const maxProfilePhotoBytes = 5 * 1024 * 1024;

/// Detects JPEG or PNG from the file signature. The picked file name and any
/// client-supplied MIME type are deliberately ignored. Returns null for anything else.
MediaType? detectPhotoContentType(Uint8List bytes) {
  if (bytes.length >= 3 &&
      bytes[0] == 0xFF &&
      bytes[1] == 0xD8 &&
      bytes[2] == 0xFF) {
    return MediaType('image', 'jpeg');
  }
  const pngSignature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
  if (bytes.length >= pngSignature.length) {
    for (var i = 0; i < pngSignature.length; i++) {
      if (bytes[i] != pngSignature[i]) {
        return null;
      }
    }
    return MediaType('image', 'png');
  }
  return null;
}
