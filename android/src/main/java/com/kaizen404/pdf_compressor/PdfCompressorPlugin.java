package com.kaizen404.pdf_compressor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import androidx.annotation.NonNull;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

/** PdfCompressorPlugin */
public class PdfCompressorPlugin implements FlutterPlugin, MethodCallHandler {

  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "pdf_compressor");
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

    private void saveCompressedPdfFromReader(PdfReader reader) throws Exception {
      PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(this.outputPath));
      stamper.setFullCompression();
      stamper.close();
    }

    private void optimizeAllXrefObjectsFromReader(PdfReader reader) throws Exception {
      int xrefSize = reader.getXrefSize();
      for (int iter = 1; iter <= xrefSize; iter++) {
        PdfObject pdfObject = reader.getPdfObject(iter);

        if (!objectIsStream(pdfObject)) {
          continue;
        }

        PRStream pRStream = (PRStream) pdfObject;

        if (subtypeIsImage(pRStream)) {
          compressXrefImageFromPRStream2(pRStream);
        }
      }
    }

    /**
     * 
     * @param pRStream
     * @throws Exception
     */
    private void compressXrefImageFromPRStream(PRStream pRStream) throws Exception {
      byte[] imageAsBytes = new PdfImageObject(pRStream).getImageAsBytes();
      Bitmap imageBitmap = BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length);

      if (imageBitmap != null) {
        int width = imageBitmap.getWidth();
        int height = imageBitmap.getHeight();

        Bitmap outputImageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(outputImageBitmap).drawBitmap(imageBitmap, 0.0f, 0.0f, (Paint) null);
        if (!imageBitmap.isRecycled()) {
          imageBitmap.recycle();
        }

        ByteArrayOutputStream outputImageStream = new ByteArrayOutputStream();
        outputImageBitmap.compress(Bitmap.CompressFormat.JPEG, this.quality, outputImageStream);
        if (!outputImageBitmap.isRecycled()) {
          outputImageBitmap.recycle();
        }

        resetPRStreamForImage(pRStream, outputImageStream.toByteArray(), width, height);
        outputImageStream.close();
      }
    }

    /**
     * 
     * @param pRStream
     * @throws Exception
     */
    private void compressXrefImageFromPRStream2(PRStream pRStream) throws Exception {
      byte[] imageAsBytes = new PdfImageObject(pRStream).getImageAsBytes();
      ByteArrayOutputStream outputImageStream = new ByteArrayOutputStream();

      Bitmap resultBitmap = compressImage(imageAsBytes, quality, outputImageStream);

      int width = resultBitmap.getWidth();
      int height = resultBitmap.getHeight();

      resetPRStreamForImage(pRStream, outputImageStream.toByteArray(), width, height);
      outputImageStream.close();
    }

    private void resetPRStreamForImage(PRStream stream, byte[] data, int width, int height) {
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
      return (object != null && object.toString().equals(PdfName.IMAGE.toString()));
    }

    private boolean objectIsStream(PdfObject object) {
      return (object != null && object.isStream());
    }

    /**
     * 
     * @param imageAsBytes
     * @param quality
     * @param outputImageStream
     * @throws Exception
     */
    private Bitmap compressImage(byte[] imageAsBytes, int quality, ByteArrayOutputStream outputImageStream)
        throws Exception {
      Bitmap scaledBitmap = null;

      BitmapFactory.Options options = new BitmapFactory.Options();

      // by setting this field as true, the actual bitmap pixels are not loaded in the
      // memory. Just the bounds are loaded. If
      // you try the use the bitmap here, you will get null.
      options.inJustDecodeBounds = true;
      Bitmap bmp = BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length, options);

      int actualHeight = options.outHeight;
      int actualWidth = options.outWidth;

      // max Height and width values of the compressed image is taken as 816x612
      float maxHeight = 816.0f;
      float maxWidth = 612.0f;
      float imgRatio = actualWidth / actualHeight;
      float maxRatio = maxWidth / maxHeight;

      // width and height values are set maintaining the aspect ratio of the image

      if (actualHeight > maxHeight || actualWidth > maxWidth) {
        if (imgRatio < maxRatio) {
          imgRatio = maxHeight / actualHeight;
          actualWidth = (int) (imgRatio * actualWidth);
          actualHeight = (int) maxHeight;
        } else if (imgRatio > maxRatio) {
          imgRatio = maxWidth / actualWidth;
          actualHeight = (int) (imgRatio * actualHeight);
          actualWidth = (int) maxWidth;
        } else {
          actualHeight = (int) maxHeight;
          actualWidth = (int) maxWidth;

        }
      }

      // setting inSampleSize value allows to load a scaled down version of the
      // original image
      options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

      // inJustDecodeBounds set to false to load the actual bitmap
      options.inJustDecodeBounds = false;

      // this options allow android to claim the bitmap memory if it runs low on
      // memory
      options.inPurgeable = true;
      options.inInputShareable = true;
      options.inTempStorage = new byte[16 * 1024];

      // load the bitmap from its path
      bmp = BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length, options);
      scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);

      float ratioX = actualWidth / (float) options.outWidth;
      float ratioY = actualHeight / (float) options.outHeight;
      float middleX = actualWidth / 2.0f;
      float middleY = actualHeight / 2.0f;

      Matrix scaleMatrix = new Matrix();
      scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

      Canvas canvas = new Canvas(scaledBitmap);
      canvas.setMatrix(scaleMatrix);
      canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2,
          new Paint(Paint.FILTER_BITMAP_FLAG));

      // scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
      //     true);

      scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputImageStream);
      return scaledBitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
      final int height = options.outHeight;
      final int width = options.outWidth;
      int inSampleSize = 1;

      if (height > reqHeight || width > reqWidth) {
        final int heightRatio = Math.round((float) height / (float) reqHeight);
        final int widthRatio = Math.round((float) width / (float) reqWidth);
        inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
      }
      final float totalPixels = width * height;
      final float totalReqPixelsCap = reqWidth * reqHeight * 2;
      while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
        inSampleSize++;
      }

      return inSampleSize;
    }

  }
}
