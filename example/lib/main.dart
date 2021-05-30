import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter_document_picker/flutter_document_picker.dart';

import 'package:path_provider/path_provider.dart';
import 'package:pdf_compressor/pdf_compressor.dart';

void main() {
  runApp(MyApp());
}

const _chars = 'AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz1234567890';
Random _rnd = Random();

String getRandomString(int length) => String.fromCharCodes(Iterable.generate(
    length, (_) => _chars.codeUnitAt(_rnd.nextInt(_chars.length))));

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool isLoading = false;

  @override
  void initState() {
    super.initState();
  }

  void _setLoading(bool loading) {
    setState(() {
      isLoading = loading;
    });
  }

  Future<String> getTempPath() async {
    var dir = await getExternalStorageDirectory();
    await new Directory('${dir!.path}/CompressPdfs').create(recursive: true);

    String randomString = getRandomString(10);
    String pdfFileName = '$randomString.pdf';
    return '${dir.path}/CompressPdfs/$pdfFileName';
  }

  Future<String?> openFilePicker() async {
    String? inputPath = await FlutterDocumentPicker.openDocument();

    if (inputPath == null) return 'inputPath is null';

    String outputPath = await getTempPath();
    _setLoading(true);
    try {
      await PdfCompressor.compressPdfFile(
          inputPath, outputPath, CompressQuality.MEDIUM);
      return outputPath;
    } catch (e) {
      print(e);
      return 'Error';
    }
  }

  String? result;
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Pdf Compressor plugin'),
          ),
          body: isLoading
              ? Center(
                  child: CircularProgressIndicator(),
                )
              : Center(
                  child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    TextButton(
                      child: Text("Pick pdf"),
                      onPressed: () async {
                        result = await openFilePicker().whenComplete(() {
                          _setLoading(false);
                        });
                        setState(() {});
                      },
                    ),
                    Text('Result: $result'),
                  ],
                ))),
    );
  }
}
