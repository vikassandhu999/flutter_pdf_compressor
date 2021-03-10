package com.kaizen404.pdf_compressor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.exceptions.BadPasswordException;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import com.itextpdf.text.xml.xmp.PdfSchema;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** PdfCompressorPlugin */
public class PdfCompressorPlugin implements FlutterPlugin, MethodCallHandler {

  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(
    @NonNull FlutterPluginBinding flutterPluginBinding
  ) {
    channel =
      new MethodChannel(
        flutterPluginBinding.getBinaryMessenger(),
        "pdf_compressor"
      );
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "compressPdf":
        String inputPath = call.argument("inputPath").toString();
        String outputPath = call.argument("outputPath").toString();
        int quality = call.argument("quality");
        try {
          new CompressPdf(inputPath, outputPath, quality).run();
        } catch (Exception e) {
          e.printStackTrace();
        }
        result.success("success");
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  // Main functionality
  private class CompressPdf {

    String inputPath;
    String outputPath;
    int quality;
    final int compressionLevel = 9;

    CompressPdf(String inputPath, String outputPath, int quality) {
      this.inputPath = inputPath;
      this.outputPath = outputPath;
      this.quality = quality;
    }

    public void run() throws Exception {
      PdfReader reader = new PdfReader(this.inputPath);
      optimizeAllXrefObjectsFromReader(reader);
      reader.removeUnusedObjects();
      reader.removeFields();
      saveCompressedPdfFromReader(reader);
      reader.close();
    }

    private void saveCompressedPdfFromReader(PdfReader reader)
      throws Exception {
      PdfStamper stamper = new PdfStamper(
        reader,
        new FileOutputStream(this.outputPath)
      );
      stamper.setFullCompression();
      stamper.close();
    }

    private void optimizeAllXrefObjectsFromReader(PdfReader reader)
      throws Exception {
      int xrefSize = reader.getXrefSize();
      for (int iter = 1; iter <= xrefSize; iter++) {
        PdfObject pdfObject = reader.getPdfObject(iter);

        if (!objectIsStream(pdfObject)) {
          continue;
        }

        PRStream pRStream = (PRStream) pdfObject;

        if (subtypeIsImage(pRStream)) {
          compressXrefImageFromPRStream(pRStream);
          continue;
        }
      }
    }

    private void compressXrefImageFromPRStream(PRStream pRStream)
      throws Exception {
      byte[] imageAsBytes = new PdfImageObject(pRStream).getImageAsBytes();
      Bitmap imageBitmap = BitmapFactory.decodeByteArray(
        imageAsBytes,
        0,
        imageAsBytes.length
      );

      if (imageBitmap != null) {
        int width = imageBitmap.getWidth();
        int height = imageBitmap.getHeight();

        Bitmap outputImageBitmap = Bitmap.createBitmap(
          width,
          height,
          Bitmap.Config.ARGB_8888
        );
        new Canvas(outputImageBitmap)
        .drawBitmap(imageBitmap, 0.0f, 0.0f, (Paint) null);
        if (!imageBitmap.isRecycled()) {
          imageBitmap.recycle();
        }

        ByteArrayOutputStream outputImageStream = new ByteArrayOutputStream();
        outputImageBitmap.compress(
          Bitmap.CompressFormat.JPEG,
          this.quality,
          outputImageStream
        );
        if (!outputImageBitmap.isRecycled()) {
          outputImageBitmap.recycle();
        }

        resetPRStreamForImage(
          pRStream,
          outputImageStream.toByteArray(),
          width,
          height
        );
        outputImageStream.close();
      }
    }

    private void resetPRStreamForImage(
      PRStream stream,
      byte[] data,
      int width,
      int height
    ) {
      stream.clear();
      stream.setData(data, false, this.compressionLevel);
      stream.put(PdfName.TYPE, PdfName.XOBJECT);
      stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
      stream.put(PdfName.FILTER, PdfName.DCTDECODE);
      stream.put(PdfName.WIDTH, new PdfNumber(width));
      stream.put(PdfName.HEIGHT, new PdfNumber(height));
      stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
      stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);
    }

    private boolean subtypeIsImage(PRStream stream) {
      PdfObject object = stream.get(PdfName.SUBTYPE);
      return (
        object != null && object.toString().equals(PdfName.IMAGE.toString())
      );
    }

    private boolean objectIsStream(PdfObject object) {
      return (object != null && object.isStream());
    }
  }
}
