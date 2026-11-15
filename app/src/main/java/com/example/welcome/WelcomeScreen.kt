package com.example.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.components.AppLogoBadge
import com.example.ui.i18n.AppLanguage

private data class WelcomeHighlightCardSpec(
    val titleBn: String,
    val titleEn: String,
    val icon: ImageVector,
    val iconTint: Color,
    val lightBg: Color,
    val darkBg: Color,
    val tag: String
)

/**
 * Native Jetpack Compose Educational Welcome Screen for Smart School Library BD.
 * Works 100% offline using locally bundled vector & logo drawables, adapts to phones and tablets,
 * supports Light and Dark modes, persists onboarding completion via DataStore Preferences,
 * and allows bilingual Bengali / English switching.
 */
@Composable
fun WelcomeScreen(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onStartClick: () -> Unit,
    isRevisitFromSettings: Boolean = false,
    onBackFromSettings: (() -> Unit)? = null
) {
    if (isRevisitFromSettings && onBackFromSettings != null) {
        BackHandler(onBack = onBackFromSettings)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.3f

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF071936),
                Color(0xFF0C274E),
                Color(0xFF0F2942),
                Color(0xFF0A2E24)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF7DD3FC),
                Color(0xFFBAE6FD),
                Color(0xFFF0F9FF),
                Color(0xFFECFDF5),
                Color(0xFFDCFCE7)
            )
        )
    }

    val primaryHeadingColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F2B6B)
    val accentGreenColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF0D8A2C)
    val introTextColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF13295B)

    val highlights = remember {
        listOf(
            WelcomeHighlightCardSpec(
                titleBn = "মূল পাঠ্যবই\n(Class 5–10)",
                titleEn = "Original Textbooks\n(Class 5–10)",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconTint = Color(0xFF2563EB),
                lightBg = Color(0xFFEEF2FF),
                darkBg = Color(0xFF172554),
                tag = "welcome_highlight_textbooks"
            ),
            WelcomeHighlightCardSpec(
                titleBn = "গাইড ও নোট",
                titleEn = "Guides & Notes",
                icon = Icons.Filled.EditNote,
                iconTint = Color(0xFF0284C7),
                lightBg = Color(0xFFE0F2FE),
                darkBg = Color(0xFF0C4A6E),
                tag = "welcome_highlight_guides"
            ),
            WelcomeHighlightCardSpec(
                titleBn = "সাধারণ জ্ঞান",
                titleEn = "General Knowledge",
                icon = Icons.Filled.Lightbulb,
                iconTint = Color(0xFFD97706),
                lightBg = Color(0xFFFEF3C7),
                darkBg = Color(0xFF451A03),
                tag = "welcome_highlight_gk"
            ),
            WelcomeHighlightCardSpec(
                titleBn = "MCQ ও পরীক্ষার প্রস্তুতি",
                titleEn = "MCQ & Exam Prep",
                icon = Icons.AutoMirrored.Filled.FactCheck,
                iconTint = Color(0xFF059669),
                lightBg = Color(0xFFECFDF5),
                darkBg = Color(0xFF064E3B),
                tag = "welcome_highlight_mcq"
            ),
            WelcomeHighlightCardSpec(
                titleBn = "Original PDF Reading",
                titleEn = "Original PDF Reading",
                icon = Icons.Filled.PictureAsPdf,
                iconTint = Color(0xFFDC2626),
                lightBg = Color(0xFFFEF2F2),
                darkBg = Color(0xFF450A0A),
                tag = "welcome_highlight_pdf"
            ),
            WelcomeHighlightCardSpec(
                titleBn = "বুকমার্ক ও পড়ার অগ্রগতি",
                titleEn = "Bookmarks & Progress",
                icon = Icons.Filled.Star,
                iconTint = Color(0xFFDB2777),
                lightBg = Color(0xFFFDF2F8),
                darkBg = Color(0xFF500724),
                tag = "welcome_highlight_bookmarks"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("welcome_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        // Decorative Soft Green Horizon Hills at Bottom
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .align(Alignment.BottomCenter)
        ) {
            val w = size.width
            val h = size.height
            val backHill = Path().apply {
                moveTo(0f, h * 0.45f)
                quadraticTo(w * 0.32f, h * 0.10f, w * 0.65f, h * 0.55f)
                quadraticTo(w * 0.85f, h * 0.78f, w, h * 0.35f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = backHill,
                color = if (isDark) Color(0xFF064E3B).copy(alpha = 0.55f) else Color(0xFF4ADE80).copy(alpha = 0.45f)
            )
            val frontHill = Path().apply {
                moveTo(0f, h * 0.62f)
                quadraticTo(w * 0.5f, h * 0.92f, w, h * 0.48f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = frontHill,
                color = if (isDark) Color(0xFF059669).copy(alpha = 0.45f) else Color(0xFF22C55E).copy(alpha = 0.42f)
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional Back Row when opened from Settings -> About / Introduction
            if (isRevisitFromSettings && onBackFromSettings != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackFromSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("welcome_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryHeadingColor
                        )
                    }
                    Text(
                        text = if (currentLanguage == AppLanguage.BN) "অ্যাপ পরিচিতি" else "App Introduction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryHeadingColor
                    )
                }
            }

            // 1. Smart School Library BD Logo & Brand Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag("welcome_brand_header")
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = if (isDark) 0.14f else 0.85f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Box(modifier = Modifier.padding(6.dp)) {
                        AppLogoBadge(size = 64.dp)
                    }
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Smart School Library BD",
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryHeadingColor
                    )
                    Text(
                        text = "Read • Learn • Grow",
                        style = MaterialTheme.typography.labelLarge,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E3A8A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2 & 3. Hero Educational Section: Large Bengali Headline + School Students Reading Books Illustration
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0F294A).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.65f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, start = 14.dp, end = 14.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Exact Required Bengali Headline
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = primaryHeadingColor, fontWeight = FontWeight.ExtraBold)) {
                                append("বই পড়ি, জ্ঞান বাড়াই,\n")
                            }
                            withStyle(SpanStyle(color = accentGreenColor, fontWeight = FontWeight.ExtraBold)) {
                                append("স্মার্ট বাংলাদেশ গড়ি")
                            }
                        },
                        fontSize = 23.sp,
                        lineHeight = 31.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "বই পড়ি, জ্ঞান বাড়াই, স্মার্ট বাংলাদেশ গড়ি"
                            }
                            .testTag("welcome_headline")
                    )

                    // Decorative Red Curved Brushstroke Accent
                    Canvas(
                        modifier = Modifier
                            .width(170.dp)
                            .height(10.dp)
                            .padding(top = 2.dp)
                    ) {
                        val strokePath = Path().apply {
                            moveTo(0f, size.height * 0.85f)
                            quadraticTo(size.width * 0.5f, 0f, size.width, size.height * 0.75f)
                        }
                        drawPath(
                            path = strokePath,
                            color = Color(0xFFDC2626),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Locally Bundled Offline Educational Illustration of Students Reading Books
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_students),
                        contentDescription = "স্কুল শিক্ষার্থীদের বই পড়ার চিত্র / School students reading books",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .testTag("welcome_students_illustration")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Short Introduction
            Text(
                text = "Smart School Library BD — আপনার ডিজিটাল শিক্ষাসঙ্গী। এখানে পাবেন পঞ্চম থেকে দশম শ্রেণির পাঠ্যবই, গাইড, নোট, সাধারণ জ্ঞান এবং পরীক্ষার প্রস্তুতির সহায়ক উপকরণ।",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = introTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .testTag("welcome_intro_text")
            )

            if (currentLanguage == AppLanguage.EN) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your digital learning companion for Classes 5–10 textbooks, guides, notes, general knowledge, and exam preparation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = introTextColor.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Compact 2x3 Educational Highlights Grid
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF0F172A).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.82f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_highlights_grid")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    highlights.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { spec ->
                                WelcomeHighlightTile(
                                    spec = spec,
                                    isDark = isDark,
                                    currentLanguage = currentLanguage,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Educational Quote Banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDark) Color(0xFF422006) else Color(0xFFFEF3C7),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFFF59E0B).copy(alpha = 0.45f) else Color(0xFFFCD34D)
                    ),
                    modifier = Modifier.testTag("welcome_quote_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "❝ ",
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "শিক্ষাই জাতির মেরুদণ্ড।",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFFFEF3C7) else Color(0xFF0F2B6B)
                        )
                        Text(
                            text = " ❞",
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 8. One Large, Attractive Green Button: "শুরু করুন"
            Button(
                onClick = onStartClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D8A2C),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .heightIn(min = 56.dp)
                    .testTag("welcome_start_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "শুরু করুন",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7. Bengali / English Language Selection Pill (Accessible >= 48dp height)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.92f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF475569) else Color(0xFF93C5FD)
                ),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("welcome_language_selector")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E3A8A),
                        modifier = Modifier
                            .padding(start = 6.dp, end = 4.dp)
                            .size(18.dp)
                    )

                    LanguageChoiceChip(
                        label = "বাংলা",
                        selected = currentLanguage == AppLanguage.BN,
                        isDark = isDark,
                        tag = "welcome_lang_bn",
                        onClick = { onSelectLanguage(AppLanguage.BN) }
                    )

                    Text(
                        text = "/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    LanguageChoiceChip(
                        label = "English",
                        selected = currentLanguage == AppLanguage.EN,
                        isDark = isDark,
                        tag = "welcome_lang_en",
                        onClick = { onSelectLanguage(AppLanguage.EN) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WelcomeHighlightTile(
    spec: WelcomeHighlightCardSpec,
    isDark: Boolean,
    currentLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (isDark) spec.darkBg else spec.lightBg,
        border = BorderStroke(
            width = 1.dp,
            color = spec.iconTint.copy(alpha = if (isDark) 0.35f else 0.16f)
        ),
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag(spec.tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(spec.iconTint.copy(alpha = if (isDark) 0.25f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = if (isDark) Color.White else spec.iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (currentLanguage == AppLanguage.BN) spec.titleBn else spec.titleEn,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 14.5.sp
                ),
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F2B6B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LanguageChoiceChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val activeBg = if (isDark) Color(0xFF2563EB) else Color(0xFFDBEAFE)
    val activeText = if (isDark) Color.White else Color(0xFF0F2B6B)
    val inactiveText = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)

    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) activeBg else Color.Transparent,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .heightIn(min = 38.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (selected) activeText else inactiveText
            )
        }
    }
}
