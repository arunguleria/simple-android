package org.simple.clinic.widgets.qrcodescanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class MlKitQrCodeAnalyzer(
    private val onQrCodeDetected: OnQrCodeDetected
) : ImageAnalysis.Analyzer {

  private val options = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .build()

  private val scanner = BarcodeScanning.getClient(options)

  @SuppressLint("UnsafeExperimentalUsageError")
  override fun analyze(imageProxy: ImageProxy) {
    val actualImage = imageProxy.image
    if (actualImage != null) {
      val image = InputImage.fromMediaImage(actualImage, imageProxy.imageInfo.rotationDegrees)
      scanner.process(image)
          .addOnSuccessListener { barcodeList ->
            barcodeList.forEach { barcode ->
              val rawValue = barcode.rawValue
              if (rawValue != null) {
                onQrCodeDetected(rawValue)
              }
            }
          }
          .addOnFailureListener {
            // Ignoring failed QR scanning
          }
          .addOnCompleteListener {
            imageProxy.close()
          }
    }
  }
}
