package com.example.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.AppLogoBadge
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val strings = LocalAppStrings.current
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.82f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "splash_alpha"
    )
    val infinite = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(1450L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF031026), NavyDeep, NavyPrimary, Color(0xFF072B52))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .scale(scale)
                .alpha(alpha)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = glowAlpha * 0.45f))
                )
                AppLogoBadge(size = 112.dp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                        append("Smart School\n")
                    }
                    withStyle(SpanStyle(color = EmeraldPrimary, fontWeight = FontWeight.Bold)) {
                        append("Library BD")
                    }
                },
                fontSize = 30.sp,
                lineHeight = 38.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "পড়ো, জানো, এগিয়ে যাও...",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(44.dp))

            CircularProgressIndicator(
                color = EmeraldPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = strings.loading,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

private data class OnboardingFeatureItem(
    val icon: ImageVector,
    val titleBn: String,
    val descBn: String,
    val titleEn: String,
    val descEn: String
)

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val strings = LocalAppStrings.current
    val isBn = strings.language == AppLanguage.BN

    val features = remember {
        listOf(
            OnboardingFeatureItem(
                Icons.Filled.School,
                "১. ডিজিটাল স্কুল লাইব্রেরি",
                "পঞ্চম থেকে দশম শ্রেণির শিক্ষার্থীদের জন্য আধুনিক ও সুশৃঙ্খল ডিজিটাল লাইব্রেরি।",
                "1. Digital School Library",
                "Complete structured digital school library for Classes 5 to 10."
            ),
            OnboardingFeatureItem(
                Icons.AutoMirrored.Filled.MenuBook,
                "২. মূল পাঠ্যবই (NCTB)",
                "শ্রেণি ও বিভাগ অনুযায়ী অনুমোদিত মূল পাঠ্যবই আসল পৃষ্ঠার গঠনে পড়ুন।",
                "2. Original Textbooks",
                "Read authorized NCTB textbooks organized by class, stream, and subject."
            ),
            OnboardingFeatureItem(
                Icons.Filled.Description,
                "৩. গাইড ও অধ্যায়ভিত্তিক নোট",
                "মূল বইয়ের পাশাপাশি আলাদা গাইড, সৃজনশীল প্রশ্ন, অনুশীলনী ও সাজেশন।",
                "3. Guides & Chapter Notes",
                "Dedicated library for chapter notes, CQ, MCQ, explanations, and suggestions."
            ),
            OnboardingFeatureItem(
                Icons.Filled.Lightbulb,
                "৪. সাধারণ জ্ঞান ভাণ্ডার",
                "বাংলাদেশ, আন্তর্জাতিক, বিজ্ঞান, ইতিহাস, ভূগোল, খেলাধুলা ও সাম্প্রতিক বিষয়াবলি।",
                "4. General Knowledge",
                "Explore 7 curated GK categories with answers and clear explanations."
            ),
            OnboardingFeatureItem(
                Icons.Filled.Quiz,
                "৫. MCQ পরীক্ষা ও প্রস্তুতি",
                "কাউন্টডাউন টাইমারসহ বিষয়ভিত্তিক ও মডেল টেস্ট দিন এবং তাৎক্ষণিক ফলাফল দেখুন।",
                "5. MCQ Examinations",
                "Timed subject, chapter, and model MCQ exams with automatic scoring."
            ),
            OnboardingFeatureItem(
                Icons.Filled.BookmarkAdded,
                "৬. ব্যক্তিগত বুকমার্ক ও নোটবুক",
                "সম্পূর্ণ বই বা নির্দিষ্ট পৃষ্ঠা বুকমার্ক করুন এবং নিজস্ব নোটবুকে নোট লিখুন।",
                "6. Personal Bookmarks & Notes",
                "Save whole books or exact PDF pages and maintain your private notebook."
            ),
            OnboardingFeatureItem(
                Icons.AutoMirrored.Filled.TrendingUp,
                "৭. পড়ার অগ্রগতি ট্র্যাকিং",
                "সর্বশেষ পঠিত পৃষ্ঠা থেকে পুনরায় পড়া শুরু করুন এবং শেখার অগ্রগতি দেখুন।",
                "7. Reading Progress",
                "Automatically restore your last read page and monitor study progress."
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner Card inspired by Uploaded Onboarding Reference
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(NavyDeep, RoyalBlue, EmeraldDark))
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppLogoBadge(size = 76.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (isBn) "স্বাগতম Smart School Library BD-তে" else "Welcome to Smart School Library BD",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (isBn) {
                            "একটি পূর্ণাঙ্গ ডিজিটাল শিক্ষার ভাণ্ডার — পঞ্চম থেকে দশম শ্রেণির জন্য!"
                        } else {
                            "A complete digital learning companion for Classes 5 to 10!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            features.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = EmeraldDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBn) item.titleBn else item.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isBn) item.descBn else item.descEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldDark,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_continue_button")
            ) {
                Text(
                    text = strings.onboardingContinue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
