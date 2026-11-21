package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.StoreRepository
import com.example.ui.components.ShopNavTab
import com.example.ui.screens.MainShopScreen
import com.example.ui.screens.SellScreen
import com.example.ui.screens.StockScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BusinessNavDestination
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.LocalAppLanguage
import com.example.util.NetworkMonitor
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
class FatherAddProductTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun clearPrefs() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("storeos_stock_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("storeos_app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun createViewModels(userId: String = "father_user"): Pair<AuthViewModel, StoreViewModel> {
        val repository = StoreRepository(db = null, appContext = context)
        val authViewModel = AuthViewModel(repository)
        val storeViewModel = StoreViewModel(
            storeId = "demo_store",
            repository = repository,
            networkMonitor = NetworkMonitor(context),
            currentUserId = userId
        )
        return authViewModel to storeViewModel
    }

    @Test
    fun `father adds product from home, it appears in stock with needsReview true, and existing products are unchanged`() {
        val (authViewModel, storeViewModel) = createViewModels("father_1")
        storeViewModel.setFatherModeForMember(memberId = "father_1", enabled = true)

        val initialExistingCount = storeViewModel.products.value.size
        assertTrue(initialExistingCount > 0)
        assertTrue(storeViewModel.products.value.all { !it.needsReview })

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides storeViewModel.currentLanguage.value) {
                    MainShopScreen(authViewModel = authViewModel, storeViewModel = storeViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        // Big "পণ্য যোগ করুন" button on Father home below Sell
        composeRule.onNodeWithTag("home_new_sale_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("father_add_product_btn").assertIsDisplayed()
        composeRule.onNodeWithText("পণ্য যোগ করুন").assertIsDisplayed()
        composeRule.onNodeWithTag("father_add_product_btn").performClick()
        composeRule.waitForIdle()

        // One-screen Bangla form without wholesale/MRP/barcode/conversion fields
        composeRule.onNodeWithTag("father_add_product_dialog").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("wizard_wholesale_price").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("wizard_mrp").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("wizard_barcode").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("father_product_name_input").performTextInput("নতুন মুড়ি")
        composeRule.onNodeWithTag("father_unit_kg").performScrollTo().performClick()
        composeRule.onNodeWithTag("father_product_qty_input").performScrollTo().performTextInput("25")
        composeRule.onNodeWithTag("father_product_sell_price_input").performScrollTo().performTextInput("90")
        composeRule.onNodeWithTag("father_product_buy_price_input").performScrollTo().performTextInput("75")
        composeRule.waitForIdle()

        // One-line summary & big save button visible before saving
        composeRule.onNodeWithTag("father_product_summary_line").assertIsDisplayed()
        composeRule.onNodeWithTag("father_save_product_btn").assertIsDisplayed()
        composeRule.onNodeWithText("ঠিক আছে, সেভ করুন").assertIsDisplayed()

        composeRule.onNodeWithTag("father_save_product_btn").performClick()
        composeRule.waitForIdle()

        // Dialog dismissed and product saved
        assertEquals(0, composeRule.onAllNodesWithTag("father_add_product_dialog").fetchSemanticsNodes().size)
        val allProducts = storeViewModel.products.value
        assertEquals(initialExistingCount + 1, allProducts.size)

        val added = allProducts.firstOrNull { it.name == "নতুন মুড়ি" }
        assertNotNull("Added product must appear in Stock products list", added)
        assertTrue("needsReview must be true", added!!.needsReview)
        assertEquals("father_1", added.createdBy)
        assertNotNull("createdAt must be populated", added.createdAt)
        assertEquals("kg", added.unit)
        assertEquals(25.0, added.stock, 0.001)
        assertEquals(90.0, added.sellPrice, 0.001)
        assertEquals(75.0, added.buyPrice, 0.001)
        assertTrue("Category must be automatically set", added.category.isNotBlank())

        // Pre-existing products stay unchanged (needsReview == false)
        val existingUnchanged = allProducts.filter { it.id != added.id }
        assertEquals(initialExistingCount, existingUnchanged.size)
        assertTrue("Existing products must remain needsReview == false", existingUnchanged.all { !it.needsReview })
    }

    @Test
    fun `father add product shows all soft warnings and still allows saving with warnings`() {
        val (authViewModel, storeViewModel) = createViewModels("father_warn")
        storeViewModel.setFatherModeForMember(memberId = "father_warn", enabled = true)
        val existingName = storeViewModel.products.value.first().name

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides storeViewModel.currentLanguage.value) {
                    MainShopScreen(authViewModel = authViewModel, storeViewModel = storeViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("father_add_product_btn").performClick()
        composeRule.waitForIdle()

        // 1) Duplicate name + 2) Qty > 1000 + 3) Sell below buy
        composeRule.onNodeWithTag("father_product_name_input").performTextInput(existingName)
        composeRule.onNodeWithTag("father_product_qty_input").performScrollTo().performTextInput("1500")
        composeRule.onNodeWithTag("father_product_sell_price_input").performScrollTo().performTextInput("50")
        composeRule.onNodeWithTag("father_product_buy_price_input").performScrollTo().performTextInput("80")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("father_warn_duplicate_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("father_warn_sell_below_buy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("father_warn_high_qty").performScrollTo().assertIsDisplayed()

        // 4) Price of zero warning (sell price = 0)
        composeRule.onNodeWithTag("father_product_sell_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("father_product_sell_price_input").performTextInput("0")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("father_warn_zero_price").performScrollTo().assertIsDisplayed()

        // Still allows saving even with all soft warnings active!
        composeRule.onNodeWithTag("father_save_product_btn").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("father_add_product_dialog").fetchSemanticsNodes().size)
        val savedWithWarnings = storeViewModel.products.value.firstOrNull {
            it.createdBy == "father_warn" && it.name == existingName && it.stock == 1500.0
        }
        assertNotNull("Product must still save even with soft warnings active", savedWithWarnings)
        assertTrue(savedWithWarnings!!.needsReview)
        assertEquals(0.0, savedWithWarnings.sellPrice, 0.001)
        assertEquals(80.0, savedWithWarnings.buyPrice, 0.001)
    }

    @Test
    fun `owner sees needs review filter and badge in stock, edits and approves to clear flag`() {
        val (_, storeViewModel) = createViewModels("father_2")
        storeViewModel.setLanguage(AppLanguage.EN)
        storeViewModel.setSimpleMode(false)
        storeViewModel.setFatherModeForMember(memberId = "father_2", enabled = false)

        // Add one flagged product from Father mode path
        composeRule.runOnUiThread {
            storeViewModel.addNewProductWizard(
                name = "Father Flagged Tea",
                category = "General",
                unit = "pcs",
                buyPrice = 100.0,
                sellPrice = 130.0,
                stock = 12.0,
                needsReview = true
            )
        }
        composeRule.waitForIdle()

        val flagged = storeViewModel.products.value.first { it.name == "Father Flagged Tea" }
        val existingNormal = storeViewModel.products.value.first { it.id != flagged.id }
        assertTrue(flagged.needsReview)
        assertFalse(existingNormal.needsReview)

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    StockScreen(storeViewModel = storeViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        // Needs review filter chip is visible; existing visible item has no badge
        composeRule.onNodeWithTag("stock_filter_needs_review").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("stock_needs_review_badge_${existingNormal.id}").fetchSemanticsNodes().size
        )

        // Filter by Needs review -> only flagged item shown, with its Needs review badge
        composeRule.onNodeWithTag("stock_filter_needs_review").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stock_product_card_${flagged.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("stock_needs_review_badge_${flagged.id}", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText("Needs review", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("stock_product_card_${existingNormal.id}").fetchSemanticsNodes().size
        )

        // Open flagged product, edit name, and tap Approve
        composeRule.onNodeWithTag("stock_product_card_${flagged.id}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wizard_needs_review_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_approve_product_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_name_en").performTextClearance()
        composeRule.onNodeWithTag("wizard_name_en").performTextInput("Father Approved Tea")
        composeRule.onNodeWithTag("wizard_approve_product_btn").performClick()
        composeRule.waitForIdle()

        val updated = storeViewModel.products.value.first { it.id == flagged.id }
        assertEquals("Father Approved Tea", updated.name)
        assertFalse("Owner approval must clear needsReview flag", updated.needsReview)

        // Filter still active -> approved product no longer appears under Needs review
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("stock_product_card_${flagged.id}").fetchSemanticsNodes().size
        )
        assertTrue(storeViewModel.products.value.none { it.needsReview })
    }

    @Test
    fun `father edits own added product setting needsReview true again and new product is sellable immediately`() {
        val (authViewModel, storeViewModel) = createViewModels("father_3")

        // Father adds a product (simulated as Father saved)
        composeRule.runOnUiThread {
            storeViewModel.addNewProductWizard(
                name = "পিতার তেল",
                nameBn = "পিতার তেল",
                category = "General",
                unit = "litre",
                buyPrice = 150.0,
                sellPrice = 180.0,
                stock = 10.0,
                needsReview = true
            )
        }
        composeRule.waitForIdle()
        val prod = storeViewModel.products.value.first { it.name == "পিতার তেল" }

        // Owner approves it (clears flag)
        composeRule.runOnUiThread {
            storeViewModel.updateProductWizard(
                productId = prod.id,
                name = prod.name,
                nameBn = prod.nameBn,
                category = prod.category,
                unit = prod.unit,
                buyPrice = prod.buyPrice,
                sellPrice = prod.sellPrice,
                stock = prod.stock,
                needsReview = false
            )
        }
        composeRule.waitForIdle()
        assertFalse(storeViewModel.products.value.first { it.id == prod.id }.needsReview)

        // Enable Father mode
        storeViewModel.setFatherModeForMember(memberId = "father_3", enabled = true)

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides storeViewModel.currentLanguage.value) {
                    MainShopScreen(authViewModel = authViewModel, storeViewModel = storeViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        // Only products Father added appear in editable list; pre-existing products do not
        val otherProduct = storeViewModel.products.value.first { it.id != prod.id }
        composeRule.onNodeWithTag("father_added_product_${prod.id}").performScrollTo().assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("father_added_product_${otherProduct.id}").fetchSemanticsNodes().size
        )

        // Father opens and edits name, quantity, and prices
        composeRule.onNodeWithTag("father_added_product_${prod.id}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("father_product_name_input").performTextClearance()
        composeRule.onNodeWithTag("father_product_name_input").performTextInput("সয়াবিন তেল")
        composeRule.onNodeWithTag("father_product_qty_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("father_product_qty_input").performTextInput("18")
        composeRule.onNodeWithTag("father_product_sell_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("father_product_sell_price_input").performTextInput("195")
        composeRule.onNodeWithTag("father_product_buy_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("father_product_buy_price_input").performTextInput("165")
        composeRule.onNodeWithTag("father_save_product_btn").performClick()
        composeRule.waitForIdle()

        val reEdited = storeViewModel.products.value.first { it.id == prod.id }
        assertEquals("সয়াবিন তেল", reEdited.name)
        assertEquals(18.0, reEdited.stock, 0.001)
        assertEquals(195.0, reEdited.sellPrice, 0.001)
        assertEquals(165.0, reEdited.buyPrice, 0.001)
        assertTrue("Each Father edit must set needsReview = true again", reEdited.needsReview)

        // Move to Bin goes to Bin rather than hard-deleting
        composeRule.onNodeWithTag("father_added_product_${prod.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("father_product_bin_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("father_bin_confirm_btn").performClick()
        composeRule.waitForIdle()

        assertTrue(storeViewModel.products.value.none { it.id == prod.id })
        assertTrue(storeViewModel.productBin.value.any { it.id == prod.id })
    }

    @Test
    fun `newly added father product is sellable right away in SellScreen`() {
        val (_, storeViewModel) = createViewModels("father_sell")
        storeViewModel.setFatherModeForMember(memberId = "father_sell", enabled = true)

        composeRule.runOnUiThread {
            storeViewModel.addNewProductWizard(
                name = "খাতা ১০০ পাতা",
                nameBn = "খাতা ১০০ পাতা",
                category = "General",
                unit = "pcs",
                buyPrice = 40.0,
                sellPrice = 55.0,
                stock = 15.0,
                needsReview = true
            )
        }
        composeRule.waitForIdle()
        val added = storeViewModel.products.value.first { it.name == "খাতা ১০০ পাতা" }
        assertTrue(added.needsReview)

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.BN) {
                    SellScreen(storeViewModel = storeViewModel, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sell_search_input").performTextInput("খাতা")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("খাতা ১০০ পাতা").assertIsDisplayed()
        composeRule.onNodeWithText("খাতা ১০০ পাতা").performClick()
        composeRule.waitForIdle()

        // Checkout bar appears immediately because item was added to cart
        composeRule.onNodeWithTag("sell_floating_checkout_bar").assertIsDisplayed()
    }

    @Test
    fun `father mode still cannot see detailed screens`() {
        val (authViewModel, storeViewModel) = createViewModels("father_sec")
        storeViewModel.setFatherModeForMember(memberId = "father_sec", enabled = true)

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides storeViewModel.currentLanguage.value) {
                    MainShopScreen(authViewModel = authViewModel, storeViewModel = storeViewModel)
                }
            }
        }
        composeRule.waitForIdle()

        // Trying to open Detailed Stock, History, More, Settings, or Detailed Business screens redirects back to Father Home
        listOf(ShopNavTab.STOCK, ShopNavTab.HISTORY, ShopNavTab.MORE).forEach { tab ->
            composeRule.runOnUiThread { storeViewModel.navigateToTab(tab) }
            composeRule.waitForIdle()
            assertEquals(ShopNavTab.HOME, storeViewModel.currentTab.value)
            composeRule.onNodeWithTag("father_home_screen").assertIsDisplayed()
        }

        composeRule.runOnUiThread { storeViewModel.openSettings() }
        composeRule.waitForIdle()
        assertFalse(storeViewModel.showSettings.value)
        composeRule.onNodeWithTag("father_home_screen").assertIsDisplayed()

        listOf(
            BusinessNavDestination.FINANCIAL_REPORTS_HUB,
            BusinessNavDestination.STAFF_MANAGEMENT_SCREEN,
            BusinessNavDestination.CASH_BANK_SCREEN,
            BusinessNavDestination.DATA_HEALTH_CHECK
        ).forEach { dest ->
            composeRule.runOnUiThread { storeViewModel.openSubDestination(ShopNavTab.BUSINESS, dest) }
            composeRule.waitForIdle()
            assertEquals(ShopNavTab.HOME, storeViewModel.currentTab.value)
            composeRule.onNodeWithTag("father_home_screen").assertIsDisplayed()
        }
    }
}
