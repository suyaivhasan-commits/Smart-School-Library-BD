package com.example.reader

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class SpeechLanguage {
    BENGALI,
    ENGLISH
}

data class SpeechChunk(
    val index: Int,
    val text: String,
    val language: SpeechLanguage
)

sealed interface PageTextExtractionResult {
    data class Success(
        val chunks: List<SpeechChunk>,
        val primaryLanguage: SpeechLanguage
    ) : PageTextExtractionResult

    data class Error(
        val messageBn: String,
        val messageEn: String
    ) : PageTextExtractionResult
}

enum class PageAudioPhase {
    IDLE,
    EXTRACTING,
    PLAYING,
    PAUSED,
    ERROR
}

data class PageAudioUiState(
    val phase: PageAudioPhase = PageAudioPhase.IDLE,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val activeLanguage: SpeechLanguage? = null,
    val errorMessage: String? = null,
    val activePageIndex: Int = -1
)

/**
 * Pure Kotlin text normalization, legacy Bijoy/SutonnyMJ mojibake detection,
 * and Bengali/English language chunking for PDF page-wise audio reading.
 */
object PdfPageTextProcessor {

    private val bijoyGlyphSet = setOf(
        '†', '‡', '•', '©', '¨', '®', '°', '±', '²', '³', '´', 'µ', '¶',
        '¸', '¹', 'º', '»', '¼', '½', '¾', '¿', '‹', '›', '‰', 'Š', 'Œ',
        'Ž', 'š', 'œ', 'ž', 'Ÿ', '‚', '„', '…'
    )

    private val bijoyClusterRegex = Regex(
        "(‡[A-Za-z]|†[A-Za-z]|cÖ|evsjv|Avwg|KviY|GKwU|wKš‘|Zvn‡j)"
    )

    private val isolatedPageNumberLineRegex = Regex("^[\\s()\\[\\]\\-–—]*[0-9০-৯]{1,5}[\\s()\\[\\]\\-–—]*$")
    private val multiSpaceRegex = Regex("[ \\t\\x0B\\f\\r]+")
    private val multiNewlineRegex = Regex("\\n{3,}")

    fun isBengaliChar(ch: Char): Boolean = ch in '\u0980'..'\u09FF'

    fun isEnglishLetter(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z'

    fun countBengaliChars(text: String): Int = text.count(::isBengaliChar)

    fun countEnglishChars(text: String): Int = text.count(::isEnglishLetter)

    fun hasReadableText(text: String): Boolean {
        val letterCount = text.count { isBengaliChar(it) || isEnglishLetter(it) }
        return letterCount >= 4
    }

    fun normalizeText(raw: String): String {
        if (raw.isBlank()) return ""
        val unifiedLines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\u200B", "")
            .replace("\uFEFF", "")
            // Re-join soft English word-wrap hyphens at end of line
            .replace(Regex("([A-Za-z])-\\n([A-Za-z])"), "$1$2")

        val cleanedLines = unifiedLines
            .lines()
            .map { line -> line.replace(multiSpaceRegex, " ").trim() }
            .filter { line ->
                line.isNotEmpty() && !isolatedPageNumberLineRegex.matches(line)
            }

        return cleanedLines
            .joinToString("\n")
            .replace(multiNewlineRegex, "\n\n")
            .trim()
    }

    /**
     * Detects non-Unicode legacy Bengali PDFs encoded using Bijoy / SutonnyMJ ANSI font maps.
     * Never flags normal English or Unicode Bengali text.
     */
    fun looksLikeLegacyBijoyMojibake(rawText: String, normalizedText: String = normalizeText(rawText)): Boolean {
        val sample = normalizedText.ifBlank { rawText }.trim()
        if (sample.isEmpty()) return false

        val bengaliUnicodeCount = countBengaliChars(sample)
        if (bengaliUnicodeCount > 0) {
            return false
        }

        val replacementCount = sample.count { it == '\uFFFD' }
        val readableLetters = sample.count { it.isLetterOrDigit() }
        if (replacementCount >= 3 && readableLetters < 12) {
            return true
        }

        val bijoyMarkers = sample.count { it in bijoyGlyphSet }
        val clusterMatches = bijoyClusterRegex.findAll(sample).count()
        val latinExtendedCount = sample.count { it in '\u00C0'..'\u00FF' }

        if (clusterMatches >= 2) return true
        if (bijoyMarkers >= 2) return true

        val nonWhitespace = sample.count { !it.isWhitespace() }.coerceAtLeast(1)
        val suspiciousRatio = (bijoyMarkers + latinExtendedCount).toFloat() / nonWhitespace.toFloat()
        return (bijoyMarkers >= 1 || latinExtendedCount >= 3) && suspiciousRatio >= 0.04f
    }

    fun detectSegmentLanguage(segment: String, fallback: SpeechLanguage = SpeechLanguage.BENGALI): SpeechLanguage {
        val bnCount = countBengaliChars(segment)
        val enCount = countEnglishChars(segment)
        return when {
            bnCount == 0 && enCount == 0 -> fallback
            bnCount == 0 -> SpeechLanguage.ENGLISH
            enCount == 0 -> SpeechLanguage.BENGALI
            bnCount >= enCount -> SpeechLanguage.BENGALI
            else -> SpeechLanguage.ENGLISH
        }
    }

    fun detectDominantLanguage(text: String): SpeechLanguage {
        val bnCount = countBengaliChars(text)
        val enCount = countEnglishChars(text)
        return if (bnCount == 0 && enCount > 0) {
            SpeechLanguage.ENGLISH
        } else if (enCount > bnCount * 2 && bnCount < 10) {
            SpeechLanguage.ENGLISH
        } else if (enCount > bnCount) {
            SpeechLanguage.ENGLISH
        } else {
            SpeechLanguage.BENGALI
        }
    }

    private val decorativeSpeechSymbolsRegex = Regex("[•▪●◆■►✓✔|_~]+")
    private val inlineOpeningBracketChars = setOf('(', '[', '{', '"', '\'', '‘', '“')

    /**
     * Strips decorative bullets/lines that TTS engines pronounce awkwardly while preserving
     * natural reading punctuation.
     */
    fun cleanTextForSpeech(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(decorativeSpeechSymbolsRegex, " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }

    /**
     * Splits page text into ordered SpeechChunks grouped by sentence and script transitions.
     * Adjacent same-language clauses are merged cleanly up to [maxChunkChars].
     */
    fun splitIntoLanguageChunks(text: String, maxChunkChars: Int = 340): List<SpeechChunk> {
        val normalized = normalizeText(text)
        if (!hasReadableText(normalized)) return emptyList()

        val dominant = detectDominantLanguage(normalized)
        val sentenceUnits = splitSentences(normalized)
        val rawTypedSegments = mutableListOf<Pair<SpeechLanguage, String>>()

        for (sentence in sentenceUnits) {
            val scriptPieces = splitMixedScriptRuns(sentence, dominant)
            rawTypedSegments.addAll(scriptPieces)
        }

        if (rawTypedSegments.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<SpeechLanguage, String>>()
        for ((lang, segText) in rawTypedSegments) {
            val trimmed = cleanTextForSpeech(segText)
            if (trimmed.isEmpty()) continue
            if (!trimmed.any { isBengaliChar(it) || isEnglishLetter(it) || it.isDigit() }) continue

            val prev = merged.lastOrNull()
            if (prev != null && prev.first == lang && prev.second.length + 1 + trimmed.length <= maxChunkChars) {
                merged[merged.lastIndex] = lang to "${prev.second} $trimmed"
            } else if (trimmed.length > maxChunkChars) {
                for (part in splitLongText(trimmed, maxChunkChars)) {
                    merged.add(lang to part)
                }
            } else {
                merged.add(lang to trimmed)
            }
        }

        return merged.mapIndexed { idx, (lang, txt) ->
            SpeechChunk(index = idx, text = txt, language = lang)
        }
    }

    private fun splitSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (i in text.indices) {
            val ch = text[i]
            sb.append(ch)
            val isTerminator = ch == '।' || ch == '?' || ch == '!' || ch == '\n' ||
                (ch == '.' && (i == text.lastIndex || text[i + 1].isWhitespace()))
            if (isTerminator) {
                val built = sb.toString().trim()
                if (built.isNotEmpty()) out.add(built)
                sb.setLength(0)
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    /**
     * Splits inline script transitions inside a single token (e.g. "সালোকসংশ্লেষণ(Photosynthesis)"
     * or "PDF-টি") so Bengali and English words without spaces are separated cleanly.
     */
    internal fun tokenizeScriptBoundaries(sentence: String): List<String> {
        val whitespaceTokens = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (whitespaceTokens.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        for (token in whitespaceTokens) {
            val hasBn = token.any(::isBengaliChar)
            val hasEn = token.any(::isEnglishLetter)
            if (!hasBn || !hasEn) {
                result.add(token)
                continue
            }

            val current = StringBuilder()
            var activeScript: SpeechLanguage? = null
            for (ch in token) {
                val chScript = when {
                    isBengaliChar(ch) -> SpeechLanguage.BENGALI
                    isEnglishLetter(ch) -> SpeechLanguage.ENGLISH
                    else -> null
                }
                if (chScript != null && activeScript != null && chScript != activeScript && current.isNotEmpty()) {
                    // Carry trailing opening brackets (e.g., '(' before an English term) into the next piece
                    var splitIndex = current.length
                    while (splitIndex > 0 && current[splitIndex - 1] in inlineOpeningBracketChars) {
                        splitIndex--
                    }
                    val head = current.substring(0, splitIndex).trim()
                    val carriedPrefix = current.substring(splitIndex)
                    if (head.isNotEmpty()) {
                        result.add(head)
                    }
                    current.setLength(0)
                    current.append(carriedPrefix)
                }
                current.append(ch)
                if (chScript != null) {
                    activeScript = chScript
                }
            }
            val tail = current.toString().trim()
            if (tail.isNotEmpty()) {
                result.add(tail)
            }
        }
        return result
    }

    /**
     * Separates Bengali and English spans inside the same sentence when each span carries real words,
     * so mixed pages (e.g. Bangla question + English technical term/paragraph) switch voice smoothly.
     */
    private fun splitMixedScriptRuns(sentence: String, fallback: SpeechLanguage): List<Pair<SpeechLanguage, String>> {
        val bnTotal = countBengaliChars(sentence)
        val enTotal = countEnglishChars(sentence)
        if (bnTotal == 0) return listOf(SpeechLanguage.ENGLISH to sentence)
        if (enTotal == 0) return listOf(SpeechLanguage.BENGALI to sentence)
        if (enTotal < 2) return listOf(SpeechLanguage.BENGALI to sentence)
        if (bnTotal < 2) return listOf(SpeechLanguage.ENGLISH to sentence)

        val tokens = tokenizeScriptBoundaries(sentence)
        if (tokens.isEmpty()) return listOf(fallback to sentence)

        val grouped = mutableListOf<Pair<SpeechLanguage, StringBuilder>>()
        var currentLang = detectSegmentLanguage(tokens.first(), fallback)
        grouped.add(currentLang to StringBuilder(tokens.first()))

        for (token in tokens.drop(1)) {
            val bn = countBengaliChars(token)
            val en = countEnglishChars(token)
            val tokenLang = when {
                bn > 0 && en == 0 -> SpeechLanguage.BENGALI
                en > 0 && bn == 0 -> SpeechLanguage.ENGLISH
                bn > 0 && en > 0 -> detectSegmentLanguage(token, currentLang)
                else -> currentLang
            }
            if (tokenLang == currentLang) {
                grouped.last().second.append(' ').append(token)
            } else {
                currentLang = tokenLang
                grouped.add(currentLang to StringBuilder(token))
            }
        }

        // Collapse single-character stray letters into neighbor while keeping >= 2-letter acronyms (PDF, AI, IT, CPU)
        val finalized = mutableListOf<Pair<SpeechLanguage, String>>()
        for ((lang, sb) in grouped) {
            val txt = sb.toString().trim()
            if (txt.isEmpty()) continue
            val enLetters = countEnglishChars(txt)
            val bnLetters = countBengaliChars(txt)
            val effectiveLang = if (lang == SpeechLanguage.ENGLISH && enLetters < 2 && finalized.isNotEmpty()) {
                finalized.last().first
            } else if (lang == SpeechLanguage.BENGALI && bnLetters < 2 && finalized.isNotEmpty()) {
                finalized.last().first
            } else {
                lang
            }
            if (finalized.isNotEmpty() && finalized.last().first == effectiveLang) {
                finalized[finalized.lastIndex] = effectiveLang to "${finalized.last().second} $txt"
            } else {
                finalized.add(effectiveLang to txt)
            }
        }
        return finalized
    }

    private fun splitLongText(text: String, maxChunkChars: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > maxChunkChars) {
                result.add(current.toString().trim())
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }
}

private object PdfTextExtractorBridge {
    @Volatile
    private var pdfBoxInitialized = false

    fun ensurePdfBoxInit(context: Context) {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    suspend fun extractCurrentPageText(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        pageBitmap: Bitmap?
    ): PageTextExtractionResult {
        val rawPdfText = withContext(Dispatchers.IO) {
            runCatching {
                ensurePdfBoxInit(context)
                PDDocument.load(pdfFile).use { document ->
                    val oneBased = (pageIndex + 1).coerceIn(1, document.numberOfPages.coerceAtLeast(1))
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                        startPage = oneBased
                        endPage = oneBased
                    }
                    stripper.getText(document).orEmpty()
                }
            }.getOrElse { "" }
        }

        val normalizedPdfText = PdfPageTextProcessor.normalizeText(rawPdfText)
        if (PdfPageTextProcessor.looksLikeLegacyBijoyMojibake(rawPdfText, normalizedPdfText)) {
            return PageTextExtractionResult.Error(
                messageBn = "এই পৃষ্ঠাটি পুরোনো বিজয় (SutonnyMJ) ফন্টে তৈরি হওয়ায় অডিও হিসেবে পড়া যাচ্ছে না। ইউনিকোড বাংলা বা ইংরেজি পৃষ্ঠা ব্যবহার করুন।",
                messageEn = "This PDF page uses legacy non-Unicode Bijoy font encoding and cannot be read aloud cleanly."
            )
        }

        if (PdfPageTextProcessor.hasReadableText(normalizedPdfText)) {
            val chunks = PdfPageTextProcessor.splitIntoLanguageChunks(normalizedPdfText)
            if (chunks.isNotEmpty()) {
                return PageTextExtractionResult.Success(
                    chunks = chunks,
                    primaryLanguage = PdfPageTextProcessor.detectDominantLanguage(normalizedPdfText)
                )
            }
        }

        // Fallback to real on-device ML Kit OCR for scanned English/Latin pages on the rendered bitmap
        if (pageBitmap != null && !pageBitmap.isRecycled) {
            val ocrText = runCatching {
                withContext(Dispatchers.Default) {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        val image = InputImage.fromBitmap(pageBitmap, 0)
                        val visionText = recognizer.process(image).await()
                        val lines = visionText.textBlocks.flatMap { it.lines }
                        val sortedLines = lines.sortedWith { a, b ->
                            val topA = a.boundingBox?.top ?: 0
                            val topB = b.boundingBox?.top ?: 0
                            if (abs(topA - topB) <= 18) {
                                (a.boundingBox?.left ?: 0).compareTo(b.boundingBox?.left ?: 0)
                            } else {
                                topA.compareTo(topB)
                            }
                        }
                        sortedLines.joinToString("\n") { it.text }
                    } finally {
                        recognizer.close()
                    }
                }
            }.getOrElse { "" }

            val normalizedOcr = PdfPageTextProcessor.normalizeText(ocrText)
            if (PdfPageTextProcessor.hasReadableText(normalizedOcr)) {
                val chunks = PdfPageTextProcessor.splitIntoLanguageChunks(normalizedOcr)
                if (chunks.isNotEmpty()) {
                    return PageTextExtractionResult.Success(
                        chunks = chunks,
                        primaryLanguage = PdfPageTextProcessor.detectDominantLanguage(normalizedOcr)
                    )
                }
            }
        }

        return PageTextExtractionResult.Error(
            messageBn = "এই পৃষ্ঠায় কোনো পাঠযোগ্য ডিজিটাল টেক্সট পাওয়া যায়নি। পৃষ্ঠাটি স্ক্যান করা ছবি বা অ-ইউনিকোড ফরম্যাটে থাকতে পারে।",
            messageEn = "No readable digital text found on this page. It may be a scanned image or non-Unicode format."
        )
    }
}

class PdfPageAudioController(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PageAudioUiState())
    val state: StateFlow<PageAudioUiState> = _state.asStateFlow()

    private val generationCounter = AtomicInteger(0)
    private var extractJob: Job? = null
    private var loadedChunks: List<SpeechChunk> = emptyList()
    private var loadedPageIndex: Int = -1
    private var lastIsBn: Boolean = true
    private var lastConfiguredLanguage: SpeechLanguage? = null
    private val ttsInitDeferred = CompletableDeferred<Boolean>()

    @Volatile
    private var ttsReady = false

    @Volatile
    private var ttsInitError = false

    private var tts: TextToSpeech? = null

    init {
        runCatching {
            var engine: TextToSpeech? = null
            engine = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    ttsInitError = false
                    lastConfiguredLanguage = null
                    runCatching {
                        engine?.setPitch(1.0f)
                        engine?.setSpeechRate(0.96f)
                    }
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post { handleUtteranceDone(utteranceId) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { handleUtteranceError(utteranceId) }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            mainHandler.post { handleUtteranceError(utteranceId) }
                        }
                    })
                    ttsInitDeferred.complete(true)
                } else {
                    ttsInitError = true
                    ttsInitDeferred.complete(false)
                }
            }
            tts = engine
        }.onFailure {
            ttsInitError = true
            ttsInitDeferred.complete(false)
        }
    }

    fun playOrPauseToggle(
        pdfFile: File?,
        pageIndex: Int,
        pageBitmap: Bitmap?,
        isBn: Boolean,
        scope: CoroutineScope
    ) {
        lastIsBn = isBn
        when (_state.value.phase) {
            PageAudioPhase.PLAYING -> {
                if (_state.value.activePageIndex == pageIndex) {
                    pause()
                } else {
                    startPageExtractionAndPlay(pdfFile, pageIndex, pageBitmap, isBn, scope)
                }
            }

            PageAudioPhase.PAUSED -> {
                if (_state.value.activePageIndex == pageIndex && loadedPageIndex == pageIndex && loadedChunks.isNotEmpty()) {
                    resume()
                } else {
                    startPageExtractionAndPlay(pdfFile, pageIndex, pageBitmap, isBn, scope)
                }
            }

            PageAudioPhase.EXTRACTING -> {
                // Single active lock: ignore duplicate tap while extracting current page
                if (_state.value.activePageIndex != pageIndex) {
                    startPageExtractionAndPlay(pdfFile, pageIndex, pageBitmap, isBn, scope)
                }
            }

            PageAudioPhase.IDLE, PageAudioPhase.ERROR -> {
                startPageExtractionAndPlay(pdfFile, pageIndex, pageBitmap, isBn, scope)
            }
        }
    }

    fun pause() {
        val current = _state.value
        if (current.phase != PageAudioPhase.PLAYING) return
        generationCounter.incrementAndGet()
        lastConfiguredLanguage = null
        runCatching { tts?.stop() }
        _state.value = current.copy(phase = PageAudioPhase.PAUSED)
    }

    fun resume() {
        val current = _state.value
        if (current.phase != PageAudioPhase.PAUSED || loadedChunks.isEmpty()) return
        val resumeIndex = current.currentChunkIndex.coerceIn(0, loadedChunks.lastIndex)
        lastConfiguredLanguage = null
        val gen = generationCounter.incrementAndGet()
        speakChunkAt(gen, resumeIndex)
    }

    fun stop() {
        generationCounter.incrementAndGet()
        extractJob?.cancel()
        extractJob = null
        loadedChunks = emptyList()
        loadedPageIndex = -1
        lastConfiguredLanguage = null
        runCatching { tts?.stop() }
        _state.value = PageAudioUiState()
    }

    fun onPageChanged() {
        stop()
    }

    fun dismissError() {
        if (_state.value.phase == PageAudioPhase.ERROR) {
            _state.value = PageAudioUiState()
        }
    }

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun startPageExtractionAndPlay(
        pdfFile: File?,
        pageIndex: Int,
        pageBitmap: Bitmap?,
        isBn: Boolean,
        scope: CoroutineScope
    ) {
        if (pdfFile == null || !pdfFile.exists()) {
            _state.value = PageAudioUiState(
                phase = PageAudioPhase.ERROR,
                activePageIndex = pageIndex,
                errorMessage = if (isBn) "পিডিএফ ফাইল এখনো প্রস্তুত নয়।" else "PDF file is not ready yet."
            )
            return
        }

        if (ttsInitError) {
            _state.value = PageAudioUiState(
                phase = PageAudioPhase.ERROR,
                activePageIndex = pageIndex,
                errorMessage = if (isBn) {
                    "ডিভাইসে টেক্সট-টু-স্পিচ (TTS) ইঞ্জিন চালু করা যায়নি।"
                } else {
                    "Unable to initialize Text-to-Speech engine on this device."
                }
            )
            return
        }

        val gen = generationCounter.incrementAndGet()
        extractJob?.cancel()
        lastConfiguredLanguage = null
        runCatching { tts?.stop() }

        _state.value = PageAudioUiState(
            phase = PageAudioPhase.EXTRACTING,
            activePageIndex = pageIndex,
            errorMessage = null
        )

        extractJob = scope.launch {
            try {
                val result = PdfTextExtractorBridge.extractCurrentPageText(
                    context = appContext,
                    pdfFile = pdfFile,
                    pageIndex = pageIndex,
                    pageBitmap = pageBitmap
                )
                if (generationCounter.get() != gen) return@launch

                if (!ttsReady && !ttsInitError) {
                    withTimeoutOrNull(3500L) {
                        ttsInitDeferred.await()
                    }
                }
                if (generationCounter.get() != gen) return@launch

                withContext(Dispatchers.Main.immediate) {
                    if (generationCounter.get() != gen) return@withContext
                    when (result) {
                        is PageTextExtractionResult.Error -> {
                            loadedChunks = emptyList()
                            loadedPageIndex = -1
                            _state.value = PageAudioUiState(
                                phase = PageAudioPhase.ERROR,
                                activePageIndex = pageIndex,
                                errorMessage = if (isBn) result.messageBn else result.messageEn
                            )
                        }

                        is PageTextExtractionResult.Success -> {
                            loadedChunks = result.chunks
                            loadedPageIndex = pageIndex
                            speakChunkAt(gen, 0)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (generationCounter.get() == gen) {
                    _state.value = PageAudioUiState(
                        phase = PageAudioPhase.ERROR,
                        activePageIndex = pageIndex,
                        errorMessage = if (isBn) {
                            "পৃষ্ঠা বিশ্লেষণ ব্যর্থ: ${e.localizedMessage ?: "অজানা ত্রুটি"}"
                        } else {
                            "Page analysis failed: ${e.localizedMessage ?: "Unknown error"}"
                        }
                    )
                }
            }
        }
    }

    private fun speakChunkAt(gen: Int, chunkIndex: Int) {
        if (generationCounter.get() != gen) return
        val engine = tts
        if (engine == null || !ttsReady || ttsInitError) {
            _state.value = PageAudioUiState(
                phase = PageAudioPhase.ERROR,
                activePageIndex = loadedPageIndex,
                errorMessage = if (lastIsBn) {
                    "অডিও প্লেব্যাক ইঞ্জিন এখনো প্রস্তুত নয় বা ডিভাইসে TTS অনুপস্থিত।"
                } else {
                    "TTS engine is not ready or unavailable on this device."
                }
            )
            return
        }

        if (chunkIndex !in loadedChunks.indices) {
            _state.value = PageAudioUiState()
            return
        }

        val chunk = loadedChunks[chunkIndex]
        val langConfigured = configureVoiceLanguage(engine, chunk.language)
        if (!langConfigured) {
            lastConfiguredLanguage = null
            _state.value = PageAudioUiState(
                phase = PageAudioPhase.ERROR,
                currentChunkIndex = chunkIndex,
                totalChunks = loadedChunks.size,
                activeLanguage = chunk.language,
                activePageIndex = loadedPageIndex,
                errorMessage = if (chunk.language == SpeechLanguage.BENGALI) {
                    if (lastIsBn) {
                        "ডিভাইসে বাংলা (bn-BD / bn-IN) TTS ভয়েস ইনস্টল বা সক্রিয় নেই। ফোনের Settings -> Text-to-speech থেকে বাংলা ভয়েস ডাউনলোড ও সক্রিয় করুন।"
                    } else {
                        "Bengali TTS voice pack (bn-BD / bn-IN) is not installed on this device. Download and enable Bengali in Text-to-speech settings."
                    }
                } else {
                    if (lastIsBn) {
                        "ডিভাইসে ইংরেজি TTS ভয়েস ইনস্টল বা সক্রিয় নেই। ফোনের Text-to-speech সেটিংস থেকে ইংরেজি ভয়েস সক্রিয় করুন।"
                    } else {
                        "English TTS voice pack is not installed on this device. Enable an English voice in Text-to-speech settings."
                    }
                }
            )
            return
        }

        _state.value = PageAudioUiState(
            phase = PageAudioPhase.PLAYING,
            currentChunkIndex = chunkIndex,
            totalChunks = loadedChunks.size,
            activeLanguage = chunk.language,
            errorMessage = null,
            activePageIndex = loadedPageIndex
        )

        val utteranceId = "${gen}_${chunkIndex}"
        val params = Bundle()
        val resultCode = runCatching {
            engine.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrElse { TextToSpeech.ERROR }

        if (resultCode != TextToSpeech.SUCCESS && generationCounter.get() == gen) {
            _state.value = PageAudioUiState(
                phase = PageAudioPhase.ERROR,
                currentChunkIndex = chunkIndex,
                totalChunks = loadedChunks.size,
                activeLanguage = chunk.language,
                activePageIndex = loadedPageIndex,
                errorMessage = if (lastIsBn) "অডিও প্লে শুরু করা যায়নি।" else "Failed to start audio playback."
            )
        }
    }

    private fun configureVoiceLanguage(engine: TextToSpeech, language: SpeechLanguage): Boolean {
        val targetLangCode = when (language) {
            SpeechLanguage.BENGALI -> "bn"
            SpeechLanguage.ENGLISH -> "en"
        }

        if (lastConfiguredLanguage == language && verifyActiveVoiceMatches(engine, targetLangCode)) {
            return true
        }

        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        if (voices.isNotEmpty()) {
            val rankedVoices = voices
                .asSequence()
                .filter { voice ->
                    val vLang = voice.locale?.language.orEmpty()
                    val features = runCatching { voice.features }.getOrNull().orEmpty()
                    vLang.equals(targetLangCode, ignoreCase = true) &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features
                }
                .sortedByDescending { voice -> scoreVoiceCandidate(voice, language) }
                .toList()

            for (voice in rankedVoices) {
                val langStatus = runCatching { engine.setLanguage(voice.locale) }.getOrElse { TextToSpeech.LANG_NOT_SUPPORTED }
                if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    continue
                }
                val setVoiceRes = runCatching { engine.setVoice(voice) }.getOrElse { TextToSpeech.ERROR }
                if (setVoiceRes != TextToSpeech.SUCCESS) {
                    continue
                }
                if (!verifyActiveVoiceMatches(engine, targetLangCode)) {
                    continue
                }
                applyNaturalVoiceCadence(engine, language)
                lastConfiguredLanguage = language
                return true
            }
        }

        val candidates = when (language) {
            SpeechLanguage.BENGALI -> listOf(
                Locale.forLanguageTag("bn-BD"),
                Locale.forLanguageTag("bn-IN"),
                Locale.forLanguageTag("bn")
            )

            SpeechLanguage.ENGLISH -> listOf(
                Locale.US,
                Locale.UK,
                Locale.ENGLISH
            )
        }
        for (locale in candidates) {
            val status = runCatching { engine.setLanguage(locale) }.getOrElse { TextToSpeech.LANG_NOT_SUPPORTED }
            if (status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED) {
                if (!verifyActiveVoiceMatches(engine, targetLangCode)) {
                    continue
                }
                applyNaturalVoiceCadence(engine, language)
                lastConfiguredLanguage = language
                return true
            }
        }
        return false
    }

    private fun verifyActiveVoiceMatches(engine: TextToSpeech, targetLangCode: String): Boolean {
        val activeVoice = runCatching { engine.voice }.getOrNull() ?: return true
        val features = runCatching { activeVoice.features }.getOrNull().orEmpty()
        if (TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features) {
            return false
        }
        val activeLang = activeVoice.locale?.language.orEmpty()
        return activeLang.isBlank() || activeLang.equals(targetLangCode, ignoreCase = true)
    }

    private fun scoreVoiceCandidate(voice: Voice, language: SpeechLanguage): Int {
        val country = voice.locale?.country.orEmpty().uppercase(Locale.US)
        val regionScore = when (language) {
            SpeechLanguage.BENGALI -> when (country) {
                "BD" -> 300
                "IN" -> 200
                else -> 100
            }

            SpeechLanguage.ENGLISH -> when (country) {
                "US" -> 300
                "GB" -> 260
                "AU", "IN" -> 200
                else -> 100
            }
        }
        val offlineBonus = if (!voice.isNetworkConnectionRequired) 50 else 0
        val qualityBonus = (voice.quality / 10).coerceIn(0, 60)
        return regionScore + offlineBonus + qualityBonus
    }

    private fun applyNaturalVoiceCadence(engine: TextToSpeech, language: SpeechLanguage) {
        runCatching {
            engine.setPitch(1.0f)
            engine.setSpeechRate(
                when (language) {
                    SpeechLanguage.BENGALI -> 0.94f
                    SpeechLanguage.ENGLISH -> 0.98f
                }
            )
        }
    }

    private fun handleUtteranceDone(utteranceId: String?) {
        val parts = utteranceId?.split('_') ?: return
        val gen = parts.getOrNull(0)?.toIntOrNull() ?: return
        val finishedIdx = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (generationCounter.get() != gen) return

        val nextIndex = finishedIdx + 1
        if (nextIndex < loadedChunks.size) {
            speakChunkAt(gen, nextIndex)
        } else {
            loadedChunks = emptyList()
            loadedPageIndex = -1
            lastConfiguredLanguage = null
            _state.value = PageAudioUiState()
        }
    }

    private fun handleUtteranceError(utteranceId: String?) {
        val parts = utteranceId?.split('_') ?: return
        val gen = parts.getOrNull(0)?.toIntOrNull() ?: return
        if (generationCounter.get() != gen) return
        lastConfiguredLanguage = null
        _state.value = _state.value.copy(
            phase = PageAudioPhase.ERROR,
            errorMessage = if (lastIsBn) "অডিও পড়ার সময় ত্রুটি হয়েছে।" else "Audio playback interrupted."
        )
    }
}
