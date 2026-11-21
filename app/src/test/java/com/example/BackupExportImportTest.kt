package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Account
import com.example.data.model.AccountAdjustment
import com.example.data.model.BackupImportMode
import com.example.data.model.BackupImportProgress
import com.example.data.model.Customer
import com.example.data.model.CsvExportTarget
import com.example.data.model.Expense
import com.example.data.model.OtherIncome
import com.example.data.model.Product
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.StoreBackupPayload
import com.example.data.model.StoreCategory
import com.example.data.model.StoreMember
import com.example.data.model.StoreNote
import com.example.data.model.StorePermissions
import com.example.data.model.Supplier
import com.example.data.repository.StoreRepository
import com.example.domain.CalculationEngine
import com.example.domain.ShopType
import com.example.ui.components.BackupImportConfirmDialog
import com.example.ui.components.BackupImportPreviewDialog
import com.example.ui.components.CsvExportTargetDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.LocalAppLanguage
import com.example.util.NetworkMonitor
import com.example.util.ShopLocale
import com.example.util.StockPreferences
import com.example.util.StoreBackupCodec
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupExportImportTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("storeos_stock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("storeos_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("user_mode_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "auto_backups").deleteRecursively()
        File(context.cacheDir, "reports").deleteRecursively()
    }

    private fun newRepo(): StoreRepository = StoreRepository(db = null, appContext = context)

    @Test
    fun `export then import into empty store restores products, bin, stock, balances, invoice numbers, and totals identically`() = runBlocking {
        val sourceRepo = newRepo()
        val storeId = "real_shop_source"
        sourceRepo.configureEmptyStoreForTest(
            storeId = storeId,
            ownerId = "owner_1",
            name = "Sonar Bangla Store",
            shopType = ShopType.GROCERY
        )
        sourceRepo.updateStoreShopType(storeId, ShopType.GROCERY, addSuggestedCategories = true)

        val prodOutcome = sourceRepo.addProduct(
            storeId,
            Product(
                name = "মিনিকেট চাল",
                category = "Grocery",
                unit = "kg",
                buyPrice = 60.0,
                sellPrice = 75.0,
                stock = 50.0,
                photoUri = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
                needsReview = true
            )
        )
        val prodId = prodOutcome.productId
        val binProdOutcome = sourceRepo.addProduct(
            storeId,
            Product(
                name = "পুরোনো সাবান",
                category = "General",
                unit = "pcs",
                buyPrice = 25.0,
                sellPrice = 35.0,
                stock = 12.0
            )
        )
        val binProd = sourceRepo.observeProducts(storeId).first().first { it.id == binProdOutcome.productId }
        sourceRepo.moveProductToBin(storeId, binProd, "owner_1")

        val custId = sourceRepo.addCustomer(
            storeId,
            Customer(name = "Karim", mobile = "01711000000", address = "Dhaka", currentBalance = 200.0)
        )
        val supId = sourceRepo.addSupplier(
            storeId,
            Supplier(name = "Rahim Traders", mobile = "01811000000", company = "RT", balance = 500.0)
        )
        val accId = sourceRepo.createAccount(
            Account(storeId = storeId, name = "bKash", type = "bKash", accountNumber = "017", openingBalance = 1500.0)
        )
        sourceRepo.executeSaleCheckout(
            storeId = storeId,
            items = listOf(
                SaleItem(
                    productId = prodId,
                    name = "মিনিকেট চাল",
                    qty = 4.0,
                    unit = "kg",
                    stockQty = 4.0,
                    buyPrice = 60.0,
                    sellPrice = 75.0,
                    discount = 10.0,
                    lineTotal = 290.0,
                    profit = 50.0
                )
            ),
            invoiceDiscount = 5.0,
            paymentType = "baki",
            customerId = custId,
            customerName = "Karim",
            customerPhone = "01711000000",
            accountId = accId,
            accountName = "bKash",
            paidNowAmount = 100.0
        )
        sourceRepo.recordPurchase(
            storeId,
            Purchase(
                supplierId = supId,
                supplierName = "Rahim Traders",
                items = listOf(
                    PurchaseItem(
                        productId = prodId,
                        name = "মিনিকেট চাল",
                        qty = 10.0,
                        unit = "kg",
                        stockQty = 10.0,
                        buyPrice = 60.0,
                        lineTotal = 600.0
                    )
                ),
                totalItems = 10.0,
                totalAmount = 600.0,
                paidAmount = 400.0,
                dueAmount = 200.0,
                paymentType = "cash",
                accountId = accId,
                accountName = "bKash"
            )
        )
        sourceRepo.recordPurchaseReturn(
            storeId,
            PurchaseReturn(supplierId = supId, supplierName = "Rahim Traders", amount = 50.0, reason = "Damaged")
        )
        sourceRepo.recordCustomerPaymentAtomic(
            storeId = storeId,
            customerId = custId,
            customerName = "Karim",
            customerMobile = "01711000000",
            amount = 50.0,
            paymentDate = System.currentTimeMillis(),
            note = "Partial payment",
            accountId = accId,
            accountName = "bKash"
        )
        sourceRepo.recordExpense(Expense(storeId = storeId, amount = 120.0, category = "Rent", accountId = accId, accountName = "bKash", note = "Shop rent"))
        sourceRepo.recordOtherIncome(OtherIncome(storeId = storeId, amount = 80.0, category = "Commission", accountId = accId, accountName = "bKash"))
        sourceRepo.saveNote(StoreNote(storeId = storeId, title = "Reminder", text = "Call supplier"))
        sourceRepo.createCategory(StoreCategory(storeId = storeId, type = "expense", name = "Transport"))

        val exportedPayload = sourceRepo.exportStoreBackup(storeId = storeId, actingUserId = "owner_1")
        assertEquals(StoreBackupPayload.SOURCE_REAL, exportedPayload.source)
        assertEquals(BuildConfig.VERSION_NAME, exportedPayload.appVersion)
        assertEquals(1, exportedPayload.productBin.size)
        val rawJson = StoreBackupCodec.encodeToJson(exportedPayload)

        // Import into a brand new empty real store
        val targetRepo = newRepo()
        val targetStoreId = "real_shop_restored"
        targetRepo.configureEmptyStoreForTest(storeId = targetStoreId, ownerId = "owner_1", name = "")
        val progressUpdates = mutableListOf<BackupImportProgress>()

        val summary = targetRepo.importStoreBackup(
            storeId = targetStoreId,
            rawJson = rawJson,
            mode = BackupImportMode.ADD_TO_EMPTY_STORE,
            actingUserId = "owner_1",
            onProgress = { progressUpdates.add(it) }
        )

        assertTrue(progressUpdates.any { it.progress == 1.0f })
        assertEquals(1, summary.productsAdded)
        assertEquals(1, summary.binAdded)
        assertEquals(1, summary.salesAdded)
        assertEquals(1, summary.purchasesAdded)
        assertEquals(1, summary.purchaseReturnsAdded)
        assertEquals(1, summary.paymentsAdded)

        val restoredProds = targetRepo.observeProducts(targetStoreId).first()
        val sourceProds = sourceRepo.observeProducts(storeId).first()
        assertEquals(sourceProds.size, restoredProds.size)
        assertEquals(sourceProds.first().stock, restoredProds.first().stock, 0.001)
        assertEquals("data:image/jpeg;base64,/9j/4AAQSkZJRg==", restoredProds.first().photoUri)
        assertTrue(restoredProds.first().needsReview)

        val restoredBin = targetRepo.observeProductBin(targetStoreId).first()
        assertEquals(1, restoredBin.size)
        assertEquals("পুরোনো সাবান", restoredBin.first().name)

        val restoredCust = targetRepo.observeCustomers(targetStoreId).first().first()
        val sourceCust = sourceRepo.observeCustomers(storeId).first().first()
        assertEquals(sourceCust.currentBalance, restoredCust.currentBalance, 0.001)

        val restoredSup = targetRepo.observeSuppliers(targetStoreId).first().first()
        val sourceSup = sourceRepo.observeSuppliers(storeId).first().first()
        assertEquals(sourceSup.balance, restoredSup.balance, 0.001)

        val restoredSale = targetRepo.observeSales(targetStoreId).first().first()
        val sourceSale = sourceRepo.observeSales(storeId).first().first()
        assertEquals(sourceSale.invoiceNo, restoredSale.invoiceNo)
        assertEquals(sourceSale.subtotal, restoredSale.subtotal, 0.001)
        assertEquals(sourceSale.discount, restoredSale.discount, 0.001)
        assertEquals(sourceSale.grandTotal, restoredSale.grandTotal, 0.001)
        assertEquals(sourceSale.paidAmount, restoredSale.paidAmount, 0.001)
        assertEquals(sourceSale.dueAmount, restoredSale.dueAmount, 0.001)
        assertEquals(sourceSale.profit, restoredSale.profit, 0.001)
    }

    @Test
    fun `merge skips duplicates and importing the same file twice creates no duplicates or double stock`() = runBlocking {
        val repo = newRepo()
        val storeId = "real_merge_store"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "owner_1", name = "Merge Shop")

        val existingCustId = repo.addCustomer(storeId, Customer(name = "Existing", currentBalance = 300.0))
        val pRice = Product(id = "prod_rice", storeId = storeId, name = "Rice", stock = 20.0, sellPrice = 70.0, buyPrice = 55.0)
        val pOil = Product(id = "prod_oil", storeId = storeId, name = "Oil", stock = 10.0, sellPrice = 180.0, buyPrice = 150.0)
        val payload = StoreBackupPayload(
            source = StoreBackupPayload.SOURCE_REAL,
            appVersion = "1.0",
            shopType = ShopType.GROCERY.id,
            products = listOf(pRice, pOil),
            customers = listOf(Customer(id = existingCustId, storeId = storeId, name = "Modified", currentBalance = 9999.0))
        )
        val json = StoreBackupCodec.encodeToJson(payload)

        val firstSummary = repo.importStoreBackup(storeId, json, BackupImportMode.MERGE, actingUserId = "owner_1")
        assertEquals(2, firstSummary.productsAdded)
        assertEquals(1, firstSummary.customersSkipped)
        assertEquals(300.0, repo.observeCustomers(storeId).first().first().currentBalance, 0.001)
        assertEquals(20.0, repo.observeProducts(storeId).first().first { it.id == "prod_rice" }.stock, 0.001)

        // Auto backup must be created on the phone
        val autoFile = File(firstSummary.autoBackupPath)
        assertTrue(autoFile.exists())

        // Second import of the exact same JSON
        val secondSummary = repo.importStoreBackup(storeId, json, BackupImportMode.MERGE, actingUserId = "owner_1")
        assertEquals(0, secondSummary.totalAdded)
        assertEquals(3, secondSummary.totalSkipped)
        assertEquals(2, repo.observeProducts(storeId).first().size)
        assertEquals(20.0, repo.observeProducts(storeId).first().first { it.id == "prod_rice" }.stock, 0.001)
        assertEquals(300.0, repo.observeCustomers(storeId).first().first().currentBalance, 0.001)
    }

    @Test
    fun `counters continue after highest imported invoice, purchase, return, and payment numbers`() = runBlocking {
        val repo = newRepo()
        val storeId = "real_counter_store"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "owner_1", name = "Counter Shop")

        val prod = Product(id = "p_1", storeId = storeId, name = "Sugar", unit = "kg", buyPrice = 90.0, sellPrice = 110.0, stock = 100.0)
        val sup = Supplier(id = "sup_1", storeId = storeId, name = "Supplier A", balance = 0.0)
        val sale = Sale(
            id = "sale_250",
            storeId = storeId,
            invoiceNo = "INV-0250",
            items = listOf(SaleItem(productId = "p_1", name = "Sugar", qty = 1.0, unit = "kg", stockQty = 1.0, buyPrice = 90.0, sellPrice = 110.0)),
            paymentType = "cash"
        )
        val purchase = Purchase(
            id = "pur_45",
            storeId = storeId,
            purchaseNo = "PUR-0045",
            supplierId = "sup_1",
            supplierName = "Supplier A",
            items = listOf(PurchaseItem(productId = "p_1", name = "Sugar", qty = 1.0, unit = "kg", stockQty = 1.0, buyPrice = 90.0, lineTotal = 90.0)),
            totalAmount = 90.0
        )
        val purReturn = PurchaseReturn(
            id = "ret_12",
            storeId = storeId,
            returnNo = "RET-0012",
            supplierId = "sup_1",
            supplierName = "Supplier A",
            amount = 10.0
        )

        val cust = Customer(id = "cust_1", storeId = storeId, name = "Karim", mobile = "017", currentBalance = 500.0)
        val payment = com.example.data.model.Payment(
            id = "pay_30",
            storeId = storeId,
            paymentNo = "PAY-0030",
            customerId = "cust_1",
            customerName = "Karim",
            amount = 100.0
        )

        val payload = StoreBackupPayload(
            source = StoreBackupPayload.SOURCE_REAL,
            appVersion = "1.0",
            products = listOf(prod),
            customers = listOf(cust),
            suppliers = listOf(sup),
            sales = listOf(sale),
            purchases = listOf(purchase),
            purchaseReturns = listOf(purReturn),
            payments = listOf(payment)
        )
        repo.importStoreBackup(storeId, StoreBackupCodec.encodeToJson(payload), BackupImportMode.ADD_TO_EMPTY_STORE, actingUserId = "owner_1")

        val nextInvoice = repo.executeSaleCheckout(
            storeId = storeId,
            items = listOf(SaleItem(productId = "p_1", name = "Sugar", qty = 1.0, unit = "kg", stockQty = 1.0, buyPrice = 90.0, sellPrice = 110.0)),
            paymentType = "cash"
        )
        val nextPurchase = repo.recordPurchase(
            storeId = storeId,
            purchase = Purchase(
                supplierId = "sup_1",
                supplierName = "Supplier A",
                items = listOf(PurchaseItem(productId = "p_1", name = "Sugar", qty = 1.0, unit = "kg", stockQty = 1.0, buyPrice = 90.0, lineTotal = 90.0)),
                totalAmount = 90.0
            )
        )
        val nextReturn = repo.recordPurchaseReturn(
            storeId = storeId,
            returnItem = PurchaseReturn(supplierId = "sup_1", supplierName = "Supplier A", amount = 5.0)
        )
        val nextPayment = repo.recordCustomerPaymentAtomic(
            storeId = storeId,
            customerId = "cust_1",
            customerName = "Karim",
            customerMobile = "017",
            amount = 50.0,
            paymentDate = System.currentTimeMillis(),
            note = "Next payment"
        )

        assertEquals("INV-0251", nextInvoice)
        assertEquals("PUR-0046", nextPurchase)
        assertEquals("RET-0013", nextReturn)
        assertEquals("PAY-0031", nextPayment)
    }

    @Test
    fun `demo file is refused in real store and real file is refused in demo store`() = runBlocking {
        val demoRepo = newRepo()
        val demoPayload = demoRepo.exportStoreBackup(storeId = "demo_store", actingUserId = "demo_user")
        assertEquals(StoreBackupPayload.SOURCE_DEMO, demoPayload.source)
        val demoJson = StoreBackupCodec.encodeToJson(demoPayload)

        val realRepo = newRepo()
        realRepo.configureEmptyStoreForTest(storeId = "real_shop_x", ownerId = "owner_1", name = "Real Shop")
        val demoIntoRealError = runCatching {
            realRepo.importStoreBackup("real_shop_x", demoJson, BackupImportMode.MERGE, actingUserId = "owner_1")
        }.exceptionOrNull()
        assertTrue(demoIntoRealError is IllegalArgumentException)
        assertEquals(ShopLocale.get("backup_err_demo_into_real", AppLanguage.EN), demoIntoRealError?.message)

        val realJson = StoreBackupCodec.encodeToJson(realRepo.exportStoreBackup("real_shop_x", actingUserId = "owner_1"))
        val realIntoDemoError = runCatching {
            demoRepo.importStoreBackup("demo_store", realJson, BackupImportMode.MERGE, actingUserId = "demo_user")
        }.exceptionOrNull()
        assertTrue(realIntoDemoError is IllegalArgumentException)
        assertEquals(ShopLocale.get("backup_err_real_into_demo", AppLanguage.EN), realIntoDemoError?.message)
    }

    @Test
    fun `damaged or wrong files return clear errors and never crash`() {
        val invalidInputs = listOf(
            "",
            "   ",
            "{not valid json",
            "{\"format\":\"storeos-backup\"} garbage",
            "{\"format\":\"wrong-app\",\"version\":1,\"source\":\"real\",\"store\":{}}",
            "{\"format\":\"storeos-backup\",\"version\":0,\"source\":\"real\",\"store\":{}}",
            "{\"format\":\"storeos-backup\",\"version\":999,\"source\":\"real\",\"store\":{}}",
            "{\"format\":\"storeos-backup\",\"version\":1,\"source\":\"real\"}",
            "{\"format\":\"storeos-backup\",\"version\":1,\"source\":\"cloud\",\"store\":{\"name\":\"X\"}}",
            "{\"format\":\"storeos-backup\",\"version\":1,\"source\":\"real\",\"store\":{\"name\":\"X\"},\"products\":[\"bad_row\"]}"
        )

        for (lang in listOf(AppLanguage.EN, AppLanguage.BN)) {
            for (input in invalidInputs) {
                val err = runCatching { StoreBackupCodec.decodeAndValidateJson(input, lang) }.exceptionOrNull()
                assertTrue("Expected failure for input='$input'", err is IllegalArgumentException)
                assertFalse("Error message must be non-blank", err?.message.isNullOrBlank())
            }
        }

        // Empty store block when data exists
        val repo = newRepo()
        val demoJson = runBlocking {
            StoreBackupCodec.encodeToJson(repo.exportStoreBackup("demo_store", actingUserId = "demo_user"))
        }
        val nonEmptyErr = runCatching {
            runBlocking {
                repo.importStoreBackup("demo_store", demoJson, BackupImportMode.ADD_TO_EMPTY_STORE, actingUserId = "demo_user")
            }
        }.exceptionOrNull()
        assertTrue(nonEmptyErr is IllegalStateException)
        assertEquals(ShopLocale.get("backup_err_store_not_empty", AppLanguage.EN), nonEmptyErr?.message)
    }

    @Test
    fun `export never includes AI key, PIN, staff members, invites, or sign-in data`() = runBlocking {
        val repo = newRepo()
        val storeId = "real_privacy_shop"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "secret_owner_uid_123", name = "Privacy Shop")
        StockPreferences.forStore(context, storeId).setOwnerPin("4321")
        repo.seedDemoMemberForTest(
            StoreMember(
                userId = "staff_uid_999",
                email = "staff@example.com",
                displayName = "Staff Helper",
                role = "staff"
            )
        )

        val payload = repo.exportStoreBackup(storeId = storeId, actingUserId = "secret_owner_uid_123")
        val json = StoreBackupCodec.encodeToJson(payload)
        val root = JSONObject(json)

        assertFalse(root.has("private"))
        assertFalse(root.has("aiKey"))
        assertFalse(root.has("invites"))
        assertFalse(root.has("pin"))
        val storeObj = root.getJSONObject("store")
        assertFalse(storeObj.has("ownerId"))
        assertFalse(storeObj.has("members"))
        assertFalse(storeObj.has("memberUids"))
        assertFalse(json.contains("secret_owner_uid_123"))
        assertFalse(json.contains("staff_uid_999"))
        assertFalse(json.contains("staff@example.com"))
        assertFalse(json.contains("4321"))
        assertFalse(json.contains("owner_pin_hash"))
    }

    @Test
    fun `csv exports write UTF-8 BOM and Bangla correctly with shopname-date naming`() {
        val repo = newRepo()
        val storeId = "real_csv_shop"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "owner_1", name = "Bismillah Traders")
        runBlocking {
            repo.addProduct(
                storeId,
                Product(name = "সয়াবিন তেল", category = "Oil", unit = "ltr", buyPrice = 150.0, sellPrice = 165.0, stock = 24.0)
            )
            repo.addCustomer(storeId, Customer(name = "রফিকুল ইসলাম", mobile = "01799000000", currentBalance = 450.0))
            repo.addSupplier(storeId, Supplier(name = "হক সাপ্লায়ার্স", mobile = "01999000000", company = "Haque", balance = 700.0))
        }

        val vm = StoreViewModel(storeId, repo, NetworkMonitor(context), currentUserId = "owner_1")
        val deadlineOwner = System.currentTimeMillis() + 3_000L
        while (!vm.isOwner.value && System.currentTimeMillis() < deadlineOwner) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(15L)
        }
        assertTrue("Expected vm.isOwner to become true", vm.isOwner.value)

        val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        assertEquals("Bismillah_Traders-$expectedDate.json", vm.buildBackupJsonFileName())
        assertEquals("Bismillah_Traders-$expectedDate-products.csv", vm.buildCsvFileName(CsvExportTarget.PRODUCTS))

        for (target in CsvExportTarget.entries) {
            var exportedFile: File? = null
            vm.exportCsvTarget(target) { exportedFile = it }
            val deadline = System.currentTimeMillis() + 5_000L
            while (exportedFile == null && vm.errorMessage.value == null && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15L)
            }
            assertEquals("Unexpected CSV export error for $target", null, vm.errorMessage.value)
            assertNotNull("Timed out waiting for CSV export of $target", exportedFile)
            val file = exportedFile!!
            assertTrue(file.exists())
            assertTrue(file.name.startsWith("Bismillah_Traders-$expectedDate-${target.slug}"))
            val bytes = file.readBytes()
            assertTrue(bytes.size > 3)
            assertEquals(0xEF.toByte(), bytes[0])
            assertEquals(0xBB.toByte(), bytes[1])
            assertEquals(0xBF.toByte(), bytes[2])
            val utf8Body = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            when (target) {
                CsvExportTarget.PRODUCTS -> assertTrue(utf8Body.contains("সয়াবিন তেল"))
                CsvExportTarget.CUSTOMERS -> assertTrue(utf8Body.contains("রফিকুল ইসলাম"))
                CsvExportTarget.SUPPLIERS -> assertTrue(utf8Body.contains("হক সাপ্লায়ার্স"))
                CsvExportTarget.SALES -> assertTrue(utf8Body.contains("Bismillah Traders"))
            }
        }
        vm.dispose()
    }

    @Test
    fun `settings export and import are hidden in Simple and Father modes and require Owner PIN`() {
        val repo = newRepo()
        val storeId = "real_ui_shop"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "owner_ui", name = "UI Shop")
        StockPreferences.forStore(context, storeId).setOwnerPin("2580")

        val authVm = AuthViewModel(repo)
        val ownerVm = StoreViewModel(storeId, repo, NetworkMonitor(context), currentUserId = "owner_ui")
        composeRule.waitForIdle()

        // 1. Detailed owner mode shows Export/Import and requires Owner PIN
        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    SettingsScreen(
                        authViewModel = authVm,
                        storeViewModel = ownerVm,
                        onBack = {},
                        onLogout = {},
                        onNavigateDestination = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_export_backup_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_import_backup_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_export_csv_btn").performScrollTo().assertIsDisplayed()

        // Tapping Export JSON must ask for Owner PIN
        composeRule.onNodeWithTag("settings_export_backup_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_dialog").assertIsDisplayed()

        // Wrong PIN is rejected
        composeRule.onNodeWithTag("backup_pin_input").performTextInput("1111")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_error_text").assertIsDisplayed()

        // Correct PIN opens Save/Share JSON choice dialog
        composeRule.onNodeWithTag("backup_pin_input").performTextReplacement("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_json_export_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_json_export_cancel_btn").performClick()
        composeRule.waitForIdle()

        // 2. Father mode hides Export/Import
        ownerVm.setFatherModeForMember("owner_ui", enabled = true)
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("settings_export_backup_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("settings_import_backup_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("settings_export_csv_btn").fetchSemanticsNodes().size)

        // 3. Simple mode hides Export/Import
        ownerVm.setFatherModeForMember("owner_ui", enabled = false)
        ownerVm.setSimpleMode(true)
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("settings_export_backup_btn").fetchSemanticsNodes().size)
        ownerVm.dispose()
    }

    @Test
    fun `owner PIN unlock opens JSON Save and Share dialog, CSV dialog shows Save and Share, preview and confirm show source and progress`() {
        val repo = newRepo()
        val storeId = "real_dialog_shop"
        repo.configureEmptyStoreForTest(storeId = storeId, ownerId = "owner_ui", name = "Dialog Shop")
        StockPreferences.forStore(context, storeId).setOwnerPin("2580")

        val authVm = AuthViewModel(repo)
        val ownerVm = StoreViewModel(storeId, repo, NetworkMonitor(context), currentUserId = "owner_ui")
        composeRule.waitForIdle()

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    SettingsScreen(
                        authViewModel = authVm,
                        storeViewModel = ownerVm,
                        onBack = {},
                        onLogout = {},
                        onNavigateDestination = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_export_backup_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_input").performTextInput("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("backup_json_export_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_json_save_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_json_share_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_json_export_cancel_btn").performClick()
        composeRule.waitForIdle()

        // Also unlock CSV dialog with PIN
        composeRule.onNodeWithTag("settings_export_csv_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_pin_input").performTextInput("2580")
        composeRule.onNodeWithTag("backup_pin_confirm_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("backup_csv_target_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_products").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_customers").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_suppliers").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_sales").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_save_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_share_btn").performScrollTo().assertIsDisplayed()

        ownerVm.dispose()
    }

    @Test
    fun `import preview shows source and disables empty-store import when non-empty, confirm dialog shows batch progress bar`() {
        val payload = StoreBackupPayload(
            source = StoreBackupPayload.SOURCE_REAL,
            appVersion = "1.0",
            products = listOf(Product(id = "p1", name = "Item")),
            productBin = listOf(Product(id = "b1", name = "Deleted Item"))
        )
        val preview = com.example.data.model.BackupImportPreview(
            payload = payload,
            version = 1,
            appVersion = "1.0",
            source = StoreBackupPayload.SOURCE_REAL,
            sourceStoreName = "Preview Store",
            shopType = ShopType.GENERAL.id,
            exportedAtMillis = 1_700_000_000_000L,
            targetStoreIsEmpty = false,
            productCategoriesCount = 1,
            categoryRecordsCount = 0,
            productsCount = 1,
            binCount = 1,
            customersCount = 0,
            suppliersCount = 0,
            salesCount = 0,
            purchasesCount = 0,
            purchaseReturnsCount = 0,
            paymentsCount = 0,
            supplierPaymentsCount = 0,
            accountsCount = 1,
            accountAdjustmentsCount = 0,
            expensesCount = 0,
            incomeCount = 0,
            notesCount = 0
        )

        composeRule.setContent {
            var showConfirm by remember { mutableStateOf(false) }
            StoreOSTheme {
                if (!showConfirm) {
                    BackupImportPreviewDialog(
                        preview = preview,
                        currentLanguage = AppLanguage.EN,
                        onChooseMode = { showConfirm = true },
                        onDismiss = {}
                    )
                } else {
                    BackupImportConfirmDialog(
                        preview = preview,
                        mode = BackupImportMode.MERGE,
                        currentLanguage = AppLanguage.EN,
                        isBusy = true,
                        importProgress = BackupImportProgress(
                            progress = 0.6f,
                            completedBatches = 1,
                            totalBatches = 2,
                            isPreparing = false
                        ),
                        onConfirm = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("backup_preview_source_meta").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup_import_mode_empty_btn").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("backup_import_mode_merge_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("backup_import_confirm_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_import_progress_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_import_progress_label").assertIsDisplayed()
    }
}
