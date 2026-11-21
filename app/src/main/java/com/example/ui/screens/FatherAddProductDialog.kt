package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.data.model.Product
import com.example.domain.CalculationEngine
import com.example.ui.components.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.AlertRed
import com.example.ui.theme.VividGreen
import com.example.util.AppLanguage
import com.example.util.PhotoStorageMode
import com.example.util.ProductImageUtil
import com.example.util.ShopLocale

data class FatherProductWarnings(
    val duplicateName: Boolean = false,
    val sellBelowBuy: Boolean = false,
    val zeroPrice: Boolean = false,
    val highQuantity: Boolean = false
) {
    val hasAny: Boolean get() = duplicateName || sellBelowBuy || zeroPrice || highQuantity
}

fun evaluateFatherProductWarnings(
    rawName: String,
    qty: Double?,
    sellPrice: Double?,
    buyPrice: Double?,
    existingProducts: List<Product>,
    editingProductId: String? = null
): FatherProductWarnings {
    val trimmed = rawName.trim()
    val duplicate = trimmed.isNotBlank() && existingProducts.any { existing ->
        existing.id != editingProductId && (
            existing.name.trim().equals(trimmed, ignoreCase = true) ||
                (existing.nameBn.isNotBlank() && existing.nameBn.trim().equals(trimmed, ignoreCase = true))
            )
    }
    val sellBelowBuy = sellPrice != null && buyPrice != null && sellPrice < buyPrice
    val zeroPrice = (sellPrice != null && sellPrice == 0.0) || (buyPrice != null && buyPrice == 0.0)
    val highQuantity = qty != null && qty > 1000.0
    return FatherProductWarnings(
        duplicateName = duplicate,
        sellBelowBuy = sellBelowBuy,
        zeroPrice = zeroPrice,
        highQuantity = highQuantity
    )
}

@Composable
fun FatherAddProductDialog(
    productToEdit: Product? = null,
    existingProducts: List<Product> = emptyList(),
    defaultCategory: String = "General",
    photoStorageMode: PhotoStorageMode = PhotoStorageMode.SYNC,
    isBusy: Boolean = false,
    externalError: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        nameBn: String,
        category: String,
        unit: String,
        buyPrice: Double,
        sellPrice: Double,
        stock: Double,
        photoUri: String
    ) -> Unit,
    onMoveToBin: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditMode = productToEdit != null
    val amberAccent = Color(0xFFFFC107)
    val highContrastCardBg = Color(0xFF141814)
    val highContrastBorder = Color(0x52FFFFFF)

    val initialName = remember(productToEdit) {
        productToEdit?.let { it.nameBn.ifBlank { it.name } } ?: ""
    }
    val initialUnit = remember(productToEdit) {
        productToEdit?.let { CalculationEngine.effectiveStockUnit(it) } ?: "pcs"
    }
    val initialQty = remember(productToEdit) {
        productToEdit?.let { formatFatherEditableNumber(it.stock) } ?: ""
    }
    val initialSell = remember(productToEdit) {
        productToEdit?.let { formatFatherEditableNumber(it.sellPrice) } ?: ""
    }
    val initialBuy = remember(productToEdit) {
        productToEdit?.let { formatFatherEditableNumber(it.buyPrice) } ?: ""
    }
    val initialPhotoUri = remember(productToEdit) { productToEdit?.photoUri ?: "" }

    var nameInput by remember(productToEdit) { mutableStateOf(initialName) }
    var selectedUnit by remember(productToEdit) { mutableStateOf(initialUnit) }
    var qtyInput by remember(productToEdit) { mutableStateOf(initialQty) }
    var sellPriceInput by remember(productToEdit) { mutableStateOf(initialSell) }
    var buyPriceInput by remember(productToEdit) { mutableStateOf(initialBuy) }
    var photoUriString by remember(productToEdit) { mutableStateOf(initialPhotoUri) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var showMoveToBinConfirm by remember { mutableStateOf(false) }

    val availableUnits = remember(initialUnit) {
        (CalculationEngine.SUPPORTED_UNITS + initialUnit)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    fun cleanupStagedCameraPhotoIfUnused(oldUri: String) {
        if (oldUri != initialPhotoUri &&
            (oldUri.contains("/product_photos/cam_") || oldUri.contains("/product_photos/cropped_"))
        ) {
            ProductImageUtil.deleteLocalPhotoIfOwned(context, oldUri)
        }
    }

    fun handleDismiss() {
        cleanupStagedCameraPhotoIfUnused(photoUriString)
        onDismiss()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cleanupStagedCameraPhotoIfUnused(photoUriString)
            photoUriString = uri.toString()
            validationError = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            validationError = null
            showInAppCamera = true
        } else {
            validationError = "ক্যামেরার অনুমতি নেই — গ্যালারি থেকে ছবি দিন"
        }
    }

    fun onTakePhotoClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            validationError = null
            showInAppCamera = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showInAppCamera) {
        ProductCameraScreen(
            maxDimensionPx = if (photoStorageMode == PhotoStorageMode.SYNC) {
                ProductImageUtil.SYNC_MAX_DIMENSION_PX
            } else {
                ProductImageUtil.PHONE_ONLY_MAX_DIMENSION_PX
            },
            maxBytes = if (photoStorageMode == PhotoStorageMode.SYNC) {
                ProductImageUtil.SYNC_MAX_BYTES
            } else {
                ProductImageUtil.PHONE_ONLY_MAX_BYTES
            },
            onPhotoConfirmed = { capturedUri ->
                cleanupStagedCameraPhotoIfUnused(photoUriString)
                photoUriString = capturedUri.toString()
                validationError = null
                showInAppCamera = false
            },
            onPickFromGallery = {
                showInAppCamera = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCancel = { showInAppCamera = false }
        )
        return
    }

    val qtyParsed = remember(qtyInput) {
        val trimmed = ShopLocale.toAsciiDigits(qtyInput).trim()
        if (trimmed.isEmpty()) null else trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
    val sellParsed = remember(sellPriceInput) {
        val trimmed = ShopLocale.toAsciiDigits(sellPriceInput).trim()
        if (trimmed.isEmpty()) null else trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
    val buyParsed = remember(buyPriceInput) {
        val trimmed = ShopLocale.toAsciiDigits(buyPriceInput).trim()
        if (trimmed.isEmpty()) null else trimmed.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    val warnings = remember(nameInput, qtyParsed, sellParsed, buyParsed, existingProducts, productToEdit) {
        evaluateFatherProductWarnings(
            rawName = nameInput,
            qty = qtyParsed,
            sellPrice = sellParsed,
            buyPrice = buyParsed,
            existingProducts = existingProducts,
            editingProductId = productToEdit?.id
        )
    }

    val resolvedCategory = remember(productToEdit, defaultCategory) {
        productToEdit?.category?.trim()?.ifBlank { null }
            ?: defaultCategory.trim().ifBlank { "General" }
    }

    val unitBangla = ShopLocale.displayUnit(selectedUnit, AppLanguage.BN)
    val summaryName = nameInput.trim().ifBlank { "নাম দিন" }
    val summaryQty = qtyParsed?.let { "${ShopLocale.formatNumber(it, AppLanguage.BN)} $unitBangla" } ?: "— $unitBangla"
    val summarySell = sellParsed?.let { "বিক্রয় ${ShopLocale.formatMoney(it, AppLanguage.BN)}" } ?: "বিক্রয় —"
    val summaryBuy = buyParsed?.let { "ক্রয় ${ShopLocale.formatMoney(it, AppLanguage.BN)}" } ?: "ক্রয় —"
    val oneLineSummary = "$summaryName · $summaryQty · $summarySell · $summaryBuy"

    Dialog(
        onDismissRequest = { if (!isBusy) handleDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = generateSequence(dialogView.parent) { (it as? android.view.View)?.parent }
                .filterIsInstance<DialogWindowProvider>()
                .firstOrNull()
                ?.window
            if (dialogWindow != null) {
                @Suppress("DEPRECATION")
                dialogWindow.clearFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                )
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                dialogWindow.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                ViewCompat.requestApplyInsets(dialogWindow.decorView)
            }
        }

        val safeInsets = WindowInsets.statusBars
            .union(WindowInsets.navigationBars)
            .union(WindowInsets.displayCutout)
            .union(WindowInsets.ime)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070907))
                .testTag("father_add_product_dialog"),
            contentAlignment = Alignment.TopCenter
        ) {
            if (showMoveToBinConfirm && isEditMode && onMoveToBin != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(safeInsets)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = highContrastCardBg,
                        border = BorderStroke(1.5.dp, AlertRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 460.dp)
                            .testTag("father_move_to_bin_confirm_dialog")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = ShopLocale.get("father_move_to_bin_title", AppLanguage.BN),
                                color = AlertRed,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = ShopLocale.format(
                                    "father_move_to_bin_desc",
                                    AppLanguage.BN,
                                    productToEdit?.nameBn?.ifBlank { productToEdit.name } ?: ""
                                ),
                                color = Color.White,
                                fontSize = 19.sp,
                                lineHeight = 25.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showMoveToBinConfirm = false },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, highContrastBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 52.dp)
                                        .testTag("father_bin_cancel_btn")
                                ) {
                                    Text(
                                        text = ShopLocale.get("cancel", AppLanguage.BN),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        showMoveToBinConfirm = false
                                        onMoveToBin()
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AlertRed,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 52.dp)
                                        .testTag("father_bin_confirm_btn")
                                ) {
                                    Text(
                                        text = ShopLocale.get("father_move_to_bin_btn", AppLanguage.BN),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(safeInsets)
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { if (!isBusy) handleDismiss() },
                            shape = CircleShape,
                            color = Color(0x28FFFFFF),
                            border = BorderStroke(1.dp, highContrastBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("father_add_product_close_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "বন্ধ করুন",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isEditMode) "পণ্য ঠিক করুন" else "নতুন পণ্য যোগ করুন",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isEditMode && onMoveToBin != null) {
                            Surface(
                                onClick = { if (!isBusy) showMoveToBinConfirm = true },
                                shape = CircleShape,
                                color = AlertRed.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("father_product_bin_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "বিনে পাঠান",
                                        tint = AlertRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // One-screen scrollable Bangla form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. নাম (Required)
                        FatherLargeTextField(
                            label = "নাম *",
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                validationError = null
                            },
                            placeholder = "যেমন: মিনিকেট চাল",
                            keyboardType = KeyboardType.Text,
                            testTag = "father_product_name_input"
                        )

                        // 2. একক (Required)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "একক *",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableUnits.forEach { unit ->
                                    val isSelected = selectedUnit.equals(unit, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            selectedUnit = unit
                                            validationError = null
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) VividGreen else highContrastCardBg,
                                        border = BorderStroke(
                                            1.4.dp,
                                            if (isSelected) VividGreen else highContrastBorder
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("father_unit_$unit")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = ShopLocale.displayUnit(unit, AppLanguage.BN),
                                                color = if (isSelected) Color(0xFF061204) else Color.White,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. পরিমাণ (Required)
                        FatherLargeTextField(
                            label = "পরিমাণ ($unitBangla) *",
                            value = qtyInput,
                            onValueChange = {
                                qtyInput = it
                                validationError = null
                            },
                            placeholder = "যেমন: ১০",
                            keyboardType = KeyboardType.Decimal,
                            testTag = "father_product_qty_input"
                        )

                        // 4. বিক্রয় মূল্য (Required)
                        FatherLargeTextField(
                            label = "বিক্রয় মূল্য (৳) *",
                            value = sellPriceInput,
                            onValueChange = {
                                sellPriceInput = it
                                validationError = null
                            },
                            placeholder = "যেমন: ১২০",
                            keyboardType = KeyboardType.Decimal,
                            testTag = "father_product_sell_price_input"
                        )

                        // 5. ক্রয় মূল্য (Required)
                        FatherLargeTextField(
                            label = "ক্রয় মূল্য (৳) *",
                            value = buyPriceInput,
                            onValueChange = {
                                buyPriceInput = it
                                validationError = null
                            },
                            placeholder = "যেমন: ১০০",
                            keyboardType = KeyboardType.Decimal,
                            testTag = "father_product_buy_price_input"
                        )

                        // 6. ছবি (ঐচ্ছিক)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = highContrastCardBg,
                            border = BorderStroke(1.2.dp, highContrastBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "ছবি (ঐচ্ছিক)",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (photoUriString.isNotBlank()) {
                                        ProductThumbnail(
                                            photoUri = photoUriString,
                                            category = resolvedCategory,
                                            contentDescription = "পণ্যের ছবি",
                                            modifier = Modifier.size(56.dp),
                                            cornerRadius = 12.dp,
                                            storeId = productToEdit?.storeId.orEmpty(),
                                            productId = productToEdit?.id.orEmpty()
                                        )
                                    }
                                    Surface(
                                        onClick = ::onTakePhotoClick,
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1F2A1D),
                                        border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("father_photo_camera_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = null,
                                                tint = VividGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "ছবি তুলুন",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF1D211D),
                                        border = BorderStroke(1.dp, highContrastBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("father_photo_gallery_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoLibrary,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "গ্যালারি",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                if (photoUriString.isNotBlank()) {
                                    Text(
                                        text = "ছবি সরান",
                                        color = AlertRed,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                cleanupStagedCameraPhotoIfUnused(photoUriString)
                                                photoUriString = ""
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("father_photo_remove_btn")
                                    )
                                }
                            }
                        }

                        // Soft warnings (non-blocking)
                        if (warnings.hasAny) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = amberAccent.copy(alpha = 0.14f),
                                border = BorderStroke(1.2.dp, amberAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("father_warnings_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (warnings.duplicateName) {
                                        FatherWarningRow(
                                            text = "এই নামে আগে থেকেই একটি পণ্য আছে",
                                            testTag = "father_warn_duplicate_name"
                                        )
                                    }
                                    if (warnings.sellBelowBuy) {
                                        FatherWarningRow(
                                            text = "বিক্রয় মূল্য ক্রয় মূল্যের চেয়ে কম",
                                            testTag = "father_warn_sell_below_buy"
                                        )
                                    }
                                    if (warnings.zeroPrice) {
                                        FatherWarningRow(
                                            text = "দাম ০ টাকা দেওয়া হয়েছে",
                                            testTag = "father_warn_zero_price"
                                        )
                                    }
                                    if (warnings.highQuantity) {
                                        FatherWarningRow(
                                            text = "পরিমাণ ১,০০০-এর বেশি দেওয়া হয়েছে",
                                            testTag = "father_warn_high_qty"
                                        )
                                    }
                                }
                            }
                        }

                        val shownError = validationError ?: externalError
                        if (!shownError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = AlertRed.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, AlertRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("father_product_error")
                            ) {
                                Text(
                                    text = shownError,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Pre-save 1-line summary + big "ঠিক আছে, সেভ করুন" button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF172215),
                            border = BorderStroke(1.dp, VividGreen.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = oneLineSummary,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("father_product_summary_line")
                            )
                        }

                        Surface(
                            onClick = {
                                if (isBusy) return@Surface
                                val trimmedName = nameInput.trim()
                                val cleanQty = qtyParsed
                                val cleanSell = sellParsed
                                val cleanBuy = buyParsed
                                when {
                                    trimmedName.isBlank() -> {
                                        validationError = "পণ্যের নাম লিখুন"
                                    }
                                    selectedUnit.isBlank() -> {
                                        validationError = "একক নির্বাচন করুন"
                                    }
                                    qtyInput.isBlank() || cleanQty == null -> {
                                        validationError = "পরিমাণ লিখুন"
                                    }
                                    sellPriceInput.isBlank() || cleanSell == null -> {
                                        validationError = "বিক্রয় মূল্য লিখুন"
                                    }
                                    buyPriceInput.isBlank() || cleanBuy == null -> {
                                        validationError = "ক্রয় মূল্য লিখুন"
                                    }
                                    else -> {
                                        validationError = null
                                        val hasBangla = trimmedName.any { it in '\u0980'..'\u09FF' }
                                        onSave(
                                            trimmedName,
                                            if (hasBangla) trimmedName else "",
                                            resolvedCategory,
                                            selectedUnit.trim(),
                                            cleanBuy,
                                            cleanSell,
                                            CalculationEngine.roundStock(cleanQty),
                                            photoUriString
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = VividGreen,
                            border = BorderStroke(1.5.dp, Color(0xFFA5F27F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 62.dp)
                                .testTag("father_save_product_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF061204),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF061204),
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ঠিক আছে, সেভ করুন",
                                            color = Color(0xFF061204),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FatherLargeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color(0xFF8C948C),
                    fontSize = 20.sp
                )
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141814),
                unfocusedContainerColor = Color(0xFF141814),
                focusedBorderColor = VividGreen,
                unfocusedBorderColor = Color(0x52FFFFFF),
                cursorColor = VividGreen,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun FatherWarningRow(
    text: String,
    testTag: String
) {
    val amberAccent = Color(0xFFFFC107)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = amberAccent,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            color = amberAccent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatFatherEditableNumber(value: Double): String {
    if (!value.isFinite()) return ""
    val roundedLong = value.toLong()
    return if (kotlin.math.abs(value - roundedLong.toDouble()) < 0.005) {
        roundedLong.toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
}
