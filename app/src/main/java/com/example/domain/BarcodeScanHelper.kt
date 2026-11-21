package com.example.domain

import com.example.data.model.Product
import com.example.util.AppLanguage

enum class BarcodeStockWarning {
    NONE,
    LOW_STOCK,
    ZERO_OR_OUT_OF_STOCK
}

object BarcodeScanHelper {

    fun normalizeBarcode(raw: String): String = raw.trim()

    fun findProductByBarcode(rawBarcode: String, products: List<Product>): Product? {
        val clean = normalizeBarcode(rawBarcode)
        if (clean.isBlank()) return null
        return products.firstOrNull { product ->
            val stored = normalizeBarcode(product.barcode)
            stored.isNotBlank() && stored.equals(clean, ignoreCase = true)
        }
    }

    fun findDuplicateBarcodeProduct(
        rawBarcode: String,
        products: List<Product>,
        excludeProductId: String? = null
    ): Product? {
        val clean = normalizeBarcode(rawBarcode)
        if (clean.isBlank()) return null
        return products.firstOrNull { product ->
            product.id != excludeProductId &&
                normalizeBarcode(product.barcode).let { it.isNotBlank() && it.equals(clean, ignoreCase = true) }
        }
    }

    fun duplicateBarcodeMessage(
        rawBarcode: String,
        conflictingProduct: Product,
        lang: AppLanguage
    ): String {
        val clean = normalizeBarcode(rawBarcode)
        val productName = if (lang == AppLanguage.BN && conflictingProduct.nameBn.isNotBlank()) {
            conflictingProduct.nameBn
        } else {
            conflictingProduct.name
        }
        return if (lang == AppLanguage.BN) {
            "বারকোড '$clean' ইতিমধ্যেই '$productName' পণ্যে দেওয়া আছে"
        } else {
            "Barcode '$clean' is already used by '$productName'"
        }
    }

    fun evaluateScannedStockWarning(
        product: Product,
        nextCartStockQty: Double
    ): BarcodeStockWarning {
        if (product.stock <= 0.0 || nextCartStockQty > product.stock + 0.0001) {
            return BarcodeStockWarning.ZERO_OR_OUT_OF_STOCK
        }
        if (product.stock <= product.lowStockLimit) {
            return BarcodeStockWarning.LOW_STOCK
        }
        return BarcodeStockWarning.NONE
    }

    fun filterProductsForBarcodeLink(
        products: List<Product>,
        query: String
    ): List<Product> {
        val q = query.trim()
        val matching = if (q.isBlank()) {
            products
        } else {
            products.filter { prod ->
                prod.name.contains(q, ignoreCase = true) ||
                    prod.nameBn.contains(q, ignoreCase = true) ||
                    prod.category.contains(q, ignoreCase = true) ||
                    prod.barcode.contains(q, ignoreCase = true)
            }
        }
        return matching.sortedWith(
            compareBy<Product> { it.barcode.isNotBlank() }
                .thenBy { it.name.lowercase() }
        )
    }
}
