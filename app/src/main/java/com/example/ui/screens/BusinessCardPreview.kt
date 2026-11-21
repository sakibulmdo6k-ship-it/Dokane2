package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessCardDraft
import com.example.data.model.BusinessCardLayout
import com.example.data.model.BusinessCardPalettes
import com.example.data.model.BusinessCardTextAlign
import com.example.data.model.PaletteColorRoles
import com.example.domain.BusinessCardPaletteEngine
import com.example.util.AppLanguage
import com.example.util.BusinessCardLocalStore
import com.example.util.BusinessCardRenderer

/**
 * Live Jetpack Compose preview of the front-only 3.5 x 2 inch Business Card.
 * - Supports 6 designs: Classic, Centered, Split Panel, Bold Band, Minimal Line, Corner Accent.
 * - Enforces >= 4.5:1 WCAG contrast on every text element against its local background.
 * - Hides any blank field (never renders placeholder/sample text on the card).
 */
@Composable
fun BusinessCardCanvasPreview(
    draft: BusinessCardDraft,
    currentLanguage: AppLanguage,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false
) {
    val palette = remember(draft.paletteId) { BusinessCardPalettes.byId(draft.paletteId) }
    val roles = remember(palette, draft.isLightCard) {
        BusinessCardPaletteEngine.resolveRoles(palette, draft.isLightCard)
    }

    val bgBitmap: Bitmap? = remember(draft.backgroundImageUri, isThumbnail) {
        BusinessCardLocalStore.decodeLocalBitmap(
            draft.backgroundImageUri,
            maxDimensionPx = if (isThumbnail) 360 else 1050
        )
    }
    val logoBitmap: Bitmap? = remember(draft.logoUri, isThumbnail) {
        BusinessCardLocalStore.decodeLocalBitmap(
            draft.logoUri,
            maxDimensionPx = if (isThumbnail) 160 else 400
        )
    }

    val hasBgPhoto = bgBitmap != null
    val effectiveBgArgb = if (hasBgPhoto) android.graphics.Color.BLACK else roles.backgroundArgb
    val (mainTextArgb, highlightTextArgb) = remember(roles, hasBgPhoto, draft.backgroundOverlayAlpha) {
        BusinessCardPaletteEngine.effectiveTextRolesForCanvas(
            roles = roles,
            hasBackgroundPhoto = hasBgPhoto,
            overlayAlpha = draft.backgroundOverlayAlpha
        )
    }
    val secondaryMainColor = remember(mainTextArgb, effectiveBgArgb, roles) {
        Color(BusinessCardPaletteEngine.applySafeSecondaryArgb(mainTextArgb, effectiveBgArgb, 0.86f, roles))
    }

    val bgColor = Color(roles.backgroundArgb)
    val mainColor = Color(mainTextArgb)
    val highlightColor = Color(highlightTextArgb)
    val accentColor = Color(roles.accentGraphicsArgb)
    val midColor = Color(roles.midGraphicsArgb)

    val shopText = draft.shopName.trim()
    val ownerText = draft.ownerName.trim()
    val titleText = draft.title.trim()
    val taglineText = draft.tagline.trim()
    val phoneText = draft.phone.trim()
    val emailText = draft.email.trim()
    val addressText = draft.address.trim()

    val cardRadius = if (isThumbnail) 8.dp else 14.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BusinessCardLocalStore.CARD_ASPECT_RATIO)
            .then(if (isThumbnail) Modifier else Modifier.shadow(12.dp, RoundedCornerShape(cardRadius)))
            .then(if (isThumbnail) Modifier else Modifier.testTag("bc_card_preview")),
        shape = RoundedCornerShape(cardRadius),
        color = bgColor,
        border = if (isThumbnail) null else BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            val widthRatio = (maxWidth.value / 336f).coerceIn(0.28f, 1.45f)
            val textScale = widthRatio * draft.fontSize.scale

            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = draft.backgroundOverlayAlpha.coerceIn(0f, 0.85f)))
                )
            }

            when (draft.layout) {
                BusinessCardLayout.CLASSIC -> CardFrontClassicPreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
                BusinessCardLayout.CENTERED -> CardFrontCenteredPreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
                BusinessCardLayout.SPLIT_PANEL -> CardFrontSplitPanelPreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    roles = roles,
                    hasBgPhoto = hasBgPhoto,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
                BusinessCardLayout.BOLD_BAND -> CardFrontBoldBandPreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    roles = roles,
                    hasBgPhoto = hasBgPhoto,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
                BusinessCardLayout.MINIMAL_LINE -> CardFrontMinimalLinePreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
                BusinessCardLayout.CORNER_ACCENT -> CardFrontCornerAccentPreview(
                    shopText = shopText,
                    ownerText = ownerText,
                    titleText = titleText,
                    taglineText = taglineText,
                    phoneText = phoneText,
                    emailText = emailText,
                    addressText = addressText,
                    logoBitmap = logoBitmap,
                    mainColor = mainColor,
                    secondaryColor = secondaryMainColor,
                    highlightColor = highlightColor,
                    accentColor = accentColor,
                    midColor = midColor,
                    draft = draft,
                    widthRatio = widthRatio,
                    textScale = textScale,
                    isThumbnail = isThumbnail
                )
            }
        }
    }
}

private fun Modifier.optionalTag(isThumbnail: Boolean, tag: String): Modifier =
    if (isThumbnail) this else this.testTag(tag)

@Composable
private fun CardFrontClassicPreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Start)
    val colAlign = toHorizontalAlignment(resolvedAlign)
    val hasHeader = shopText.isNotBlank() || taglineText.isNotBlank() || logoBitmap != null
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val hasContacts = phoneText.isNotBlank() || emailText.isNotBlank() || addressText.isNotBlank()

    Row(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width((6f * widthRatio).dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = (15f * widthRatio).dp, vertical = (13f * widthRatio).dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (hasHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = colAlign
                    ) {
                        if (shopText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                                color = highlightColor,
                                fontSize = (15f * textScale).sp,
                                lineHeight = (18f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = resolvedAlign,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_shop_name")
                            )
                        }
                        if (taglineText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                                color = secondaryColor,
                                fontSize = (8.8f * textScale).sp,
                                lineHeight = (11f * textScale).sp,
                                textAlign = resolvedAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (logoBitmap != null) {
                        Spacer(modifier = Modifier.width((8f * widthRatio).dp))
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = shopText.ifBlank { "Logo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size((34f * widthRatio).dp)
                                .clip(RoundedCornerShape((6f * widthRatio).dp))
                                .optionalTag(isThumbnail, "bc_preview_logo")
                        )
                    }
                }
            }

            if (hasHeader && (hasOwner || hasContacts)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((1f * widthRatio).coerceAtLeast(0.7f).dp)
                        .background(accentColor.copy(alpha = 0.72f))
                )
            }

            if (hasOwner) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (ownerText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                            color = mainColor,
                            fontSize = (11.8f * textScale).sp,
                            lineHeight = (14f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                        )
                    }
                    if (titleText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (9f * textScale).sp,
                            lineHeight = (11.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasContacts) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign,
                    verticalArrangement = Arrangement.spacedBy((1.5f * widthRatio).dp)
                ) {
                    if (phoneText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(phoneText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.8f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                        )
                    }
                    if (emailText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(emailText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_email")
                        )
                    }
                    if (addressText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrontCenteredPreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Center)
    val colAlign = toHorizontalAlignment(resolvedAlign)
    val hasHeader = shopText.isNotBlank() || taglineText.isNotBlank() || logoBitmap != null
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val contactLine = listOfNotNull(
        phoneText.takeIf { it.isNotBlank() },
        emailText.takeIf { it.isNotBlank() }
    ).joinToString("  •  ")
    val hasContacts = contactLine.isNotBlank() || addressText.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding((8f * widthRatio).dp)
            .border(
                (1f * widthRatio).coerceAtLeast(0.7f).dp,
                accentColor.copy(alpha = 0.55f),
                RoundedCornerShape((8f * widthRatio).dp)
            )
            .padding(horizontal = (14f * widthRatio).dp, vertical = (9f * widthRatio).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = colAlign,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (hasHeader) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = shopText.ifBlank { "Logo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size((26f * widthRatio).dp)
                                .clip(RoundedCornerShape((6f * widthRatio).dp))
                                .optionalTag(isThumbnail, "bc_preview_logo")
                        )
                        Spacer(modifier = Modifier.height((3f * widthRatio).dp))
                    }
                    if (shopText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (14.5f * textScale).sp,
                            lineHeight = (17f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_shop_name")
                        )
                    }
                    if (taglineText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasHeader && (hasOwner || hasContacts)) {
                Box(
                    modifier = Modifier
                        .width((60f * widthRatio).dp)
                        .height((1.5f * widthRatio).coerceAtLeast(0.8f).dp)
                        .background(accentColor)
                )
            }

            if (hasOwner) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (ownerText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                            color = mainColor,
                            fontSize = (11.2f * textScale).sp,
                            lineHeight = (13.5f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                        )
                    }
                    if (titleText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (8.8f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasContacts) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign,
                    verticalArrangement = Arrangement.spacedBy((1f * widthRatio).dp)
                ) {
                    if (contactLine.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(contactLine, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.4f * textScale).sp,
                            lineHeight = (10.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                        )
                    }
                    if (addressText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.4f * textScale).sp,
                            lineHeight = (10.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrontSplitPanelPreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    roles: PaletteColorRoles,
    hasBgPhoto: Boolean,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val panelBgArgb = if (hasBgPhoto) android.graphics.Color.BLACK else roles.splitPanelArgb
    val panelBg = if (hasBgPhoto) Color.Black.copy(alpha = 0.68f) else Color(roles.splitPanelArgb)
    val panelTextArgb = BusinessCardPaletteEngine.resolveSplitPanelTextColor(panelBgArgb, roles)
    val panelTextColor = Color(panelTextArgb)
    val panelSecondaryColor = Color(
        BusinessCardPaletteEngine.applySafeSecondaryArgb(panelTextArgb, panelBgArgb, 0.86f, roles)
    )

    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Start)
    val colAlign = toHorizontalAlignment(resolvedAlign)
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val hasContacts = phoneText.isNotBlank() || emailText.isNotBlank() || addressText.isNotBlank()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.37f)
                .fillMaxHeight()
                .background(panelBg)
                .padding(horizontal = (10f * widthRatio).dp, vertical = (12f * widthRatio).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = shopText.ifBlank { "Logo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size((34f * widthRatio).dp)
                        .clip(RoundedCornerShape((7f * widthRatio).dp))
                        .optionalTag(isThumbnail, "bc_preview_logo")
                )
                if (shopText.isNotBlank() || taglineText.isNotBlank()) {
                    Spacer(modifier = Modifier.height((5f * widthRatio).dp))
                }
            }
            if (shopText.isNotBlank()) {
                Text(
                    text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                    color = panelTextColor,
                    fontSize = (12f * textScale).sp,
                    lineHeight = (14.5f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.optionalTag(isThumbnail, "bc_preview_shop_name")
                )
            }
            if (taglineText.isNotBlank()) {
                Spacer(modifier = Modifier.height((3f * widthRatio).dp))
                Text(
                    text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                    color = panelSecondaryColor,
                    fontSize = (8f * textScale).sp,
                    lineHeight = (10f * textScale).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.63f)
                .fillMaxHeight()
                .padding(horizontal = (13f * widthRatio).dp, vertical = (13f * widthRatio).dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = colAlign
        ) {
            if (hasOwner) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (ownerText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (12.5f * textScale).sp,
                            lineHeight = (15f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                        )
                    }
                    if (titleText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (9f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasOwner && hasContacts) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((1f * widthRatio).coerceAtLeast(0.7f).dp)
                        .background(accentColor.copy(alpha = 0.72f))
                )
            }

            if (hasContacts) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign,
                    verticalArrangement = Arrangement.spacedBy((2f * widthRatio).dp)
                ) {
                    if (phoneText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(phoneText, draft.fontPair),
                            color = mainColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                        )
                    }
                    if (emailText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(emailText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.4f * textScale).sp,
                            lineHeight = (10.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_email")
                        )
                    }
                    if (addressText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.4f * textScale).sp,
                            lineHeight = (10.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrontBoldBandPreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    roles: PaletteColorRoles,
    hasBgPhoto: Boolean,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val bandBgArgb = if (hasBgPhoto) android.graphics.Color.BLACK else roles.splitPanelArgb
    val bandBg = if (hasBgPhoto) Color.Black.copy(alpha = 0.72f) else Color(roles.splitPanelArgb)
    val bandTextArgb = BusinessCardPaletteEngine.resolveSplitPanelTextColor(bandBgArgb, roles)
    val bandTextColor = Color(bandTextArgb)
    val bandSecondaryColor = Color(
        BusinessCardPaletteEngine.applySafeSecondaryArgb(bandTextArgb, bandBgArgb, 0.86f, roles)
    )

    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Start)
    val colAlign = toHorizontalAlignment(resolvedAlign)
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val hasContacts = phoneText.isNotBlank() || emailText.isNotBlank() || addressText.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        // Bold top header band
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.39f)
                .background(bandBg)
                .padding(horizontal = (16f * widthRatio).dp, vertical = (9f * widthRatio).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = colAlign,
                verticalArrangement = Arrangement.Center
            ) {
                if (shopText.isNotBlank()) {
                    Text(
                        text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                        color = bandTextColor,
                        fontSize = (15f * textScale).sp,
                        lineHeight = (18f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = resolvedAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_shop_name")
                    )
                }
                if (taglineText.isNotBlank()) {
                    Text(
                        text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                        color = bandSecondaryColor,
                        fontSize = (8.6f * textScale).sp,
                        lineHeight = (11f * textScale).sp,
                        textAlign = resolvedAlign,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (logoBitmap != null) {
                Spacer(modifier = Modifier.width((8f * widthRatio).dp))
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = shopText.ifBlank { "Logo" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size((34f * widthRatio).dp)
                        .clip(RoundedCornerShape((7f * widthRatio).dp))
                        .optionalTag(isThumbnail, "bc_preview_logo")
                )
            }
        }

        // Accent ribbon edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((2.5f * widthRatio).coerceAtLeast(1.2f).dp)
                .background(accentColor)
        )

        // Lower body section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.61f)
                .padding(horizontal = (16f * widthRatio).dp, vertical = (10f * widthRatio).dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = colAlign
        ) {
            if (hasOwner) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (ownerText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (12f * textScale).sp,
                            lineHeight = (14.5f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                        )
                    }
                    if (titleText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.8f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasContacts) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign,
                    verticalArrangement = Arrangement.spacedBy((1.5f * widthRatio).dp)
                ) {
                    val line1 = listOfNotNull(
                        phoneText.takeIf { it.isNotBlank() },
                        emailText.takeIf { it.isNotBlank() }
                    ).joinToString("  •  ")
                    if (line1.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(line1, draft.fontPair),
                            color = mainColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                        )
                    }
                    if (addressText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.4f * textScale).sp,
                            lineHeight = (10.5f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrontMinimalLinePreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Start)
    val contactAlign = toComposeTextAlign(draft.textAlign, TextAlign.End)
    val hasHeader = shopText.isNotBlank() || taglineText.isNotBlank() || logoBitmap != null
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val hasContacts = phoneText.isNotBlank() || emailText.isNotBlank() || addressText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (18f * widthRatio).dp, vertical = (14f * widthRatio).dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (hasHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (shopText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (15.5f * textScale).sp,
                            lineHeight = (18.5f * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_shop_name")
                        )
                    }
                    if (taglineText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (logoBitmap != null) {
                    Spacer(modifier = Modifier.width((8f * widthRatio).dp))
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = shopText.ifBlank { "Logo" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size((32f * widthRatio).dp)
                            .clip(RoundedCornerShape((6f * widthRatio).dp))
                            .optionalTag(isThumbnail, "bc_preview_logo")
                    )
                }
            }
        }

        if (hasHeader && (hasOwner || hasContacts)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((1.2f * widthRatio).coerceAtLeast(0.8f).dp)
                    .background(accentColor)
            )
        }

        if (hasOwner || hasContacts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy((10f * widthRatio).dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (hasOwner) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = toHorizontalAlignment(resolvedAlign)
                    ) {
                        if (ownerText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                                color = mainColor,
                                fontSize = (11.5f * textScale).sp,
                                lineHeight = (14f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = resolvedAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                            )
                        }
                        if (titleText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                                color = highlightColor,
                                fontSize = (8.8f * textScale).sp,
                                lineHeight = (11f * textScale).sp,
                                textAlign = resolvedAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (hasContacts) {
                    Column(
                        modifier = Modifier.weight(1.1f),
                        horizontalAlignment = toHorizontalAlignment(contactAlign),
                        verticalArrangement = Arrangement.spacedBy((1.5f * widthRatio).dp)
                    ) {
                        if (phoneText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(phoneText, draft.fontPair),
                                color = secondaryColor,
                                fontSize = (8.4f * textScale).sp,
                                lineHeight = (10.5f * textScale).sp,
                                textAlign = contactAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                            )
                        }
                        if (emailText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(emailText, draft.fontPair),
                                color = secondaryColor,
                                fontSize = (8.2f * textScale).sp,
                                lineHeight = (10.5f * textScale).sp,
                                textAlign = contactAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_email")
                            )
                        }
                        if (addressText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                                color = secondaryColor,
                                fontSize = (8.2f * textScale).sp,
                                lineHeight = (10.5f * textScale).sp,
                                textAlign = contactAlign,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFrontCornerAccentPreview(
    shopText: String,
    ownerText: String,
    titleText: String,
    taglineText: String,
    phoneText: String,
    emailText: String,
    addressText: String,
    logoBitmap: Bitmap?,
    mainColor: Color,
    secondaryColor: Color,
    highlightColor: Color,
    accentColor: Color,
    midColor: Color,
    draft: BusinessCardDraft,
    widthRatio: Float,
    textScale: Float,
    isThumbnail: Boolean
) {
    val resolvedAlign = toComposeTextAlign(draft.textAlign, TextAlign.Start)
    val colAlign = toHorizontalAlignment(resolvedAlign)
    val hasHeader = shopText.isNotBlank() || taglineText.isNotBlank() || logoBitmap != null
    val hasOwner = ownerText.isNotBlank() || titleText.isNotBlank()
    val hasContacts = phoneText.isNotBlank() || emailText.isNotBlank() || addressText.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        // Architectural diagonal corner accents (strictly clear of text content inset)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val topRight = Path().apply {
                moveTo(w * 0.78f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.34f)
                close()
            }
            drawPath(path = topRight, color = accentColor)

            val bottomLeft = Path().apply {
                moveTo(0f, h * 0.68f)
                lineTo(w * 0.16f, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path = bottomLeft, color = midColor)

            val cornerTickW = w * 0.09f
            val cornerTickH = h * 0.025f
            val inset = 10f * widthRatio.coerceAtLeast(0.4f)
            drawRect(
                color = accentColor,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(cornerTickW, cornerTickH)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = (18f * widthRatio).dp,
                    end = (24f * widthRatio).dp,
                    top = (15f * widthRatio).dp,
                    bottom = (14f * widthRatio).dp
                ),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = colAlign
        ) {
            if (hasHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = (26f * widthRatio).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((8f * widthRatio).dp)
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = shopText.ifBlank { "Logo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size((32f * widthRatio).dp)
                                .clip(RoundedCornerShape((6f * widthRatio).dp))
                                .optionalTag(isThumbnail, "bc_preview_logo")
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = colAlign
                    ) {
                        if (shopText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(shopText, draft.fontPair),
                                color = highlightColor,
                                fontSize = (14.8f * textScale).sp,
                                lineHeight = (17.5f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = resolvedAlign,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_shop_name")
                            )
                        }
                        if (taglineText.isNotBlank()) {
                            Text(
                                text = BusinessCardRenderer.buildComposeStyledText(taglineText, draft.fontPair),
                                color = secondaryColor,
                                fontSize = (8.6f * textScale).sp,
                                lineHeight = (11f * textScale).sp,
                                textAlign = resolvedAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (hasOwner) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign
                ) {
                    if (ownerText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(ownerText, draft.fontPair),
                            color = mainColor,
                            fontSize = (11.5f * textScale).sp,
                            lineHeight = (14f * textScale).sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_owner_name")
                        )
                    }
                    if (titleText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(titleText, draft.fontPair),
                            color = highlightColor,
                            fontSize = (8.8f * textScale).sp,
                            lineHeight = (11f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (hasContacts) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = colAlign,
                    verticalArrangement = Arrangement.spacedBy((1.5f * widthRatio).dp)
                ) {
                    val line1 = listOfNotNull(
                        phoneText.takeIf { it.isNotBlank() },
                        emailText.takeIf { it.isNotBlank() }
                    ).joinToString("  •  ")
                    if (line1.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(line1, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.6f * textScale).sp,
                            lineHeight = (10.8f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_phone")
                        )
                    }
                    if (addressText.isNotBlank()) {
                        Text(
                            text = BusinessCardRenderer.buildComposeStyledText(addressText, draft.fontPair),
                            color = secondaryColor,
                            fontSize = (8.5f * textScale).sp,
                            lineHeight = (10.8f * textScale).sp,
                            textAlign = resolvedAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().optionalTag(isThumbnail, "bc_preview_address")
                        )
                    }
                }
            }
        }
    }
}

private fun toComposeTextAlign(
    textAlign: BusinessCardTextAlign,
    defaultAlign: TextAlign
): TextAlign = when (textAlign) {
    BusinessCardTextAlign.AUTO -> defaultAlign
    BusinessCardTextAlign.LEFT -> TextAlign.Start
    BusinessCardTextAlign.CENTER -> TextAlign.Center
    BusinessCardTextAlign.RIGHT -> TextAlign.End
}

private fun toHorizontalAlignment(textAlign: TextAlign): Alignment.Horizontal = when (textAlign) {
    TextAlign.Center -> Alignment.CenterHorizontally
    TextAlign.End -> Alignment.End
    else -> Alignment.Start
}
