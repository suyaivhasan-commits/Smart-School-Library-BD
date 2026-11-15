package com.example.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.components.AppLogoBadge
import com.example.components.NavyScreenTopBar
import com.example.components.classGradientFor
import com.example.model.AcademicStream
import com.example.model.DefaultCatalogSeed
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.streamTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.RoyalBlue

@Composable
fun ClassSelectionScreen(
    classNumber: Int,
    streams: List<AcademicStream>,
    initialStream: String?,
    hasStreamsOverride: Boolean? = null,
    activeSubscribedStreams: Set<String> = emptySet(),
    isSuperAdmin: Boolean = false,
    onBack: () -> Unit,
    onOpenTextbooks: (Int, String) -> Unit,
    onOpenGuides: (Int, String) -> Unit,
    onOpenSubscription: (Int, String) -> Unit = { _, _ -> },
    onOpenGk: () -> Unit,
    onOpenExamPrep: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val requiresStream = hasStreamsOverride ?: (classNumber == 9 || classNumber == 10)

    var chosenStream by rememberSaveable(classNumber) {
        mutableStateOf(
            if (requiresStream) {
                val candidate = initialStream?.takeIf { it in listOf("SCIENCE", "HUMANITIES", "BUSINESS") } ?: ""
                if (isSuperAdmin || candidate in activeSubscribedStreams) candidate else activeSubscribedStreams.firstOrNull().orEmpty()
            } else {
                "ALL"
            }
        )
    }

    val titleText = classTitle(classNumber, lang)
    val subtitleText = if (requiresStream && chosenStream.isNotBlank()) {
        streamTitle(chosenStream, lang)
    } else if (isBn) {
        "পাঠ্যবই, গাইড ও পরীক্ষার প্রস্তুতি"
    } else {
        "Textbooks, Guides & Exam Preparation"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("class_selection_screen_$classNumber")
    ) {
        NavyScreenTopBar(
            title = titleText,
            subtitle = subtitleText,
            onBack = onBack
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Class Banner Card matching Reference Image
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(classGradientFor(classNumber))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppLogoBadge(size = 64.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (requiresStream && chosenStream.isNotBlank()) {
                                streamTitle(chosenStream, lang)
                            } else if (isBn) {
                                "মূল পাঠ্যবই ও গাইড সংগ্রহশালা"
                            } else {
                                "Original Textbooks & Study Guides"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Stream Selection Step for Classes 9 & 10
                if (requiresStream && chosenStream.isBlank()) {
                    Text(
                        text = strings.selectStreamTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val activeStreams = streams.ifEmpty { DefaultCatalogSeed.defaultStreams }
                    activeStreams.forEach { stream ->
                        val isUnlocked = isSuperAdmin || stream.code in activeSubscribedStreams
                        val icon = when (stream.code) {
                            "SCIENCE" -> Icons.Filled.Science
                            "HUMANITIES" -> Icons.Filled.Public
                            else -> Icons.Filled.AccountBalance
                        }
                        val accent = when (stream.code) {
                            "SCIENCE" -> RoyalBlue
                            "HUMANITIES" -> EmeraldDark
                            else -> Color(0xFF7C3AED)
                        }
                        val subText = if (isUnlocked) {
                            if (isBn) "সক্রিয় সাবস্ক্রিপশন • পাঠ্যবই ও গাইড দেখুন" else "Active Subscription • View books & guides"
                        } else {
                            if (isBn) "আলাদা সাবস্ক্রিপশন প্রয়োজন • কিনতে ট্যাপ করুন" else "Separate Subscription Required • Tap to buy"
                        }
                        SectionActionCard(
                            title = if (isBn) stream.displayNameBn else stream.displayNameEn,
                            subtitle = subText,
                            icon = icon,
                            accentColor = if (isUnlocked) accent else Color(0xFF6B7280),
                            testTag = "stream_select_${stream.code}",
                            onClick = {
                                if (isUnlocked) {
                                    chosenStream = stream.code
                                } else {
                                    onOpenSubscription(classNumber, stream.code)
                                }
                            }
                        )
                    }
                } else {
                    val streamIsUnlocked = isSuperAdmin || chosenStream in activeSubscribedStreams
                    if (requiresStream) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isBn) "নির্বাচিত বিভাগ:" else "Selected Stream:"} ${streamTitle(chosenStream, lang)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (streamIsUnlocked) EmeraldDark else Color(0xFFDC2626)
                            )
                            OutlinedButton(
                                onClick = { chosenStream = "" },
                                modifier = Modifier.testTag("change_stream_button")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isBn) "বিভাগ পরিবর্তন" else "Change Stream")
                            }
                        }
                    }

                    // A. মূল পাঠ্যবই (Original Textbooks)
                    SectionActionCard(
                        title = strings.originalTextbooks,
                        subtitle = if (streamIsUnlocked) strings.allBooksSub else (if (isBn) "এই বিভাগের জন্য সাবস্ক্রিপশন নিন" else "Subscription required for this stream"),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        accentColor = if (streamIsUnlocked) RoyalBlue else Color(0xFF6B7280),
                        testTag = "open_textbooks_card",
                        onClick = {
                            if (streamIsUnlocked) {
                                onOpenTextbooks(classNumber, chosenStream.ifBlank { "ALL" })
                            } else {
                                onOpenSubscription(classNumber, chosenStream.ifBlank { "ALL" })
                            }
                        }
                    )

                    // B. গাইড ও নোট (Guides & Notes)
                    SectionActionCard(
                        title = strings.guidesAndNotes,
                        subtitle = if (streamIsUnlocked) strings.allGuidesSub else (if (isBn) "এই বিভাগের জন্য সাবস্ক্রিপশন নিন" else "Subscription required for this stream"),
                        icon = Icons.Filled.Description,
                        accentColor = if (streamIsUnlocked) EmeraldDark else Color(0xFF6B7280),
                        testTag = "open_guides_card",
                        onClick = {
                            if (streamIsUnlocked) {
                                onOpenGuides(classNumber, chosenStream.ifBlank { "ALL" })
                            } else {
                                onOpenSubscription(classNumber, chosenStream.ifBlank { "ALL" })
                            }
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isBn) "সহায়ক প্রস্তুতি মডিউল" else "Practice & Knowledge Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShortcutHalfCard(
                            title = strings.featureGk,
                            icon = Icons.Filled.Lightbulb,
                            tint = GoldDark,
                            onClick = onOpenGk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("class_shortcut_gk")
                        )
                        ShortcutHalfCard(
                            title = strings.featureExam,
                            icon = Icons.Filled.Quiz,
                            tint = EmeraldPrimary,
                            onClick = { onOpenExamPrep(classNumber) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("class_shortcut_exam")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ShortcutHalfCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
