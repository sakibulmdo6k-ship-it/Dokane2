package com.example.domain

import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.util.AppLanguage

object SellQuickPickHelper {

    const val MAX_FAVORITE_PRODUCTS = 12
    const val MAX_RECENT_PRODUCTS = 10

    fun extractFavoriteProducts(
        products: List<Product>,
        limit: Int = MAX_FAVORITE_PRODUCTS
    ): List<Product> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<Product>(minOf(products.size, limit))
        for (product in products) {
            if (product.isFavorite) {
                result.add(product)
                if (result.size >= limit) break
            }
        }
        return result
    }

    fun canPinAnotherFavorite(
        products: List<Product>,
        targetProductId: String,
        limit: Int = MAX_FAVORITE_PRODUCTS
    ): Boolean {
        val currentTarget = products.firstOrNull { it.id == targetProductId }
        if (currentTarget?.isFavorite == true) return true
        val pinnedCount = products.count { it.isFavorite && it.id != targetProductId }
        return pinnedCount < limit
    }

    /**
     * Computes up to [limit] most recently sold distinct active products from existing [sales],
     * ordered newest-first, skipping deleted products (not present in [products]) and hiding any
     * product already shown in Favorites ([favoriteProductIds]).
     */
    fun computeRecentSoldProducts(
        products: List<Product>,
        sales: List<Sale>,
        favoriteProductIds: Set<String>,
        limit: Int = MAX_RECENT_PRODUCTS
    ): List<Product> {
        if (limit <= 0 || products.isEmpty() || sales.isEmpty()) return emptyList()
        val activeById = HashMap<String, Product>(products.size * 2)
        for (p in products) {
            if (p.id.isNotBlank()) {
                activeById[p.id] = p
            }
        }
        if (activeById.isEmpty()) return emptyList()

        val orderedSales = if (isAlreadySortedDescendingByTime(sales)) {
            sales
        } else {
            sales.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }

        val seen = HashSet<String>(limit * 2)
        val result = ArrayList<Product>(limit)
        for (sale in orderedSales) {
            for (item in sale.items) {
                val prodId = item.productId
                if (prodId.isBlank() || prodId in favoriteProductIds) continue
                val activeProduct = activeById[prodId] ?: continue
                if (seen.add(prodId)) {
                    result.add(activeProduct)
                    if (result.size >= limit) return result
                }
            }
        }
        return result
    }

    private fun isAlreadySortedDescendingByTime(sales: List<Sale>): Boolean {
        var prevTime = Long.MAX_VALUE
        for (sale in sales) {
            val t = sale.createdAt?.toDate()?.time ?: 0L
            if (t > prevTime) return false
            prevTime = t
        }
        return true
    }

    fun maxFavoritesMessage(lang: AppLanguage): String {
        return if (lang == AppLanguage.BN) {
            "সর্বোচ্চ ১২টি প্রিয় পণ্য। আগে একটি সরান"
        } else {
            "Max 12 favorites. Remove one first"
        }
    }

    fun emptyFavoritesHint(lang: AppLanguage): String {
        return if (lang == AppLanguage.BN) {
            "তারা চাপুন, পণ্য পিন হবে"
        } else {
            "Tap the star on a product to pin it"
        }
    }

    fun ownerOnlyPinMessage(lang: AppLanguage): String {
        return if (lang == AppLanguage.BN) {
            "শুধু দোকানের মালিক প্রিয় পণ্য পিন বা সরাতে পারবেন।"
        } else {
            "Only the store owner can pin or unpin favorites."
        }
    }
}
