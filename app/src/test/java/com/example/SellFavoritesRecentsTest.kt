package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.StoreBackupPayload
import com.example.data.repository.StoreRepository
import com.example.domain.SellQuickPickHelper
import com.example.domain.ShopType
import com.example.ui.screens.SellScreen
import com.example.ui.theme.StoreOSTheme
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.LocalAppLanguage
import com.example.util.NetworkMonitor
import com.example.util.StoreBackupCodec
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class SellFavoritesRecentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("storeos_stock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("storeos_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun createEmptyOwnerSetup(storeId: String = "demo_store", userId: String = "owner_fav"): Pair<StoreRepository, StoreViewModel> {
        val repo = StoreRepository(db = null, appContext = context).apply {
            configureEmptyStoreForTest(storeId = storeId, ownerId = userId, name = "Quick Pick Store", shopType = ShopType.GROCERY)
        }
        val vm = StoreViewModel(
            storeId = storeId,
            repository = repo,
            networkMonitor = NetworkMonitor(context),
            currentUserId = userId
        )
        return repo to vm
    }

    @Test
    fun quickPickHelper_computesMax12Favorites_andLast10NewestNonFavoriteActiveRecents() {
        val products = (1..15).map { idx ->
            Product(
                id = "p$idx",
                name = "Item $idx",
                sellPrice = idx * 10.0,
                stock = 20.0,
                isFavorite = idx <= 13 // Deliberately test capping at 12
            )
        }

        val favorites = SellQuickPickHelper.extractFavoriteProducts(products)
        assertEquals(12, favorites.size)
        assertFalse(SellQuickPickHelper.canPinAnotherFavorite(products.take(12), "p15"))
        assertTrue(SellQuickPickHelper.canPinAnotherFavorite(products.take(11), "p15"))

        // Sales intentionally unsorted, include deleted ("deleted_p"), favorite ("p1"), and duplicates ("p14")
        val cleanProducts = (1..15).map { idx ->
            Product(
                id = "p$idx",
                name = "Item $idx",
                sellPrice = idx * 10.0,
                stock = 20.0,
                isFavorite = idx == 1 // Only p1 is favorite
            )
        }
        val favIds = setOf("p1")
        val sales = listOf(
            Sale(
                id = "s_old",
                createdAt = Timestamp(Date(1_000L)),
                items = listOf(
                    SaleItem(productId = "p2", name = "Item 2"),
                    SaleItem(productId = "p3", name = "Item 3")
                )
            ),
            Sale(
                id = "s_newest",
                createdAt = Timestamp(Date(9_000L)),
                items = listOf(
                    SaleItem(productId = "deleted_p", name = "Deleted"),
                    SaleItem(productId = "p1", name = "Item 1"),
                    SaleItem(productId = "p15", name = "Item 15"),
                    SaleItem(productId = "p14", name = "Item 14")
                )
            ),
            Sale(
                id = "s_mid",
                createdAt = Timestamp(Date(5_000L)),
                items = (4..14).map { SaleItem(productId = "p$it", name = "Item $it") }
            )
        )

        val recents = SellQuickPickHelper.computeRecentSoldProducts(cleanProducts, sales, favIds)
        assertEquals(10, recents.size)
        // Newest first (p15, p14 from s_newest), then p4..p11 from s_mid (skipping p14 dup & deleted_p & p1 favorite)
        assertEquals(
            listOf("p15", "p14", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11"),
            recents.map { it.id }
        )
    }

    @Test
    fun repositoryAndViewModel_enforceMax12Favorites_ownerOnly_andBackupRoundTrip() {
        val (repo, ownerVm) = createEmptyOwnerSetup(storeId = "demo_store", userId = "owner_fav")
        try {
            val ids = runBlocking {
                (1..13).map { i ->
                    repo.addProduct(
                        "demo_store",
                        Product(name = "Prod $i", sellPrice = 20.0 + i, stock = 5.0, isFavorite = false)
                    ).productId
                }
            }
            ShadowLooper.idleMainLooper()

            runBlocking {
                for (i in 0 until 12) {
                    repo.setProductFavorite("demo_store", ids[i], true)
                }
            }
            ShadowLooper.idleMainLooper()
            assertEquals(12, ownerVm.products.value.count { it.isFavorite })

            // Pinning 13th is rejected with "Remove one first"
            var thirteenthError: String? = null
            val thirteenthProd = ownerVm.products.value.first { it.id == ids[12] }
            ownerVm.setProductFavorite(thirteenthProd, true) { thirteenthError = it }
            assertNotNull(thirteenthError)
            assertTrue(thirteenthError!!.contains("Remove one first", ignoreCase = true))

            // Updating a favorite product preserves isFavorite
            val firstProd = ownerVm.products.value.first { it.id == ids[0] }
            ownerVm.updateProductWizard(
                productId = firstProd.id,
                name = "Prod 1 Edited",
                sellPrice = 25.0,
                stock = 5.0
            )
            ShadowLooper.idleMainLooper()
            assertTrue(ownerVm.products.value.first { it.id == ids[0] }.isFavorite)

            // Backup codec preserves isFavorite
            val payload = StoreBackupPayload(source = StoreBackupPayload.SOURCE_DEMO, products = ownerVm.products.value)
            val decoded = StoreBackupCodec.decodeAndValidateJson(StoreBackupCodec.encodeToJson(payload), AppLanguage.EN)
            assertEquals(12, decoded.products.count { it.isFavorite })

            // Father mode cannot pin/unpin
            ownerVm.setFatherModeForMember("owner_fav", true)
            ShadowLooper.idleMainLooper()
            assertFalse(ownerVm.canPinFavorites.value)
            var fatherErr: String? = null
            ownerVm.setProductFavorite(ownerVm.products.value.first { it.id == ids[0] }, false) { fatherErr = it }
            assertNotNull(fatherErr)
            assertTrue(ownerVm.products.value.first { it.id == ids[0] }.isFavorite)
            ownerVm.setFatherModeForMember("owner_fav", false)
            ShadowLooper.idleMainLooper()

            // Non-owner staff cannot pin/unpin
            val staffRepo = StoreRepository(db = null, appContext = context).apply {
                configureEmptyStoreForTest(storeId = "real_shop", ownerId = "owner_1", name = "Shop", shopType = ShopType.GROCERY)
            }
            val pId = runBlocking {
                staffRepo.addProduct("real_shop", Product(name = "Milk", sellPrice = 80.0, stock = 10.0, isFavorite = true)).productId
            }
            val staffVm = StoreViewModel(
                storeId = "real_shop",
                repository = staffRepo,
                networkMonitor = NetworkMonitor(context),
                currentUserId = "staff_cashier"
            )
            try {
                ShadowLooper.idleMainLooper()
                assertFalse(staffVm.canPinFavorites.value)
                var staffErr: String? = null
                staffVm.setProductFavorite(staffVm.products.value.first { it.id == pId }, false) { staffErr = it }
                assertNotNull(staffErr)
                assertTrue(staffVm.products.value.first { it.id == pId }.isFavorite)
            } finally {
                staffVm.dispose()
            }
        } finally {
            ownerVm.dispose()
        }
    }

    @Test
    fun sellScreen_favoritesAndRecents_pinUnpinTapAddOutOfStockAndSearchHide() {
        val (repo, vm) = createEmptyOwnerSetup("demo_store", "owner_ui")
        val riceId: String
        val teaId: String
        val zeroId: String
        val deletedId: String

        runBlocking {
            riceId = repo.addProduct("demo_store", Product(name = "Miniket Rice", sellPrice = 75.0, stock = 20.0)).productId
            teaId = repo.addProduct("demo_store", Product(name = "Ispahani Tea", sellPrice = 220.0, stock = 12.0)).productId
            zeroId = repo.addProduct("demo_store", Product(name = "Zero Cola", sellPrice = 30.0, stock = 0.0, lowStockLimit = 2.0, isFavorite = false)).productId
            deletedId = repo.addProduct("demo_store", Product(name = "Old Candy", sellPrice = 5.0, stock = 10.0)).productId

            // Create a sale containing tea, zeroCola, and deleted Candy
            repo.executeSaleCheckout(
                storeId = "demo_store",
                items = listOf(
                    SaleItem(productId = deletedId, name = "Old Candy", qty = 1.0, stockQty = 1.0, sellPrice = 5.0, lineTotal = 5.0),
                    SaleItem(productId = teaId, name = "Ispahani Tea", qty = 1.0, stockQty = 1.0, sellPrice = 220.0, lineTotal = 220.0),
                    SaleItem(productId = zeroId, name = "Zero Cola", qty = 1.0, stockQty = 1.0, sellPrice = 30.0, lineTotal = 30.0)
                ),
                allowOutOfStockProductIds = setOf(zeroId)
            )
            val deletedProd = repo.observeProducts("demo_store").first()
            val candy = deletedProd.first { it.id == deletedId }
            repo.moveProductToBin("demo_store", candy, "owner_ui")
        }

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                    Box(modifier = Modifier.size(width = 412.dp, height = 850.dp)) {
                        SellScreen(storeViewModel = vm, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        try {
            // 1) No favorites yet -> shows 1-line hint instead of empty row
            composeRule.onNodeWithTag("sell_favorites_empty_hint").assertIsDisplayed()
            composeRule.onNodeWithText("Tap the star on a product to pin it").assertIsDisplayed()

            // 2) Recents visible when search is empty; skips deleted Old Candy, shows Ispahani Tea & Zero Cola
            composeRule.onNodeWithTag("sell_recents_header").assertIsDisplayed()
            composeRule.onNodeWithTag("sell_recent_chip_$teaId").assertIsDisplayed()
            composeRule.onNodeWithTag("sell_recent_chip_$zeroId").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithTag("sell_recent_chip_$deletedId").fetchSemanticsNodes().size)

            // 3) Owner pins Ispahani Tea via star icon -> appears in Favorites and disappears from Recents
            composeRule.onNodeWithTag("sell_product_star_$teaId").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_favorites_row").assertIsDisplayed()
            composeRule.onNodeWithTag("sell_favorite_chip_$teaId").assertIsDisplayed()
            assertEquals(0, composeRule.onAllNodesWithTag("sell_recent_chip_$teaId").fetchSemanticsNodes().size)

            // 4) Tap favorite chip twice -> adds 1 then +1 more (x2)
            composeRule.onNodeWithTag("sell_favorite_chip_$teaId").performClick()
            composeRule.waitForIdle()
            assertTrue(composeRule.onAllNodesWithText("×1").fetchSemanticsNodes().isNotEmpty())

            composeRule.onNodeWithTag("sell_favorite_chip_$teaId").performClick()
            composeRule.waitForIdle()
            assertTrue(composeRule.onAllNodesWithTag("sell_favorite_chip_$teaId").fetchSemanticsNodes().isNotEmpty())
            assertTrue(composeRule.onAllNodesWithText("×2").fetchSemanticsNodes().isNotEmpty())

            // 5) Tap out-of-stock recent chip (Zero Cola) -> still adds + shows amber Low/zero stock note
            composeRule.onNodeWithTag("sell_recent_chip_$zeroId").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_low_zero_stock_banner").assertIsDisplayed()

            // 6) Typing in search box hides Recents row
            composeRule.onNodeWithTag("sell_search_input").performTextInput("Rice")
            composeRule.waitForIdle()
            assertEquals(0, composeRule.onAllNodesWithTag("sell_recents_row").fetchSemanticsNodes().size)
            composeRule.onNodeWithTag("sell_search_input").performTextClearance()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_recents_row").assertIsDisplayed()

            // 7) Long-press favorite chip unpins Ispahani Tea -> returns to empty hint & appears back in Recents
            composeRule.onNodeWithTag("sell_favorite_chip_$teaId").performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_favorites_empty_hint").assertIsDisplayed()
            composeRule.onNodeWithTag("sell_recent_chip_$teaId").assertIsDisplayed()
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun sellScreen_fatherMode_canSeeAndTapFavorites_cannotPinOrUnpin() {
        val (repo, vm) = createEmptyOwnerSetup("demo_store", "father_fav")
        val milkId: String
        val breadId: String
        runBlocking {
            milkId = repo.addProduct("demo_store", Product(name = "দুধ ১ লিটার", nameBn = "দুধ ১ লিটার", sellPrice = 95.0, stock = 10.0, isFavorite = true)).productId
            breadId = repo.addProduct("demo_store", Product(name = "পাউরুটি", nameBn = "পাউরুটি", sellPrice = 45.0, stock = 10.0, isFavorite = false)).productId
        }
        vm.setFatherModeForMember("father_fav", true)
        ShadowLooper.idleMainLooper()

        composeRule.setContent {
            StoreOSTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.BN) {
                    Box(modifier = Modifier.size(width = 412.dp, height = 850.dp)) {
                        SellScreen(storeViewModel = vm, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        try {
            // Favorite chip visible and tappable
            composeRule.onNodeWithTag("sell_favorite_chip_$milkId").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_floating_checkout_bar").assertIsDisplayed()

            // No star buttons shown in Father mode
            assertEquals(0, composeRule.onAllNodesWithTag("sell_product_star_$milkId").fetchSemanticsNodes().size)
            assertEquals(0, composeRule.onAllNodesWithTag("sell_product_star_$breadId").fetchSemanticsNodes().size)

            // Long-pressing favorite chip does NOT unpin in Father mode
            composeRule.onNodeWithTag("sell_favorite_chip_$milkId").performTouchInput { longClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sell_favorite_chip_$milkId").assertIsDisplayed()
            assertTrue(vm.products.value.first { it.id == milkId }.isFavorite)
        } finally {
            vm.dispose()
        }
    }
}
