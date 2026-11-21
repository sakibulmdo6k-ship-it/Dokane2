package com.example.ui.components

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.util.AppLanguage
import com.example.util.ShopLocale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val ScanAlertAmber = Color(0xFFFFB300)

private val PRODUCT_BARCODE_FORMATS = listOf(
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.CODE_93,
    BarcodeFormat.ITF,
    BarcodeFormat.CODABAR,
    BarcodeFormat.QR_CODE,
    BarcodeFormat.DATA_MATRIX
)

fun createProductBarcodeReader(): MultiFormatReader {
    return MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to PRODUCT_BARCODE_FORMATS,
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
}

/**
 * Decodes 1D and 2D retail barcodes from grayscale Y-plane bytes using ZXing.
 * Falls back to a 90-degree rotated plane and GlobalHistogramBinarizer so vertical
 * or low-contrast barcodes scan reliably.
 */
fun decodeProductBarcodeFromLuminance(
    yData: ByteArray,
    width: Int,
    height: Int,
    reader: MultiFormatReader
): String? {
    if (width <= 0 || height <= 0 || yData.size < width * height) return null

    fun tryDecode(data: ByteArray, w: Int, h: Int): String? {
        val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            reader.reset()
            try {
                reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    tryDecode(yData, width, height)?.let { return it }

    // Rotate 90 degrees clockwise for vertical 1D barcodes in portrait preview
    val rotated = ByteArray(width * height)
    for (y in 0 until height) {
        val rowOffset = y * width
        val dstCol = height - 1 - y
        for (x in 0 until width) {
            rotated[x * height + dstCol] = yData[rowOffset + x]
        }
    }
    return tryDecode(rotated, height, width)
}

private fun decodeBarcodeFromImageProxy(
    imageProxy: ImageProxy,
    reader: MultiFormatReader
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride

    val yData = if (rowStride == width) {
        data
    } else {
        ByteArray(width * height).also { clean ->
            for (row in 0 until height) {
                val srcOffset = row * rowStride
                val copyLen = minOf(width, data.size - srcOffset)
                if (copyLen > 0) {
                    System.arraycopy(data, srcOffset, clean, row * width, copyLen)
                }
            }
        }
    }
    return decodeProductBarcodeFromLuminance(yData, width, height, reader)
}

/**
 * Reusable CameraX + ZXing barcode scanner modal in Dark Glass styling.
 *
 * - Single-shot mode (`continuousMode = false`): invokes [onBarcodeScanned] once (e.g. product field).
 * - Continuous mode (`continuousMode = true`): stays open across scans so Sell can scan item after item,
 *   debouncing identical consecutive reads for [repeatCooldownMs] and pausing whenever [isPaused] is true
 *   (e.g. while an unknown-barcode link sheet is displayed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerDialog(
    currentLanguage: AppLanguage,
    continuousMode: Boolean,
    isPaused: Boolean = false,
    title: String? = null,
    subtitle: String? = null,
    lastAddedBanner: String? = null,
    amberStockWarning: String? = null,
    cartCount: Int = 0,
    repeatCooldownMs: Long = 1_400L,
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val singleShotDelivered = remember { AtomicBoolean(false) }
    val pausedState by rememberUpdatedState(isPaused)
    val latestCallback by rememberUpdatedState(onBarcodeScanned)
    val lastCodeRef = remember { AtomicReference<String?>(null) }
    val lastScanTimeRef = remember { AtomicLong(0L) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val scanTransition = rememberInfiniteTransition(label = "barcode_scan_beam")
    val beamProgress by scanTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barcode_beam_y"
    )

    DisposableEffect(lifecycleOwner) {
        var boundProvider: ProcessCameraProvider? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider

                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val reader = createProductBarcodeReader()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (pausedState || (!continuousMode && singleShotDelivered.get())) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val decoded = decodeBarcodeFromImageProxy(imageProxy, reader)?.trim()
                            if (!decoded.isNullOrBlank()) {
                                val now = System.currentTimeMillis()
                                val shouldDeliver = if (continuousMode) {
                                    val prev = lastCodeRef.get()
                                    val elapsed = now - lastScanTimeRef.get()
                                    val allowed = prev == null || !prev.equals(decoded, ignoreCase = true) || elapsed >= repeatCooldownMs
                                    if (allowed) {
                                        lastCodeRef.set(decoded)
                                        lastScanTimeRef.set(now)
                                    }
                                    allowed
                                } else {
                                    singleShotDelivered.compareAndSet(false, true)
                                }
                                if (shouldDeliver) {
                                    mainExecutor.execute {
                                        latestCallback(decoded)
                                    }
                                }
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }

                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> null
                    }

                    if (selector == null) {
                        cameraError = if (currentLanguage == AppLanguage.BN) {
                            "এই ডিভাইসে কোনো ক্যামেরা পাওয়া যায়নি।"
                        } else {
                            "No camera found on this device."
                        }
                        return@addListener
                    }

                    provider.unbindAll()
                    val boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera
                    hasFlash = boundCamera.cameraInfo.hasFlashUnit()
                } catch (e: Exception) {
                    Log.e("BarcodeScannerDialog", "Failed to start camera", e)
                    cameraError = if (currentLanguage == AppLanguage.BN) {
                        "ক্যামেরা চালু করা যায়নি: ${e.localizedMessage ?: ""}"
                    } else {
                        "Unable to open camera: ${e.localizedMessage ?: "Unknown error"}"
                    }
                }
            },
            mainExecutor
        )

        onDispose {
            try {
                camera?.cameraControl?.enableTorch(false)
                boundProvider?.unbindAll()
            } catch (e: Exception) {
                Log.w("BarcodeScannerDialog", "Camera cleanup warning", e)
            }
            analysisExecutor.shutdown()
        }
    }

    val resolvedTitle = title ?: if (currentLanguage == AppLanguage.BN) {
        if (continuousMode) "পণ্য স্ক্যান করুন" else "বারকোড স্ক্যান করুন"
    } else {
        if (continuousMode) "Scan Products" else "Scan Barcode"
    }

    val resolvedSubtitle = subtitle ?: if (currentLanguage == AppLanguage.BN) {
        if (continuousMode) {
            "বারকোড ধরলেই কার্টে যোগ হবে — একের পর এক স্ক্যান করুন"
        } else {
            "পণ্যের বারকোডের দিকে ক্যামেরা ধরুন"
        }
    } else {
        if (continuousMode) {
            "Point at a product barcode — keep scanning item after item"
        } else {
            "Point your camera at the product barcode"
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = edgeToEdgeDialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.safeDialogWindowPadding()
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(24.dp))
                .testTag("barcode_scanner_dialog"),
            color = DarkBackground,
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = VividGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resolvedTitle,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasFlash) {
                            IconButton(
                                onClick = {
                                    val nextState = !isTorchEnabled
                                    isTorchEnabled = nextState
                                    camera?.cameraControl?.enableTorch(nextState)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("barcode_torch_btn")
                            ) {
                                Icon(
                                    imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = if (isTorchEnabled) "Turn off flash" else "Turn on flash",
                                    tint = if (isTorchEnabled) VividGreen else TextSecondary
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("barcode_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = ShopLocale.get("close", currentLanguage),
                                tint = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Viewfinder Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 310.dp)
                        .heightIn(max = 260.dp)
                        .aspectRatio(1.18f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0A0C0E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraError == null) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        ) {
                            val boxW = size.width * 0.78f
                            val boxH = size.height * 0.56f
                            val left = (size.width - boxW) / 2f
                            val top = (size.height - boxH) / 2f
                            val right = left + boxW
                            val bottom = top + boxH

                            drawRect(color = Color(0x88000000))

                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(left, top),
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                blendMode = BlendMode.Clear
                            )

                            val strokeW = 4.dp.toPx()
                            val cornerLen = 24.dp.toPx()
                            drawLine(VividGreen, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(right, top), Offset(right - cornerLen, top), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW, StrokeCap.Round)
                            drawLine(VividGreen, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW, StrokeCap.Round)

                            if (!isPaused) {
                                val beamY = top + (boxH * beamProgress)
                                drawLine(
                                    color = VividGreen.copy(alpha = 0.85f),
                                    start = Offset(left + 12.dp.toPx(), beamY),
                                    end = Offset(right - 12.dp.toPx(), beamY),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22E53935)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = cameraError ?: "",
                                color = AlertRed,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Live Toast / Amber Warning inside continuous scan mode
                AnimatedVisibility(
                    visible = !lastAddedBanner.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x305DD62C),
                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("barcode_scan_added_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VividGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = lastAddedBanner.orEmpty(),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !amberStockWarning.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x2EFFB300),
                        border = BorderStroke(1.dp, ScanAlertAmber.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("barcode_scan_low_stock_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = ScanAlertAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = amberStockWarning.orEmpty(),
                                color = ScanAlertAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = resolvedSubtitle,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (continuousMode) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        color = VividGreen.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("barcode_done_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (cartCount > 0) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = VividGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (cartCount > 0) {
                                    if (currentLanguage == AppLanguage.BN) {
                                        "শেষ / কার্ট দেখুন (${ShopLocale.digits(cartCount.toString(), currentLanguage)})"
                                    } else {
                                        "Done / View Cart ($cartCount)"
                                    }
                                } else {
                                    if (currentLanguage == AppLanguage.BN) "স্ক্যান বন্ধ করুন" else "Close Scanner"
                                },
                                color = VividGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    GlassOutlinedButton(
                        text = ShopLocale.get("cancel", currentLanguage),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "barcode_cancel_btn"
                    )
                }
            }
        }
    }
}
