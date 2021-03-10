import 'dart:async';

import 'package:flutter/services.dart';

enum CompressQuality { LOW, MEDIUM, HIGH }

class PdfCompressor {
  static const MethodChannel _channel = const MethodChannel('pdf_compressor');

  static Future<void> compressPdfFile(
      String inputPath, String outputPath, CompressQuality quality) async {
    Map<String, dynamic> args = <String, dynamic>{};

    args.putIfAbsent("inputPath", () => inputPath);
    args.putIfAbsent("outputPath", () => outputPath);

    switch (quality) {
      case CompressQuality.LOW:
        args.putIfAbsent("quality", () => 10);
        break;
      case CompressQuality.MEDIUM:
        args.putIfAbsent("quality", () => 30);
        break;
      case CompressQuality.HIGH:
        args.putIfAbsent("quality", () => 50);
        break;
      default:
        args.putIfAbsent("quality", () => 50);
    }

    return await _channel.invokeMethod('compressPdf', args);
  }
}
