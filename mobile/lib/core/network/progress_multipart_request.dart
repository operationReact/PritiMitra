import 'package:http/http.dart' as http;

/// Multipart request that reports bytes handed to the transport. This is
/// upload progress, not server acceptance; the server result is still the
/// only confirmation that a photo was stored.
class ProgressMultipartRequest extends http.MultipartRequest {
  ProgressMultipartRequest(super.method, super.url, {this.onProgress});

  final void Function(int sentBytes, int totalBytes)? onProgress;

  @override
  http.ByteStream finalize() {
    final stream = super.finalize();
    final callback = onProgress;
    if (callback == null) {
      return stream;
    }
    final totalBytes = contentLength;
    var sentBytes = 0;
    return http.ByteStream(
      stream.map((chunk) {
        sentBytes += chunk.length;
        callback(sentBytes, totalBytes);
        return chunk;
      }),
    );
  }
}
