package com.example.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.EmptyOrErrorStateCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.data.GradedExamResult
import com.example.model.ExamAttempt
import com.example.model.ExamTest
import com.example.model.UiState
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ExamPrepHubScreen(
    initialClassFilter: Int,
    classes: List<com.example.model.SchoolClass> = com.example.model.DefaultCatalogSeed.defaultClasses,
    examsState: UiState<List<ExamTest>>,
    examHistoryState: UiState<List<ExamAttempt>>,
    onBack: () -> Unit,
    onStartExam: (ExamTest) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var examTypeFilter by rememberSaveable { mutableStateOf("ALL") } // ALL, MODEL, SUBJECT, CHAPTER
    var classFilter by rememberSaveable(initialClassFilter) { mutableIntStateOf(initialClassFilter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("exam_prep_hub_screen")
    ) {
        NavyScreenTopBar(
            title = strings.featureExam,
            subtitle = if (isBn) "বিষয়ভিত্তিক, অধ্যায়ভিত্তিক ও মডেল MCQ পরীক্ষা" else "Subject, Chapter & Model MCQ Exams",
            onBack = onBack
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (isBn) "সকল পরীক্ষা" else "Available Tests", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (isBn) "পরীক্ষার ইতিহাস" else "Exam History", fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedTab == 0) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    "ALL" to (if (isBn) "সব ধরন" else "All Types"),
                    "MODEL" to (if (isBn) "মডেল টেস্ট" else "Model Test"),
                    "SUBJECT" to (if (isBn) "বিষয়ভিত্তিক" else "Subject Test"),
                    "CHAPTER" to (if (isBn) "অধ্যায়ভিত্তিক" else "Chapter Test")
                )
                items(types, key = { it.first }) { (code, label) ->
                    FilterChip(
                        selected = examTypeFilter == code,
                        onClick = { examTypeFilter = code },
                        label = { Text(label) }
                    )
                }
            }

            val dynamicClassNumbers = remember(classes, examsState) {
                val fromClasses = classes.ifEmpty { com.example.model.DefaultCatalogSeed.defaultClasses }.map { it.classNumber }
                val fromExams = (examsState as? UiState.Success)?.data.orEmpty().map { it.classNumber }.filter { it > 0 }
                (fromClasses + fromExams).distinct().sorted()
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = classFilter == 0,
                        onClick = { classFilter = 0 },
                        label = { Text(strings.allFilter) }
                    )
                }
                items(dynamicClassNumbers) { cls ->
                    FilterChip(
                        selected = classFilter == cls,
                        onClick = { classFilter = cls },
                        label = { Text(classTitle(cls, lang)) }
                    )
                }
            }

            when (examsState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, examsState.message, isError = true, onRetry = onRetry)
                is UiState.Success -> {
                    val filtered = examsState.data.filter { ex ->
                        (examTypeFilter == "ALL" || ex.examType == examTypeFilter) &&
                            (classFilter == 0 || ex.classNumber == classFilter || ex.isDemo)
                    }
                    if (filtered.isEmpty()) {
                        EmptyOrErrorStateCard(
                            title = strings.emptyStateTitle,
                            body = if (isBn) "এই ফিল্টারে কোনো এমসিকিউ পরীক্ষা পাওয়া যায়নি।" else "No MCQ exams found for this filter."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { exam ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("exam_card_${exam.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = RoyalBlue.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = when (exam.examType) {
                                                        "SUBJECT" -> if (isBn) "বিষয়ভিত্তিক পরীক্ষা" else "Subject Test"
                                                        "CHAPTER" -> if (isBn) "অধ্যায়ভিত্তিক পরীক্ষা" else "Chapter Test"
                                                        else -> if (isBn) "মডেল টেস্ট" else "Model Test"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = RoyalBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                            if (exam.isDemo) {
                                                Surface(
                                                    color = Color(0xFFFEF3C7),
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        text = "DEMO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF92400E),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = if (!isBn && exam.titleEn.isNotBlank()) exam.titleEn else exam.titleBn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${classTitle(exam.classNumber, lang)} • ${exam.subjectNameBn}" +
                                                if (exam.chapter.isNotBlank()) " • ${exam.chapter}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    text = "⏱ ${exam.durationMinutes.formatLocalized(lang)} ${if (isBn) "মিনিট" else "min"}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "📝 ${exam.questionCount.formatLocalized(lang)} ${if (isBn) "প্রশ্ন" else "Qs"}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "🏆 ${exam.totalMarks.formatLocalized(lang)} ${if (isBn) "নম্বর" else "Marks"}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Button(
                                                onClick = { onStartExam(exam) },
                                                shape = RoundedCornerShape(50),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = EmeraldDark,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("start_exam_btn_${exam.id}")
                                            ) {
                                                Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(if (isBn) "পরীক্ষা দিন" else "Start Exam", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (examHistoryState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, examHistoryState.message, isError = true, onRetry = onRetry)
                is UiState.Success -> {
                    if (examHistoryState.data.isEmpty()) {
                        EmptyOrErrorStateCard(
                            title = if (isBn) "পরীক্ষার ইতিহাস খালি" else "No Exam Attempts Yet",
                            body = if (isBn) "আপনি যেকোনো মডেল টেস্ট বা বিষয়ভিত্তিক পরীক্ষায় অংশ নিলে ফলাফল এখানে সংরক্ষিত থাকবে।" else "Take any MCQ test to track your scores and exam history."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(examHistoryState.data, key = { it.id }) { attempt ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = RoyalBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attempt.examTitleBn,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isBn) {
                                                    "সঠিক উত্তর: ${attempt.correctCount.formatLocalized(lang)} / ${attempt.totalQuestions.formatLocalized(lang)}"
                                                } else {
                                                    "Correct: ${attempt.correctCount} / ${attempt.totalQuestions}"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            color = EmeraldPrimary.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "${attempt.score.formatLocalized(lang)} / ${attempt.totalMarks.formatLocalized(lang)}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = EmeraldDark,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamSessionScreen(
    exam: ExamTest,
    gradedResult: GradedExamResult?,
    onBack: () -> Unit,
    onSubmitAnswers: (selectedAnswers: List<Int>, onDone: (Boolean) -> Unit) -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    val questions = remember(exam.id) { exam.parsedQuestions() }
    val selectedOptions = remember(exam.id) {
        mutableStateListOf<Int>().apply {
            repeat(questions.size) { add(-1) }
        }
    }

    var currentQuestionIdx by rememberSaveable(exam.id) { mutableIntStateOf(0) }
    var remainingSeconds by rememberSaveable(exam.id) {
        mutableIntStateOf((exam.durationMinutes.coerceAtLeast(1)) * 60)
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSubmitted by rememberSaveable(exam.id) { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    // Live Countdown Timer (ticks 1s, auto-submits when reaching 0)
    LaunchedEffect(exam.id, isSubmitted) {
        if (!isSubmitted) {
            while (remainingSeconds > 0 && !isSubmitted) {
                delay(1000L)
                remainingSeconds -= 1
            }
            if (remainingSeconds <= 0 && !isSubmitted && !isSubmitting) {
                isSubmitting = true
                onSubmitAnswers(selectedOptions.toList()) { ok ->
                    isSubmitting = false
                    if (ok) isSubmitted = true
                }
            }
        }
    }

    val hh = remainingSeconds / 3600
    val mm = (remainingSeconds % 3600) / 60
    val ss = remainingSeconds % 60
    val formattedTimer = String.format(Locale.US, "%02d:%02d:%02d", hh, mm, ss).formatLocalized(lang)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("exam_session_screen")
    ) {
        NavyScreenTopBar(
            title = exam.titleBn,
            subtitle = if (isSubmitted) {
                if (isBn) "পরীক্ষার ফলাফল ও উত্তরপত্র" else "Exam Result & Answer Review"
            } else {
                "${exam.subjectNameBn} • ${exam.totalMarks.formatLocalized(lang)} ${if (isBn) "নম্বর" else "Marks"}"
            },
            onBack = onBack
        )

        if (questions.isEmpty()) {
            EmptyOrErrorStateCard(
                title = strings.emptyStateTitle,
                body = if (isBn) "এই পরীক্ষায় এখনো কোনো প্রশ্ন যুক্ত হয়নি।" else "No questions found in this exam."
            )
            return@Column
        }

        if (isSubmitted && gradedResult != null && gradedResult.examId == exam.id) {
            ExamResultContent(
                exam = exam,
                result = gradedResult,
                lang = lang,
                onBackToExams = onBack
            )
        } else {
            val safeIdx = currentQuestionIdx.coerceIn(0, questions.lastIndex)
            val currentQ = questions[safeIdx]
            val chosenForCurrent = selectedOptions.getOrNull(safeIdx) ?: -1

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Countdown Timer & Progress Banner matching Reference Sheet
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = if (remainingSeconds < 60) ErrorRed else NavyPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formattedTimer,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (remainingSeconds < 60) ErrorRed else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (isBn) {
                                    "প্রশ্ন ${(safeIdx + 1).formatLocalized(lang)} / ${questions.size.formatLocalized(lang)}"
                                } else {
                                    "Question ${safeIdx + 1} / ${questions.size}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Question Jump Strip
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(questions) { idx, _ ->
                            val answered = (selectedOptions.getOrNull(idx) ?: -1) >= 0
                            val isCurrent = idx == safeIdx
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCurrent -> RoyalBlue
                                            answered -> EmeraldPrimary.copy(alpha = 0.22f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) RoyalBlue else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { currentQuestionIdx = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (idx + 1).formatLocalized(lang),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Question & 4 Options Card (Never exposes answer key before submission!)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = currentQ.questionText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(4.dp))

                            val options = listOf(
                                "A" to currentQ.optionA,
                                "B" to currentQ.optionB,
                                "C" to currentQ.optionC,
                                "D" to currentQ.optionD
                            )
                            options.forEachIndexed { optIdx, (letter, optText) ->
                                val isSelected = chosenForCurrent == optIdx
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        EmeraldPrimary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.background
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) EmeraldDark else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable(enabled = !isSubmitting) {
                                            selectedOptions[safeIdx] = optIdx
                                        }
                                        .testTag("mcq_option_$letter")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) EmeraldDark else MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = optText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Prev / Next Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (safeIdx > 0) currentQuestionIdx = safeIdx - 1 },
                            enabled = safeIdx > 0 && !isSubmitting,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBn) "পূর্বের" else "Previous", fontWeight = FontWeight.Bold)
                        }

                        if (safeIdx < questions.lastIndex) {
                            Button(
                                onClick = { currentQuestionIdx = safeIdx + 1 },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(if (isBn) "পরবর্তী" else "Next", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        } else {
                            Button(
                                onClick = { showSubmitConfirm = true },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("exam_submit_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(if (isBn) "সাবমিট করুন" else "Submit Exam", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (safeIdx < questions.lastIndex) {
                        OutlinedButton(
                            onClick = { showSubmitConfirm = true },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("exam_early_submit_button")
                        ) {
                            Text(
                                text = if (isBn) "পরীক্ষা শেষ ও উত্তরপত্র জমা দিন" else "Finish & Submit Exam",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSubmitConfirm) {
        val answeredCount = selectedOptions.count { it >= 0 }
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = {
                Text(
                    text = if (isBn) "পরীক্ষা জমা দেবেন?" else "Submit Exam?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBn) {
                        "আপনি ${questions.size.formatLocalized(lang)}টি প্রশ্নের মধ্যে ${answeredCount.formatLocalized(lang)}টির উত্তর নির্বাচন করেছেন। জমা দেওয়ার পর উত্তর লক হয়ে যাবে এবং ফলাফল দেখানো হবে।"
                    } else {
                        "You answered $answeredCount out of ${questions.size} questions. Once submitted, answers are locked and graded."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirm = false
                        isSubmitting = true
                        onSubmitAnswers(selectedOptions.toList()) { ok ->
                            isSubmitting = false
                            if (ok) isSubmitted = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_submit_exam_btn")
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun ExamResultContent(
    exam: ExamTest,
    result: GradedExamResult,
    lang: AppLanguage,
    onBackToExams: () -> Unit
) {
    val isBn = lang == AppLanguage.BN
    val questions = remember(exam.id) { exam.parsedQuestions() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("exam_result_view")
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = NavyPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isBn) "অভিনন্দন! ফলাফল প্রস্তুত" else "Exam Graded!",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${result.earnedScore.formatLocalized(lang)} / ${result.totalMarks.formatLocalized(lang)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isBn) {
                            "সঠিক উত্তর: ${result.correctCount.formatLocalized(lang)}টি (মোট ${result.totalQuestions.formatLocalized(lang)}টি প্রশ্ন)"
                        } else {
                            "Correct Answers: ${result.correctCount} of ${result.totalQuestions}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onBackToExams,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color(0xFF042F1E)
                        )
                    ) {
                        Text(
                            text = if (isBn) "সকল পরীক্ষায় ফিরে যান" else "Back to Exam Hub",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        itemsIndexed(questions) { idx, q ->
            val userAns = result.selectedAnswers.getOrElse(idx) { -1 }
            val correctAns = result.correctIndices.getOrElse(idx) { 0 }
            val explanation = result.explanations.getOrElse(idx) { "" }
            val opts = listOf(q.optionA, q.optionB, q.optionC, q.optionD)
            val isCorrect = userAns == correctAns

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (isCorrect) EmeraldDark else ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(idx + 1).formatLocalized(lang)}. ${q.questionText}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isBn) {
                            "আপনার উত্তর: ${if (userAns in 0..3) opts[userAns] else "উত্তর দেননি"}"
                        } else {
                            "Your Answer: ${if (userAns in 0..3) opts[userAns] else "Skipped"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCorrect) EmeraldDark else ErrorRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isBn) {
                            "সঠিক উত্তর: ${opts.getOrElse(correctAns) { "" }}"
                        } else {
                            "Correct Answer: ${opts.getOrElse(correctAns) { "" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldDark,
                        fontWeight = FontWeight.Bold
                    )
                    if (explanation.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${if (isBn) "ব্যাখ্যা: " else "Explanation: "}$explanation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
