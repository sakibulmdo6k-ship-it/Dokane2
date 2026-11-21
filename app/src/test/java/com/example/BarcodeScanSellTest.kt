package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Product
import com.example.data.model.SaleItem
import com.example.data.repository.StoreRepository
import com.example.domain.BarcodeScanHelper
import com.example.domain.BarcodeStockWarning
import com.example.domain.ShopType
import com.example.ui.components.createProductBarcodeReader
import com.example.ui.components.decodeProductBarcodeFromLuminance
import com.example.ui.screens.AddProductWizardDialog
import com.example.ui.screens.SellScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.LocalAppLanguage
import com.example.util.NetworkMonitor
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class BarcodeScanSellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("storeos_stock_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("storeos_app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Shadows.shadowOf(context.applicationContext as Application)
            .denyPermissions(Manifest.permission.CAMERA)
    }

    private fun createDemoViewModel(userId: String = "owner_1"): Pair<StoreRepository, StoreViewModel> {
        val repo = StoreRepository(db = null, appContext = context).apply {
            configureDemoStore(ShopType.GROCERY)
        }
        val vm = StoreViewModel(
            storeId = "demo_store",
            repository = repo,
            networkMonitor = NetworkMonitor(context),
            currentUserId = userId
        )
        return repo to vm
    }

    @Test
    fun barcodeHelper_handlesBlankDuplicateMatchingAndStockWarnings() {
        val p1 = Product(id = "p1", name = "Miniket Rice", barcode = "8901234567890", stock = 5.0, lowStockLimit = 5.0)
        val p2 = Product(id = "p2", name = "Soyabean Oil", barcode = "", stock = 0.0, lowStockLimit = 3.0)
        val p3 = Product(id = "p3", name = "Atta Flour", barcode = "", stock = 10.0, lowStockLimit = 2.0)
        val list = listOf(p1, p2, p3)

        // Blank barcodes do not collide with other blank barcodes
        assertNull(BarcodeScanHelper.findDuplicateBarcodeProduct("", list))
        assertNull(BarcodeScanHelper.findDuplicateBarcodeProduct("   ", list))

        // Duplicate matching trims and ignores case
        assertEquals(p1, BarcodeScanHelper.findDuplicateBarcodeProduct(" 8901234567890 ", list))
        assertNull(BarcodeScanHelper.findDuplicateBarcodeProduct("8901234567890", list, excludeProductId = "p1"))

        // Matched product lookup
        assertEquals(p1, BarcodeScanHelper.findProductByBarcode("8901234567890", list))
        assertNull(BarcodeScanHelper.findProductByBarcode("9999999999999", list))

        // Low / zero stock warning evaluation
        assertEquals(BarcodeStockWarning.LOW_STOCK, BarcodeScanHelper.evaluateScannedStockWarning(p1, 1.0))
        assertEquals(BarcodeStockWarning.ZERO_OR_OUT_OF_STOCK, BarcodeScanHelper.evaluateScannedStockWarning(p1, 6.0))
        assertEquals(BarcodeStockWarning.ZERO_OR_OUT_OF_STOCK, BarcodeScanHelper.evaluateScannedStockWarning(p2, 1.0))
        assertEquals(BarcodeStockWarning.NONE, BarcodeScanHelper.evaluateScannedStockWarning(p3, 1.0))

        // Unlinked products are sorted ahead of already-barcoded products for linking sheet
        val filtered = BarcodeScanHelper.filterProductsForBarcodeLink(list, "")
        assertEquals(listOf("p3", "p2", "p1"), filtered.map { it.id })
    }

    @Test
    fun zxingOfflineLuminanceDecoder_readsHorizontalAndVerticalBarcodes() {
        val code = "8901234567890"
        val matrix = MultiFormatWriter().encode(code, BarcodeFormat.EAN_13, 320, 140)
        val width = matrix.width + 40
        val height = matrix.height + 40
        val yData = ByteArray(width * height) { 255.toByte() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    yData[(y + 20) * width + (x + 20)] = 0
                }
            }
        }

        val reader = createProductBarcodeReader()
        assertEquals(code, decodeProductBarcodeFromLuminance(yData, width, height, reader))

        // Also verify 90-degree rotated grayscale frame (vertical barcode held in portrait camera)
        val rotatedW = height
        val rotatedH = width
        val verticalY = ByteArray(rotatedW * rotatedH)
        for (y in 0 until height) {
            for (x in 0 until width) {
                verticalY[x * height + (height - 1 - y)] = yData[y * width + x]
            }
        }
        assertEquals(code, decodeProductBarcodeFromLuminance(verticalY, rotatedW, rotatedH, reader))
    }

    @Test
    fun repositoryAndViewModel_blockDuplicateBarcodesPerStore_andAllowOutOfStockScannedCheckout() {
        val (repo, vm) = createDemoViewModel()
        try {
            val added1 = runBlocking {
                repo.addProduct(
                    storeId = "demo_store",
                    product = Product(
                        name = "Lux Soap",
                        sellPrice = 50.0,
                        buyPrice = 40.0,
                        stock = 4.0,
                        barcode = "88001122"
                    )
                )
            }
            val added2 = runBlocking {
                repo.addProduct(
                    storeId = "demo_store",
                    product = Product(
                        name = "Zero Stock Soda",
                        sellPrice = 30.0,
                        buyPrice = 22.0,
                        stock = 0.0,
                        barcode = ""
                    )
                )
            }

            // Duplicate barcode rejected when adding a new product
            vm.addNewProductWizard(
                name = "Another Item",
                sellPrice = 10.0,
                stock = 5.0,
                barcode = " 88001122 "
            )
            assertNotNull(vm.errorMessage.value)
            assertTrue(vm.errorMessage.value!!.contains("88001122"))

            // Duplicate barcode rejected when linking to another product
            var linkErr: String? = null
            vm.linkProductBarcode(
                productId = added2.productId,
                barcode = "88001122",
                onError = { linkErr = it }
            )
            assertNotNull(linkErr)
            assertTrue(linkErr!!.contains("88001122"))

            // Same product keeps its own barcode cleanly on update
            runBlocking {
                repo.updateProduct(
                    storeId = "demo_store",
                    product = Product(
                        id = added1.productId,
                        storeId = "demo_store",
                        name = "Lux Soap 100g",
                        sellPrice = 55.0,
                        buyPrice = 40.0,
                        stock = 4.0,
                        barcode = "88001122"
                    )
                )
            }

            // Father mode cannot link a new barcode
            vm.setFatherModeForMember("owner_1", true)
            ShadowLooper.idleMainLooper()
            assertFalse(vm.canLinkBarcode.value)
            var fatherErr: String? = null
            vm.linkProductBarcode(
                productId = added2.productId,
                barcode = "99887766",
                onError = { fatherErr = it }
            )
            assertNotNull(fatherErr)
            vm.setFatherModeForMember("owner_1", false)
            ShadowLooper.idleMainLooper()

            // Normal checkout without allowOutOfStockProductIds still blocks zero-stock sale
            val zeroItem = SaleItem(
                productId = added2.productId,
                name = "Zero Stock Soda",
                qty = 1.0,
                unit = "pcs",
                stockQty = 1.0,
                buyPrice = 22.0,
                sellPrice = 30.0
            )
            var threwNormal = false
            try {
                runBlocking {
                    repo.executeSaleCheckout(storeId = "demo_store", items = listOf(zeroItem))
                }
            } catch (_: IllegalStateException) {
                threwNormal = true
            }
            assertTrue("Non-scanned zero stock sale must remain guarded", threwNormal)

            // Scanned out-of-stock checkout succeeds when product ID is in allowOutOfStockProductIds
            val inv = runBlocking {
                repo.executeSaleCheckout(
                    storeId = "demo_store",
                    items = listOf(zeroItem),
                    allowOutOfStockProductIds = setOf(added2.productId)
                )
            }
            assertTrue(inv.isNotBlank())
            val sodaAfter = vm.products.value.first { it.id == added2.productId }
            assertEquals("Stock must clamp at 0.0 cleanly", 0.0, sodaAfter.stock, 0.001)
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun addProductWizard_showsBarcodeScanIconInSimpleMode_andBlocksDuplicateBarcode() {
        val existing = listOf(
            Product(id = "prod_existing", name = "Chanachur", barcode = "12345678", stock = 5.0)
        )
        var savedCount = 0

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    AddProductWizardDialog(
                        currentLanguage = AppLanguage.EN,
                        isBusy = false,
                        isSimpleMode = true,
                        isFatherMode = false,
                        existingProducts = existing,
                        canEditBarcode = true,
                        onDismiss = {},
                        onConfirm = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> savedCount++ }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Barcode field and small scan icon are shown in Simple Mode for owner too
        composeRule.onNodeWithTag("wizard_barcode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_barcode_scan_btn").assertIsDisplayed()

        // Entering an already-used barcode blocks advancing to step 2
        composeRule.onNodeWithTag("wizard_name_en").performScrollTo().performTextInput("Tea Bag")
        composeRule.onNodeWithTag("wizard_barcode").performScrollTo().performTextInput("12345678")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wizard_next_btn").performClick()
        composeRule.waitForIdle()

        // Still on Step 1 and error is shown
        composeRule.onAllNodesWithText("Barcode '12345678' is already used by 'Chanachur'")[0]
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(0, savedCount)
    }

    @Test
    fun sellScreen_scanFlow_handlesPermissionDenied_continuousRepeat_lowZeroStock_andUnknownLink() {
        val (repo, vm) = createDemoViewModel("owner_scan")
        val pKnownId: String
        val pOutOfStockId: String
        val pUnlinkedId: String

        runBlocking {
            pKnownId = repo.addProduct(
                "demo_store",
                Product(name = "Parachute Oil", category = "Personal Care", sellPrice = 120.0, buyPrice = 100.0, stock = 10.0, barcode = "111222333")
            ).productId
            Thread.sleep(5)
            pOutOfStockId = repo.addProduct(
                "demo_store",
                Product(name = "Dry Dates", category = "Groceries", sellPrice = 200.0, buyPrice = 160.0, stock = 0.0, barcode = "444555666")
            ).productId
            Thread.sleep(5)
            pUnlinkedId = repo.addProduct(
                "demo_store",
                Product(name = "Fresh Biscuits", category = "Snacks", sellPrice = 40.0, buyPrice = 30.0, stock = 8.0, barcode = "")
            ).productId
        }

        var injectScan: ((String) -> Unit)? = null

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    Box(modifier = Modifier.size(width = 412.dp, height = 850.dp)) {
                        SellScreen(
                            storeViewModel = vm,
                            onBack = {},
                            onTestBarcodeScanHook = { handler -> injectScan = handler }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        try {
            // 1) Permission NOT granted at startup; tap scan button triggers request, and denial keeps Sell working
            composeRule.onNodeWithTag("sell_barcode_scan_btn").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            assertEquals(0, composeRule.onAllNodesWithTag("barcode_scanner_dialog").fetchSemanticsNodes().size)

            val lastReq = Shadows.shadowOf(composeRule.activity).lastRequestedPermission
            assertNotNull("Permission should be requested only after tapping scan button", lastReq)
            assertTrue(lastReq.requestedPermissions.contains(Manifest.permission.CAMERA))

            composeRule.runOnUiThread {
                composeRule.activity.onRequestPermissionsResult(
                    lastReq.requestCode,
                    arrayOf(Manifest.permission.CAMERA),
                    intArrayOf(PackageManager.PERMISSION_DENIED)
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_local_error_text").assertIsDisplayed()

            // Grant permission cleanly and re-tap scan -> continuous scanner opens
            Shadows.shadowOf(context.applicationContext as Application)
                .grantPermissions(Manifest.permission.CAMERA)
            composeRule.onNodeWithTag("sell_barcode_scan_btn").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("barcode_scanner_dialog").assertIsDisplayed()

            // 2) Scan known product twice -> qty becomes 2, toast shows, stays in scan mode
            composeRule.runOnUiThread { injectScan!!.invoke("111222333") }
            composeRule.waitForIdle()
            assertEquals("Added: Parachute Oil", ShadowToast.getTextOfLatestToast())
            composeRule.onNodeWithTag("barcode_scanner_dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("barcode_scan_added_banner").assertIsDisplayed()
            composeRule.onNodeWithText("Done / View Cart (1)").assertIsDisplayed()

            composeRule.runOnUiThread { injectScan!!.invoke("111222333") }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Done / View Cart (2)").assertIsDisplayed()

            // 3) Scan out-of-stock product -> still adds to cart AND shows amber Low/zero stock note
            composeRule.runOnUiThread { injectScan!!.invoke("444555666") }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("barcode_scan_low_stock_banner").assertIsDisplayed()
            composeRule.onNodeWithText("Done / View Cart (3)").assertIsDisplayed()

            // 4) Scan unknown barcode -> opens "New barcode. Which product is this?" sheet with search & cancel
            composeRule.runOnUiThread { injectScan!!.invoke("777888999") }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("unknown_barcode_sheet").assertIsDisplayed()
            composeRule.onNodeWithText("New barcode. Which product is this?").assertIsDisplayed()

            // Search and select "Fresh Biscuits" -> links barcode & adds 1 to cart
            composeRule.onNodeWithTag("unknown_barcode_search_input").performTextInput("Biscuit")
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("unknown_barcode_product_$pUnlinkedId").performClick()
            composeRule.waitForIdle()

            assertEquals(0, composeRule.onAllNodesWithTag("unknown_barcode_sheet").fetchSemanticsNodes().size)
            assertEquals("777888999", vm.products.value.first { it.id == pUnlinkedId }.barcode)
            composeRule.onNodeWithText("Done / View Cart (4)").assertIsDisplayed()

            // Close scanner and verify Checkout shows the lines & amber note on zero-stock item
            composeRule.onNodeWithTag("barcode_done_btn").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_floating_checkout_bar").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("×2").assertIsDisplayed()
            composeRule.onNodeWithTag("cart_line_low_stock_$pOutOfStockId").assertIsDisplayed()
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun sellScreen_fatherMode_allowsScanningToSell_butBlocksLinkingNewBarcode() {
        val (repo, vm) = createDemoViewModel("father_scan")
        runBlocking {
            repo.addProduct(
                "demo_store",
                Product(name = "খেজুর", nameBn = "খেজুর", sellPrice = 350.0, buyPrice = 300.0, stock = 4.0, barcode = "55006600")
            )
        }
        vm.setFatherModeForMember("father_scan", true)

        var injectScan: ((String) -> Unit)? = null
        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.BN) {
                    Box(modifier = Modifier.size(width = 412.dp, height = 850.dp)) {
                        SellScreen(
                            storeViewModel = vm,
                            onBack = {},
                            onTestBarcodeScanHook = { handler -> injectScan = handler }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        try {
            // Scan button is visible in Father mode too
            composeRule.onNodeWithTag("sell_barcode_scan_btn").assertIsDisplayed()

            // Known product scan works for Father
            composeRule.runOnUiThread { injectScan!!.invoke("55006600") }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_floating_checkout_bar").assertIsDisplayed()

            // Unknown barcode in Father mode shows owner-only restriction notice and cannot link
            composeRule.runOnUiThread { injectScan!!.invoke("999000111") }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("unknown_barcode_sheet").assertIsDisplayed()
            composeRule.onNodeWithTag("unknown_barcode_owner_only_notice").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithTag("unknown_barcode_search_input").fetchSemanticsNodes().size)

            composeRule.onNodeWithTag("unknown_barcode_cancel_btn").performClick()
            composeRule.waitForIdle()
            assertEquals(0, composeRule.onAllNodesWithTag("unknown_barcode_sheet").fetchSemanticsNodes().size)
        } finally {
            vm.dispose()
        }
    }
}
