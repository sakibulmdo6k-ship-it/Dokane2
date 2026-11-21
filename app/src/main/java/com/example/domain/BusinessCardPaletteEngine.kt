package com.example.domain

import com.example.data.model.BusinessCardPalette
import com.example.data.model.PaletteColorRoles
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure Kotlin WCAG 2.1 luminance & contrast engine for Business Card palettes.
 *
 * Role assignment:
 * - Darkest color = Card background
 * - Lightest color = Main text
 * - Other two colors = Name/shop highlight and lines/icons
 * - Enforces text contrast >= 4.5:1; if highlight/text falls below 4.5:1,
 *   falls back to the lightest color (or highest-contrast endpoint in Light card mode).
 * - Light card mode swaps card background and main text while preserving >= 4.5:1 text readability.
 */
object BusinessCardPaletteEngine {

    const val MIN_TEXT_CONTRAST = 4.5
    private const val ARGB_WHITE = 0xFFFFFFFF.toInt()
    private const val ARGB_BLACK = 0xFF000000.toInt()

    fun parseHexColor(hex: String): Int {
        val cleaned = hex.trim().removePrefix("#")
        val value = cleaned.toLong(16)
        return when (cleaned.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> ARGB_BLACK
        }
    }

    fun relativeLuminance(argb: Int): Double {
        val r = linearize(((argb shr 16) and 0xFF) / 255.0)
        val g = linearize(((argb shr 8) and 0xFF) / 255.0)
        val b = linearize((argb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
        val l1 = relativeLuminance(foregroundArgb)
        val l2 = relativeLuminance(backgroundArgb)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun resolveRoles(
        palette: BusinessCardPalette,
        isLightCard: Boolean = false
    ): PaletteColorRoles {
        val rawDark = parseHexColor(palette.darkHex)
        val rawMid = parseHexColor(palette.midHex)
        val rawLight = parseHexColor(palette.lightHex)
        val rawAccent = parseHexColor(palette.accentHex)

        val argbs = listOf(rawDark, rawMid, rawLight, rawAccent)
        val sortedByLum = argbs.sortedBy(::relativeLuminance)
        val darkest = sortedByLum.first()
        val lightest = sortedByLum.last()
        val middle = sortedByLum.subList(1, 3)

        val baseBackground = if (isLightCard) lightest else darkest
        val preferredMain = if (isLightCard) darkest else lightest
        val safeMainText = resolveContrastTextColor(
            preferredArgb = preferredMain,
            backgroundArgb = baseBackground,
            darkestArgb = darkest,
            lightestArgb = lightest
        )

        // Mid & accent colors are for shapes, lines, and large decoration only unless they pass >= 4.5:1
        val midGraphics = if (contrastRatio(rawMid, baseBackground) >= 1.25) {
            rawMid
        } else {
            middle.maxByOrNull { contrastRatio(it, baseBackground) } ?: rawMid
        }
        val accentGraphics = if (contrastRatio(rawAccent, baseBackground) >= 1.25) {
            rawAccent
        } else {
            middle.firstOrNull { it != midGraphics } ?: rawAccent
        }

        // Only allow a mid/accent color for text if it strictly meets >= 4.5:1; otherwise use light/dark endpoint
        val textCandidates = listOf(rawAccent, rawMid, middle[1], middle[0])
            .distinct()
            .filter { it != baseBackground && contrastRatio(it, baseBackground) >= MIN_TEXT_CONTRAST }
            .sortedByDescending { contrastRatio(it, baseBackground) }

        val safeHighlight = textCandidates.firstOrNull { it != darkest && it != lightest }
            ?: safeMainText

        // Solid panel/band color visually distinct from card background (>= 1.45 contrast where possible)
        val splitPanelColor = listOf(rawMid, rawAccent, middle[0], middle[1], if (isLightCard) darkest else lightest)
            .firstOrNull { candidate ->
                candidate != baseBackground && contrastRatio(candidate, baseBackground) >= 1.45
            } ?: if (isLightCard) darkest else midGraphics

        return PaletteColorRoles(
            backgroundArgb = baseBackground,
            mainTextArgb = safeMainText,
            highlightTextArgb = safeHighlight,
            accentGraphicsArgb = accentGraphics,
            midGraphicsArgb = midGraphics,
            splitPanelArgb = splitPanelColor,
            darkestArgb = darkest,
            lightestArgb = lightest,
            isLightCard = isLightCard,
            mainContrastRatio = contrastRatio(safeMainText, baseBackground),
            highlightContrastRatio = contrastRatio(safeHighlight, baseBackground)
        )
    }

    /**
     * Computes contrast against backgroundArgb. If below 4.5:1, automatically switches text
     * to the palette's light or dark color (whichever passes), falling back to White/Black if needed.
     */
    fun resolveContrastTextColor(
        preferredArgb: Int,
        backgroundArgb: Int,
        darkestArgb: Int,
        lightestArgb: Int
    ): Int {
        if (contrastRatio(preferredArgb, backgroundArgb) >= MIN_TEXT_CONTRAST) {
            return preferredArgb
        }
        val lightContrast = contrastRatio(lightestArgb, backgroundArgb)
        val darkContrast = contrastRatio(darkestArgb, backgroundArgb)
        if (lightContrast >= MIN_TEXT_CONTRAST && darkContrast >= MIN_TEXT_CONTRAST) {
            return if (lightContrast >= darkContrast) lightestArgb else darkestArgb
        }
        if (lightContrast >= MIN_TEXT_CONTRAST) return lightestArgb
        if (darkContrast >= MIN_TEXT_CONTRAST) return darkestArgb

        val whiteContrast = contrastRatio(ARGB_WHITE, backgroundArgb)
        val blackContrast = contrastRatio(ARGB_BLACK, backgroundArgb)
        return if (whiteContrast >= blackContrast) ARGB_WHITE else ARGB_BLACK
    }

    fun resolveContrastTextColor(
        preferredArgb: Int,
        backgroundArgb: Int,
        roles: PaletteColorRoles
    ): Int = resolveContrastTextColor(
        preferredArgb = preferredArgb,
        backgroundArgb = backgroundArgb,
        darkestArgb = roles.darkestArgb,
        lightestArgb = roles.lightestArgb
    )

    /**
     * Composites a foreground color with alpha over backgroundArgb and verifies whether
     * the resulting pixels still meet >= 4.5:1. If dimming drops contrast below 4.5:1,
     * returns full-opacity safe color.
     */
    fun safeSecondaryAlpha(
        textArgb: Int,
        backgroundArgb: Int,
        desiredAlpha: Float
    ): Float {
        val clamped = desiredAlpha.coerceIn(0f, 1f)
        val blended = compositeOver(textArgb, backgroundArgb, clamped)
        return if (contrastRatio(blended, backgroundArgb) >= MIN_TEXT_CONTRAST) clamped else 1f
    }

    fun applySafeSecondaryArgb(
        textArgb: Int,
        backgroundArgb: Int,
        desiredAlpha: Float,
        roles: PaletteColorRoles
    ): Int {
        val guaranteedText = resolveContrastTextColor(textArgb, backgroundArgb, roles)
        val alpha = safeSecondaryAlpha(guaranteedText, backgroundArgb, desiredAlpha)
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (guaranteedText and 0x00FFFFFF)
    }

    private fun compositeOver(fgArgb: Int, bgArgb: Int, alpha: Float): Int {
        val inv = 1f - alpha
        val r = (((fgArgb shr 16) and 0xFF) * alpha + ((bgArgb shr 16) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val g = (((fgArgb shr 8) and 0xFF) * alpha + ((bgArgb shr 8) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val b = ((fgArgb and 0xFF) * alpha + (bgArgb and 0xFF) * inv).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * When a photo background is active with dark overlay, text renders against the darkened image,
     * so we enforce readable light text using the palette's lightest color (or white if needed).
     */
    fun effectiveTextRolesForCanvas(
        roles: PaletteColorRoles,
        hasBackgroundPhoto: Boolean,
        overlayAlpha: Float = 0.45f
    ): Pair<Int, Int> {
        if (!hasBackgroundPhoto) {
            val safeMain = resolveContrastTextColor(roles.mainTextArgb, roles.backgroundArgb, roles)
            val safeHighlight = resolveContrastTextColor(roles.highlightTextArgb, roles.backgroundArgb, roles)
            return safeMain to safeHighlight
        }
        val darkenedBg = compositeOver(ARGB_BLACK, roles.backgroundArgb, overlayAlpha.coerceIn(0.35f, 0.85f))
        val effectiveBg = if (relativeLuminance(darkenedBg) > 0.12) ARGB_BLACK else darkenedBg
        val lightText = if (contrastRatio(roles.lightestArgb, effectiveBg) >= MIN_TEXT_CONTRAST) {
            roles.lightestArgb
        } else {
            ARGB_WHITE
        }
        val hl = if (contrastRatio(roles.highlightTextArgb, effectiveBg) >= MIN_TEXT_CONTRAST &&
            relativeLuminance(roles.highlightTextArgb) >= 0.25
        ) {
            roles.highlightTextArgb
        } else {
            lightText
        }
        return lightText to hl
    }

    /**
     * Resolves high-contrast (>= 4.5:1) text color for Split Panel / Bold Band surfaces,
     * automatically switching to the palette's light or dark color (whichever passes).
     */
    fun resolveSplitPanelTextColor(
        panelBgArgb: Int,
        roles: PaletteColorRoles
    ): Int = resolveContrastTextColor(
        preferredArgb = roles.lightestArgb,
        backgroundArgb = panelBgArgb,
        roles = roles
    )
}
