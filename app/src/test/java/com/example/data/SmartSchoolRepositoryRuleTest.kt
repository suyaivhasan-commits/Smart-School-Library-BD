package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.firebase.SecurityConstants
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.Textbook
import com.example.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSchoolRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun studentNoteAndBookmark_privateToOwner_crossUserDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val studyRepoAlice = StudyRepository(firestore, auth)
        val libraryRepoAlice = LibraryRepository(firestore, auth)

        val noteResult = withTimeout(5000L) {
            studyRepoAlice.createNote(
                title = "বীজগণিত সূত্রাবলি",
                content = "(a + b)^2 = a^2 + 2ab + b^2",
                subjectTag = "গণিত",
                colorIndex = 1,
                isPinned = true
            )
        }
        assertTrue("Expected Alice note creation to succeed", noteResult.isSuccess)
        val noteId = noteResult.getOrThrow()

        val notesEmitted = withTimeout(3000L) {
            studyRepoAlice.observeUserNotes(aliceUid).first { list -> list.any { it.id == noteId } }
        }
        assertTrue(notesEmitted.any { it.id == noteId })

        val bmResult = withTimeout(5000L) {
            libraryRepoAlice.toggleBookmark(
                bookmarkType = "PAGE",
                targetId = "bangla_class5",
                titleBn = "আমার বাংলা বই",
                classNumber = 5,
                pageNumber = 14,
                isPremium = false
            )
        }
        assertTrue(bmResult.isSuccess && bmResult.getOrThrow())

        // Cross-user Bob must be denied reading Alice's private note
        signInTestUser("bob_${UUID.randomUUID().toString().take(6)}@test.com")
        val studyRepoBob = StudyRepository(firestore, auth)
        val crossRead = withTimeout(5000L) {
            studyRepoBob.getNoteById(noteId)
        }
        assertTrue("Cross-user note access must fail", crossRead.isFailure)
        val fsEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun unauthenticatedCaller_rejectedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val libraryRepo = LibraryRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(3000L) {
                libraryRepo.observePublishedBooks().first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull("Expected unauthenticated query to throw", caught)
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun normalStudent_cannotWriteBooks_andSubmitsPendingSubscriptionOnly() = runBlocking {
        val studentUid = signInTestUser("student_${UUID.randomUUID().toString().take(6)}@test.com")
        val authRepo = AuthRepository(firestore, auth)
        val profileRes = withTimeout(5000L) {
            authRepo.ensureUserProfile(checkNotNull(auth.currentUser), fallbackName = "তানভীর আহমেদ", initialClass = 9, initialStream = "SCIENCE")
        }
        assertTrue(profileRes.isSuccess)
        assertEquals("student", profileRes.getOrThrow().role)

        // Student attempting to write directly to /books must fail at Firestore Rules level
        var bookWriteError: Throwable? = null
        try {
            withTimeout(5000L) {
                val bookMap = Textbook(
                    id = "unauth_book",
                    titleBn = "অননুমোদিত বই",
                    subjectId = "s1",
                    subjectNameBn = "বাংলা",
                    classNumber = 5,
                    pdfUrl = "https://example.org/book.pdf"
                ).toWriteMap(isCreate = true)
                firestore.collection("books").document("unauth_book_${UUID.randomUUID().toString().take(6)}")
                    .set(bookMap)
                    .await()
            }
        } catch (t: Throwable) {
            bookWriteError = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            bookWriteError?.unwrapFirestoreException()?.code
        )

        // Seed active SubscriptionPlan in Firestore emulator so backend price verification succeeds
        val planId = "plan_c9_sci_6m_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        withTimeout(5000L) {
            seedEmulatorDocumentAsAdmin(
                "subscription_plans",
                planId,
                """
                {
                  "fields": {
                    "nameBn": { "stringValue": "নবম শ্রেণি (বিজ্ঞান) ৬ মাস" },
                    "classNumber": { "integerValue": "9" },
                    "stream": { "stringValue": "SCIENCE" },
                    "durationMonths": { "integerValue": "6" },
                    "priceBdt": { "integerValue": "120" },
                    "isActive": { "booleanValue": true },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
        }

        // Student submitting manual payment request -> status is PENDING
        val studyRepo = StudyRepository(firestore, auth)
        val samplePlan = SubscriptionPlan(
            id = planId,
            nameBn = "নবম শ্রেণি (বিজ্ঞান) ৬ মাস",
            classNumber = 9,
            stream = "SCIENCE",
            durationMonths = 6,
            priceBdt = 120
        )
        val uniqueTrx = "BKX${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val reqRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "তানভীর আহমেদ",
                userEmail = "tanvir@test.com",
                plan = samplePlan,
                paymentMethod = "bKash",
                transactionReference = uniqueTrx,
                senderNumber = "01700000000"
            )
        }
        assertTrue("Expected pending payment request submission to succeed: ${reqRes.exceptionOrNull()}", reqRes.isSuccess)

        val requests = withTimeout(3000L) {
            studyRepo.observeUserSubscriptionRequests(studentUid).first { it.isNotEmpty() }
        }
        assertEquals("PENDING", requests.first().status)
        Unit
    }

    @Test
    fun paymentMethods_duplicateTrxLock_andPriceVerification_enforcedEndToEnd(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val activeMethodId = "pm_bkash_$suffix"
        val disabledMethodId = "pm_rocket_off_$suffix"
        val planId = "plan_c8_6m_$suffix"

        withTimeout(5000L) {
            seedEmulatorDocumentAsAdmin(
                "payment_methods",
                activeMethodId,
                """
                {
                  "fields": {
                    "methodType": { "stringValue": "BKASH" },
                    "methodName": { "stringValue": "bKash" },
                    "accountNumber": { "stringValue": "01711223344" },
                    "accountHolderName": { "stringValue": "Smart School Library BD" },
                    "accountType": { "stringValue": "Personal" },
                    "transactionType": { "stringValue": "Send Money" },
                    "paymentInstructions": { "stringValue": "উপরের নম্বরে Send Money করুন।" },
                    "isActive": { "booleanValue": true },
                    "displayOrder": { "integerValue": "1" },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
            seedEmulatorDocumentAsAdmin(
                "payment_methods",
                disabledMethodId,
                """
                {
                  "fields": {
                    "methodType": { "stringValue": "ROCKET" },
                    "methodName": { "stringValue": "Rocket" },
                    "accountNumber": { "stringValue": "018998877665" },
                    "accountType": { "stringValue": "Personal" },
                    "transactionType": { "stringValue": "Send Money" },
                    "isActive": { "booleanValue": false },
                    "displayOrder": { "integerValue": "2" },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
            seedEmulatorDocumentAsAdmin(
                "subscription_plans",
                planId,
                """
                {
                  "fields": {
                    "nameBn": { "stringValue": "অষ্টম শ্রেণি প্যাক" },
                    "classNumber": { "integerValue": "8" },
                    "stream": { "stringValue": "ALL" },
                    "durationMonths": { "integerValue": "6" },
                    "priceBdt": { "integerValue": "80" },
                    "isActive": { "booleanValue": true },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
        }

        val studentAUid = signInTestUser("pay_stu_a_$suffix@test.com")
        val studyRepoA = StudyRepository(firestore, auth)

        // 1. Active payment methods query returns active method and hides disabled method
        val activeMethods = withTimeout(5000L) {
            studyRepoA.observeActivePaymentMethods().first { list -> list.any { it.id == activeMethodId } }
        }
        assertTrue(activeMethods.any { it.id == activeMethodId })
        assertTrue("Disabled payment method must NOT appear in student query", activeMethods.none { it.id == disabledMethodId })

        val bkashCfg = activeMethods.first { it.id == activeMethodId }

        // 2. Even if client passes tampered local price (e.g. 5 BDT), repository & rules lock it to server price (80 BDT)
        val tamperedLocalPlan = SubscriptionPlan(
            id = planId,
            nameBn = "অষ্টম শ্রেণি প্যাক",
            classNumber = 8,
            stream = "ALL",
            durationMonths = 6,
            priceBdt = 5
        )
        val uniqueTrx = "TRX${suffix.uppercase()}"
        val submitRes = withTimeout(5000L) {
            studyRepoA.submitSubscriptionPaymentRequest(
                userName = "সাদিয়া ইসলাম",
                userEmail = "sadia@test.com",
                plan = tamperedLocalPlan,
                paymentMethod = bkashCfg.methodName,
                transactionReference = uniqueTrx,
                senderNumber = "01912345678",
                selectedMethodConfig = bkashCfg
            )
        }
        assertTrue("Expected verified payment submission to succeed: ${submitRes.exceptionOrNull()}", submitRes.isSuccess)
        val createdReqId = submitRes.getOrThrow()

        val studentARequests = withTimeout(5000L) {
            studyRepoA.observeUserSubscriptionRequests(studentAUid).first { list -> list.any { it.id == createdReqId } }
        }
        val savedReq = studentARequests.first { it.id == createdReqId }
        assertEquals("PENDING", savedReq.status)
        assertEquals("Server plan price (80 BDT) must override any client-side price", 80, savedReq.amountBdt)
        assertEquals("01711223344", savedReq.receiverNumber)
        assertEquals("Send Money", savedReq.transactionType)

        // 3. Reusing the same Transaction ID (even lowercase) must be rejected
        val duplicateRes = withTimeout(5000L) {
            studyRepoA.submitSubscriptionPaymentRequest(
                userName = "সাদিয়া ইসলাম",
                userEmail = "sadia@test.com",
                plan = tamperedLocalPlan,
                paymentMethod = bkashCfg.methodName,
                transactionReference = uniqueTrx.lowercase(),
                senderNumber = "01912345678",
                selectedMethodConfig = bkashCfg
            )
        }
        assertTrue("Duplicate Transaction ID must be rejected", duplicateRes.isFailure)

        // 4. Cross-user Student B cannot read Student A's payment request
        signInTestUser("pay_stu_b_$suffix@test.com")
        var crossReadErr: Throwable? = null
        try {
            withTimeout(5000L) {
                firestore.collection("subscription_requests").document(createdReqId).get().await()
            }
        } catch (t: Throwable) {
            crossReadErr = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossReadErr?.unwrapFirestoreException()?.code
        )
        Unit
    }

    @Test
    fun premiumEligibilityEngine_enforcesClassStreamAndExpiryCorrectly() {
        val now = 1_800_000_000_000L
        val future = Timestamp(Date(now + 30L * 24 * 3600 * 1000))
        val past = Timestamp(Date(now - 5L * 24 * 3600 * 1000))
        val studentProfile = UserProfile(userId = "stu_1", isActive = true, role = "student")

        val class5ActiveSub = Subscription(
            id = "s5",
            userId = "stu_1",
            classNumber = 5,
            stream = "ALL",
            status = "ACTIVE",
            expiryDate = future
        )
        val class9SciExpiredSub = Subscription(
            id = "s9",
            userId = "stu_1",
            classNumber = 9,
            stream = "SCIENCE",
            status = "ACTIVE",
            expiryDate = past
        )

        val class9SciActiveSub = Subscription(
            id = "s9_sci",
            userId = "stu_1",
            classNumber = 9,
            stream = "SCIENCE",
            status = "ACTIVE",
            expiryDate = future
        )
        val class9HumActiveSub = Subscription(
            id = "s9_hum",
            userId = "stu_2",
            classNumber = 9,
            stream = "HUMANITIES",
            status = "ACTIVE",
            expiryDate = future
        )
        val class10SciActiveSub = Subscription(
            id = "s10_sci",
            userId = "stu_1",
            classNumber = 10,
            stream = "SCIENCE",
            status = "ACTIVE",
            expiryDate = future
        )

        // 1. Class 5 textbook & guide -> ALLOWED with Class 5 sub
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 5,
                contentStream = "ALL",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class5ActiveSub),
                nowMillis = now
            )
        )

        // 2. Class 6 textbook -> DENIED when user only has Class 5 sub (Class Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 6,
                contentStream = "ALL",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class5ActiveSub),
                nowMillis = now
            )
        )

        // 3. Class 9 Science textbook -> ALLOWED for Class 9 Science subscriber
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 4. Class 9 Humanities textbook -> DENIED for Class 9 Science subscriber (Stream Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "HUMANITIES",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 5. Class 9 Business Studies textbook -> DENIED for Class 9 Science subscriber (Stream Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "BUSINESS",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 6. Class 9 Science textbook -> DENIED for Class 9 Humanities subscriber (Stream Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "stu_2",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9HumActiveSub),
                nowMillis = now
            )
        )

        // 7. Class 9 common subject (stream "ALL") -> ALLOWED for any active Class 9 subscriber
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "ALL",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 8. Class 10 Science textbook -> DENIED for Class 9 Science subscriber (Grade Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 10,
                contentStream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 9. Class 9 Science textbook -> DENIED for Class 10 Science subscriber (Grade Mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class10SciActiveSub),
                nowMillis = now
            )
        )

        // 10. Expired Class 9 Science sub -> DENIED_SUBSCRIPTION_EXPIRED
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciExpiredSub),
                nowMillis = now
            )
        )

        // 11a. Class 5-10 content without subscription -> DENIED_SUBSCRIPTION_REQUIRED (Classes 5-10 require class subscription)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = false,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )

        // 11b. Non-class (outside 5..10) free content -> ALLOWED_FREE
        assertEquals(
            PremiumAccessDecision.ALLOWED_FREE,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = false,
                contentClass = 0,
                contentStream = "ALL",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )

        // 12. Unauthenticated user -> DENIED_UNAUTHENTICATED
        assertEquals(
            PremiumAccessDecision.DENIED_UNAUTHENTICATED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = null,
                userProfile = null,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 13. evaluateClassAndStreamAccess helper checks
        assertTrue(
            StudyRepository.evaluateClassAndStreamAccess(
                classNumber = 9,
                stream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )
        assertFalse(
            StudyRepository.evaluateClassAndStreamAccess(
                classNumber = 9,
                stream = "HUMANITIES",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )
        assertFalse(
            StudyRepository.evaluateClassAndStreamAccess(
                classNumber = 10,
                stream = "SCIENCE",
                currentUserUid = "stu_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciActiveSub),
                nowMillis = now
            )
        )

        // 14. Trusted Super Admin UID -> ALLOWED_ADMIN
        assertEquals(
            PremiumAccessDecision.ALLOWED_ADMIN,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 10,
                contentStream = "HUMANITIES",
                currentUserUid = SecurityConstants.TRUSTED_SUPER_ADMIN_UID,
                userProfile = null,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )
    }

    @Test
    fun safeFirestoreMapper_mapsUserNotesContainingExplicitEmbeddedIdField_withoutCrashing(): Unit = runBlocking {
        val uid = signInTestUser("mapper_${UUID.randomUUID().toString().take(6)}@test.com")
        val docId = "note_embedded_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val fieldsJson = """
            {
              "fields": {
                "id": { "stringValue": "legacy_conflicting_id_value" },
                "userId": { "stringValue": "$uid" },
                "title": { "stringValue": "এম্বডেড আইডি নোট" },
                "content": { "stringValue": "পূর্বে সংরক্ষিত id ফিল্ড থাকলেও অ্যাপ ক্র্যাশ করবে না।" },
                "subjectTag": { "stringValue": "বিজ্ঞান" },
                "colorIndex": { "integerValue": "0" },
                "isPinned": { "booleanValue": false },
                "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
              }
            }
        """.trimIndent()

        withTimeout(5000L) {
            seedEmulatorDocumentAsAdmin("user_notes", docId, fieldsJson)
        }

        val studyRepo = StudyRepository(firestore, auth)
        val loaded = withTimeout(5000L) {
            studyRepo.observeUserNotes(uid).first { notes -> notes.any { it.id == docId } }
        }
        val mapped = loaded.first { it.id == docId }
        assertEquals(docId, mapped.id)
        assertEquals("এম্বডেড আইডি নোট", mapped.title)
        Unit
    }

    @Test
    fun directUploadDraft_andLocalUploadValidator_stagePdfAndUseValidCompleteGuideType(): Unit = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val draft = DirectUploadDraft(category = UploadCategory.GUIDES_AND_NOTES)
        assertEquals("COMPLETE_GUIDE", draft.guideType)

        val tempPdf = java.io.File(context.cacheDir, "sample_admin_test.pdf")
        tempPdf.writeBytes("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".toByteArray(Charsets.US_ASCII))
        val validated = LocalUploadValidator.validateSelectedPdf(
            context = context,
            uri = android.net.Uri.fromFile(tempPdf),
            preservedDisplayName = "amar_bangla_class5.pdf"
        )
        assertTrue("Expected staged PDF validation to succeed", validated.isSuccess)
        assertEquals("amar_bangla_class5.pdf", validated.getOrThrow().displayName)
        tempPdf.delete()
        Unit
    }

    @Test
    fun predefinedAnnualPlan_mandatorySender_andDuplicateTrxErrorMessage_endToEnd(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val studentUid = signInTestUser("annual_${suffix.lowercase()}@test.com")
        val studyRepo = StudyRepository(firestore, auth)

        val class5Plan = com.example.model.DefaultCatalogSeed.defaultAnnualPackages.first { it.id == "plan_annual_c5" }

        // 1. Blank sender number must fail cleanly before hitting Firestore
        val missingSenderRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "নাদিয়া আক্তার",
                userEmail = "nadia@test.com",
                plan = class5Plan,
                paymentMethod = "Nagad",
                transactionReference = "NGD$suffix",
                senderNumber = "",
                screenshotUrl = "",
                selectedMethodConfig = null,
                fallbackReceiverNumber = "01711223344"
            )
        }
        assertTrue("Missing sender number must fail", missingSenderRes.isFailure)

        // 2. Submitting predefined Class 5 annual plan with unseeded method & empty optional screenshot -> MUST SUCCEED
        val trx = "NGD$suffix"
        val validRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "নাদিয়া আক্তার",
                userEmail = "nadia@test.com",
                plan = class5Plan,
                paymentMethod = "Nagad",
                transactionReference = trx,
                senderNumber = "01755667788",
                screenshotUrl = "",
                selectedMethodConfig = null,
                fallbackReceiverNumber = "01711223344"
            )
        }
        assertTrue("Expected predefined annual plan submission to succeed: ${validRes.exceptionOrNull()}", validRes.isSuccess)
        val reqId = validRes.getOrThrow()

        val savedRequests = withTimeout(5000L) {
            studyRepo.observeUserSubscriptionRequests(studentUid).first { list -> list.any { it.id == reqId } }
        }
        val savedReq = savedRequests.first { it.id == reqId }
        assertEquals("PENDING", savedReq.status)
        assertEquals(250, savedReq.amountBdt)
        assertEquals("01755667788", savedReq.senderNumber)
        assertEquals("", savedReq.screenshotUrl)

        // 3. Reusing same Transaction ID returns clear Bengali duplicate error (not raw PERMISSION_DENIED)
        val dupRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "নাদিয়া আক্তার",
                userEmail = "nadia@test.com",
                plan = class5Plan,
                paymentMethod = "Nagad",
                transactionReference = trx.lowercase(),
                senderNumber = "01755667788",
                screenshotUrl = "",
                selectedMethodConfig = null,
                fallbackReceiverNumber = "01711223344"
            )
        }
        assertTrue("Duplicate TrxID must fail", dupRes.isFailure)
        val dupMsg = dupRes.exceptionOrNull()?.message.orEmpty()
        assertTrue("Expected clear Bengali duplicate message, got: $dupMsg", dupMsg.contains("ইতিমধ্যে ব্যবহৃত হয়েছে"))
        Unit
    }

    @Test
    fun libraryRepository_observePublishedBooksAndGuides_alignsWithSecurityRules(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val pubBookId = "pub_book_$suffix"
        val draftBookId = "draft_book_$suffix"
        val pubGuideId = "pub_guide_$suffix"

        withTimeout(5000L) {
            seedEmulatorDocumentAsAdmin(
                "books",
                pubBookId,
                """
                {
                  "fields": {
                    "titleBn": { "stringValue": "প্রকাশিত বাংলা বই" },
                    "subjectId": { "stringValue": "subj_5_bn" },
                    "subjectNameBn": { "stringValue": "বাংলা" },
                    "classNumber": { "integerValue": "5" },
                    "stream": { "stringValue": "ALL" },
                    "academicYear": { "stringValue": "2026" },
                    "edition": { "stringValue": "NCTB" },
                    "language": { "stringValue": "BN" },
                    "pageCount": { "integerValue": "120" },
                    "pdfUrl": { "stringValue": "https://example.org/pub_bn.pdf" },
                    "isPremium": { "booleanValue": false },
                    "isPublished": { "booleanValue": true },
                    "allowDownload": { "booleanValue": false },
                    "isPopular": { "booleanValue": false },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
            seedEmulatorDocumentAsAdmin(
                "books",
                draftBookId,
                """
                {
                  "fields": {
                    "titleBn": { "stringValue": "গোপন খসড়া বই" },
                    "subjectId": { "stringValue": "subj_5_bn" },
                    "subjectNameBn": { "stringValue": "বাংলা" },
                    "classNumber": { "integerValue": "5" },
                    "stream": { "stringValue": "ALL" },
                    "academicYear": { "stringValue": "2026" },
                    "edition": { "stringValue": "Draft" },
                    "language": { "stringValue": "BN" },
                    "pageCount": { "integerValue": "50" },
                    "pdfUrl": { "stringValue": "https://example.org/draft_bn.pdf" },
                    "isPremium": { "booleanValue": true },
                    "isPublished": { "booleanValue": false },
                    "allowDownload": { "booleanValue": false },
                    "isPopular": { "booleanValue": false },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
            seedEmulatorDocumentAsAdmin(
                "guides",
                pubGuideId,
                """
                {
                  "fields": {
                    "titleBn": { "stringValue": "প্রকাশিত গণিত গাইড" },
                    "classNumber": { "integerValue": "5" },
                    "stream": { "stringValue": "ALL" },
                    "subjectId": { "stringValue": "subj_5_math" },
                    "subjectNameBn": { "stringValue": "গণিত" },
                    "chapter": { "stringValue": "অধ্যায় ১" },
                    "guideType": { "stringValue": "COMPLETE_GUIDE" },
                    "isPremium": { "booleanValue": true },
                    "isPublished": { "booleanValue": true },
                    "allowDownload": { "booleanValue": false },
                    "createdAt": { "timestampValue": "2026-01-15T10:00:00Z" },
                    "updatedAt": { "timestampValue": "2026-01-15T10:00:00Z" }
                  }
                }
                """.trimIndent()
            )
        }

        signInTestUser("lib_reader_${suffix}@test.com")
        val libraryRepo = LibraryRepository(firestore, auth)

        val publishedBooks = withTimeout(5000L) {
            libraryRepo.observePublishedBooks().first { list -> list.any { it.id == pubBookId } }
        }
        assertTrue(publishedBooks.any { it.id == pubBookId })
        assertFalse("Unpublished draft book must never be returned to student", publishedBooks.any { it.id == draftBookId })

        val publishedGuides = withTimeout(5000L) {
            libraryRepo.observePublishedGuides().first { list -> list.any { it.id == pubGuideId } }
        }
        assertTrue(publishedGuides.any { it.id == pubGuideId })
        Unit
    }

    @Test
    fun superAdmin_approveAndRejectPaymentRequests_enforcesAuthSubscriptionExpiryAndNotifications(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val class9Plan = com.example.model.DefaultCatalogSeed.defaultAnnualPackages.first { it.id == "plan_annual_c9_science" }

        // 1. Student submits 2 requests: one to approve, one to reject
        val studentUid = signInTestUser("sub_flow_${suffix.lowercase()}@test.com")
        val studyRepo = StudyRepository(firestore, auth)

        val trxApprove = "BKAPPROVE$suffix"
        val approveReqRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "ফারহান সাদিক",
                userEmail = "farhan@test.com",
                plan = class9Plan,
                paymentMethod = "bKash",
                transactionReference = trxApprove,
                senderNumber = "01711001100",
                screenshotUrl = "",
                selectedMethodConfig = null,
                fallbackReceiverNumber = "01906112233"
            )
        }
        assertTrue("Expected payment submit #1 to succeed: ${approveReqRes.exceptionOrNull()}", approveReqRes.isSuccess)
        val approveReqId = approveReqRes.getOrThrow()

        val trxReject = "NGREJECT$suffix"
        val rejectReqRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "ফারহান সাদিক",
                userEmail = "farhan@test.com",
                plan = class9Plan,
                paymentMethod = "Nagad",
                transactionReference = trxReject,
                senderNumber = "01822001100",
                screenshotUrl = "",
                selectedMethodConfig = null,
                fallbackReceiverNumber = "01906112233"
            )
        }
        assertTrue("Expected payment submit #2 to succeed: ${rejectReqRes.exceptionOrNull()}", rejectReqRes.isSuccess)
        val rejectReqId = rejectReqRes.getOrThrow()

        val studentReqs = withTimeout(5000L) {
            studyRepo.observeUserSubscriptionRequests(studentUid).first { list ->
                list.any { it.id == approveReqId } && list.any { it.id == rejectReqId }
            }
        }
        val reqToApprove = studentReqs.first { it.id == approveReqId }
        val reqToReject = studentReqs.first { it.id == rejectReqId }

        // 2. Normal student calling AdminRepository approve/reject must fail at repository guard AND Firestore Rules guard
        val studentAdminRepo = AdminRepository(firestore, auth)
        var studentAdminAttemptFailed = false
        try {
            studentAdminRepo.approveSubscriptionRequest(reqToApprove)
        } catch (_: IllegalStateException) {
            studentAdminAttemptFailed = true
        }
        assertTrue("Normal student must be blocked from AdminRepository.approveSubscriptionRequest", studentAdminAttemptFailed)

        var directApproveRuleDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("subscription_requests").document(approveReqId)
                    .update("status", "APPROVED")
                    .await()
            }
        } catch (t: Throwable) {
            directApproveRuleDenied = t.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Firestore Rules must deny direct student approval", directApproveRuleDenied)

        // 3. Super Admin signs in and sees real-time payment notifications
        val adminUid = signInSuperAdmin()
        assertEquals(SecurityConstants.TRUSTED_SUPER_ADMIN_UID, adminUid)
        val adminRepo = AdminRepository(firestore, auth)

        val adminNotifs = withTimeout(5000L) {
            adminRepo.observeAdminNotifications().first { list ->
                list.any { it.relatedPaymentRequestId == approveReqId }
            }
        }
        val relatedNotifIds = adminNotifs.filter { it.relatedPaymentRequestId == approveReqId }.map { it.id }
        assertTrue("Admin notification should be created for new payment request", relatedNotifIds.isNotEmpty())

        // 4. Super Admin approves request #1 -> Subscription becomes ACTIVE until Dec 31 (Asia/Dhaka) & lock becomes APPROVED
        val approveRes = withTimeout(5000L) {
            adminRepo.approveSubscriptionRequest(
                request = reqToApprove,
                adminNote = "যাচাইকৃত ও অনুমোদিত",
                relatedAdminNotificationIds = relatedNotifIds
            )
        }
        assertTrue("Expected Super Admin approve to succeed: ${approveRes.exceptionOrNull()}", approveRes.isSuccess)

        val lockAfterApprove = withTimeout(5000L) {
            firestore.collection("used_transaction_ids").document(reqToApprove.normalizedTrxId).get().await()
        }
        assertEquals("APPROVED", lockAfterApprove.getString("status"))

        // 5. Super Admin rejects request #2 -> status REJECTED & RESERVED TrxID lock released
        val rejectRes = withTimeout(5000L) {
            adminRepo.rejectSubscriptionRequest(
                request = reqToReject,
                rejectionReason = "ভুল ট্রানজেকশন আইডি"
            )
        }
        assertTrue("Expected Super Admin reject to succeed: ${rejectRes.exceptionOrNull()}", rejectRes.isSuccess)

        val lockAfterReject = withTimeout(5000L) {
            firestore.collection("used_transaction_ids").document(reqToReject.normalizedTrxId).get().await()
        }
        assertFalse("Rejected request lock should be released", lockAfterReject.exists())

        // 6. Switch back to student and verify Active Subscription, Dec 31 expiry, & notifications
        signInTestUser("sub_flow_${suffix.lowercase()}@test.com")
        val studentSubs = withTimeout(5000L) {
            studyRepo.observeUserSubscriptions(studentUid).first { list -> list.any { it.requestId == approveReqId } }
        }
        val activeSub = studentSubs.first { it.requestId == approveReqId }
        assertEquals("ACTIVE", activeSub.status)
        assertEquals(9, activeSub.classNumber)
        assertEquals("SCIENCE", activeSub.stream)
        assertEquals(SecurityConstants.TRUSTED_SUPER_ADMIN_UID, activeSub.approvedByUid)
        assertNotNull(activeSub.expiryDate)

        val dhakaCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Dhaka")).apply {
            time = activeSub.expiryDate!!.toDate()
        }
        assertEquals(java.util.Calendar.DECEMBER, dhakaCal.get(java.util.Calendar.MONTH))
        assertEquals(31, dhakaCal.get(java.util.Calendar.DAY_OF_MONTH))

        val eligibility = StudyRepository.checkPremiumEligibility(
            isContentPremium = true,
            contentClass = 9,
            contentStream = "SCIENCE",
            currentUserUid = studentUid,
            userProfile = UserProfile(userId = studentUid, isActive = true, role = "student"),
            userSubscriptions = studentSubs,
            nowMillis = System.currentTimeMillis()
        )
        assertEquals(PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION, eligibility)

        val studentNotifs = withTimeout(5000L) {
            studyRepo.observeUserNotifications(studentUid).first { list ->
                list.any { it.notificationType == "PAYMENT_APPROVED" } &&
                    list.any { it.notificationType == "PAYMENT_REJECTED" }
            }
        }
        assertTrue(studentNotifs.any { it.relatedPaymentRequestId == approveReqId && it.notificationType == "PAYMENT_APPROVED" })
        assertTrue(studentNotifs.any { it.relatedPaymentRequestId == rejectReqId && it.notificationType == "PAYMENT_REJECTED" })
        Unit
    }

    @Test
    fun emailRegistration_persistsStudentProfileWithoutRace_andBlocksRoleEscalation(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6).lowercase()
        val email = "nafisa_$suffix@test.com"
        val authRepo = AuthRepository(firestore, auth)

        val regRes = withTimeout(5000L) {
            authRepo.registerWithEmail(
                name = "নাফিসা তাসনিম",
                email = email,
                password = "password123",
                selectedClass = 9,
                selectedStream = "SCIENCE"
            )
        }
        assertTrue("Expected email registration to succeed: ${regRes.exceptionOrNull()}", regRes.isSuccess)
        val user = regRes.getOrThrow()

        // Simulate concurrent StudentSessionViewModel.init ensureUserProfile call right after Auth registration
        val ensured = withTimeout(5000L) {
            authRepo.ensureUserProfile(user)
        }
        assertTrue(ensured.isSuccess)
        val profile = ensured.getOrThrow()
        assertEquals("নাফিসা তাসনিম", profile.name)
        assertEquals("student", profile.role)
        assertEquals(9, profile.selectedClass)
        assertEquals("SCIENCE", profile.selectedStream)

        // Student attempting to self-escalate role to super_admin in Firestore must be denied
        var roleEscalationErr: Throwable? = null
        try {
            withTimeout(5000L) {
                firestore.collection("users").document(user.uid)
                    .update("role", "super_admin")
                    .await()
            }
        } catch (t: Throwable) {
            roleEscalationErr = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            roleEscalationErr?.unwrapFirestoreException()?.code
        )
        Unit
    }

    @Test
    fun superAdmin_educationLevels_extendedClasses_andDynamicPlans_workEndToEnd(): Unit = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        signInSuperAdmin()
        val adminRepo = AdminRepository(firestore, auth)

        // Ensure clean app_settings/global state in shared emulator instance
        withTimeout(5000L) {
            firestore.collection("app_settings").document("global")
                .set(com.example.model.AppSettings().toWriteMap())
                .await()
        }

        // 1. Create Education Level (HSC) & Extended Classes (11, 12, 13 saved intentionally out of order)
        val levelRes = withTimeout(5000L) {
            adminRepo.saveEducationLevel(
                com.example.model.EducationLevel(
                    id = "level_hsc",
                    code = "HSC",
                    nameBn = "একাদশ–দ্বাদশ (HSC)",
                    nameEn = "HSC Level",
                    minClassNumber = 11,
                    maxClassNumber = 12,
                    sortOrder = 2,
                    isActive = true
                )
            )
        }
        assertTrue("Expected EducationLevel save to succeed: ${levelRes.exceptionOrNull()}", levelRes.isSuccess)

        listOf(13, 12, 11).forEach { clsNum ->
            val clsRes = withTimeout(5000L) {
                adminRepo.saveSchoolClass(
                    com.example.model.SchoolClass(
                        id = "class_$clsNum",
                        classNumber = clsNum,
                        nameBn = if (clsNum == 11) "একাদশ শ্রেণি" else if (clsNum == 12) "দ্বাদশ শ্রেণি" else "শ্রেণি ১৩",
                        nameEn = "Class $clsNum",
                        hasStreams = false,
                        isActive = true,
                        sortOrder = 30 - clsNum, // reversed sortOrder to verify classNumber serial order wins!
                        educationLevelId = if (clsNum in 11..12) "level_hsc" else "",
                        educationLevelNameBn = if (clsNum in 11..12) "একাদশ–দ্বাদশ (HSC)" else "",
                        educationLevelNameEn = if (clsNum in 11..12) "HSC Level" else ""
                    )
                )
            }
            assertTrue("Expected Class $clsNum save to succeed: ${clsRes.exceptionOrNull()}", clsRes.isSuccess)
        }

        // Duplicate classNumber check
        val dupClassRes = withTimeout(5000L) {
            adminRepo.saveSchoolClass(
                com.example.model.SchoolClass(
                    id = "dup_class_11_$suffix",
                    classNumber = 11,
                    nameBn = "ডুপ্লিকেট ১১",
                    nameEn = "Duplicate 11"
                )
            )
        }
        assertTrue("Duplicate classNumber must be rejected", dupClassRes.isFailure)

        // 2. Delete default plan plan_annual_c5 and create custom Class 11 3-month plan
        val delDefaultRes = withTimeout(5000L) {
            adminRepo.deleteSubscriptionPlan("plan_annual_c5")
        }
        assertTrue("Expected default plan suppression to succeed: ${delDefaultRes.exceptionOrNull()}", delDefaultRes.isSuccess)

        val customPlanId = "plan_c11_3m_$suffix"
        val customPlan = SubscriptionPlan(
            id = customPlanId,
            nameBn = "একাদশ শ্রেণি ৩ মাস প্যাকেজ",
            nameEn = "Class 11 3-Month Pack",
            classNumber = 11,
            stream = "ALL",
            durationMonths = 3,
            priceBdt = 190,
            isActive = true
        )
        val savePlanRes = withTimeout(5000L) { adminRepo.saveSubscriptionPlan(customPlan) }
        assertTrue("Expected custom Class 11 plan save to succeed: ${savePlanRes.exceptionOrNull()}", savePlanRes.isSuccess)

        // 3. Student queries classes, levels, and active plans
        val studentUid = signInTestUser("hsc_stu_${suffix.lowercase()}@test.com")
        val libRepo = LibraryRepository(firestore, auth)
        val studyRepo = StudyRepository(firestore, auth)

        val activeClasses = withTimeout(5000L) {
            libRepo.observeActiveClasses().first { list -> list.any { it.classNumber == 11 } && list.any { it.classNumber == 13 } }
        }
        val classNumbers = activeClasses.map { it.classNumber }
        assertTrue(classNumbers.containsAll(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13)))
        assertEquals("Classes must be strictly ascending", classNumbers.sorted(), classNumbers)

        val activePlans = withTimeout(5000L) {
            studyRepo.observeActivePlans().first { list -> list.any { it.id == customPlanId } }
        }
        assertFalse("Deleted default plan must stay suppressed", activePlans.any { it.id == "plan_annual_c5" })
        val livePlan = activePlans.first { it.id == customPlanId }
        assertEquals(3, livePlan.durationMonths)
        assertEquals(190, livePlan.priceBdt)

        // 4. Student purchases dynamic Class 11 plan -> Admin approves -> Class 11 premium unlocked
        val submitRes = withTimeout(5000L) {
            studyRepo.submitSubscriptionPaymentRequest(
                userName = "তাহমিদ আহমেদ",
                userEmail = "tahmid@test.com",
                plan = livePlan,
                paymentMethod = "bKash",
                transactionReference = "HSC$suffix",
                senderNumber = "01711223344"
            )
        }
        assertTrue("Expected payment submit for dynamic Class 11 plan to succeed: ${submitRes.exceptionOrNull()}", submitRes.isSuccess)
        val reqId = submitRes.getOrThrow()

        val req = withTimeout(5000L) {
            studyRepo.observeUserSubscriptionRequests(studentUid).first { list -> list.any { it.id == reqId } }
        }.first { it.id == reqId }

        signInSuperAdmin()
        val approveRes = withTimeout(5000L) {
            AdminRepository(firestore, auth).approveSubscriptionRequest(req)
        }
        assertTrue("Expected Super Admin approval for Class 11 plan to succeed: ${approveRes.exceptionOrNull()}", approveRes.isSuccess)

        signInTestUser("hsc_stu_${suffix.lowercase()}@test.com")
        val subs = withTimeout(5000L) {
            studyRepo.observeUserSubscriptions(studentUid).first { list -> list.any { it.requestId == reqId } }
        }
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 11,
                contentStream = "ALL",
                currentUserUid = studentUid,
                userProfile = UserProfile(userId = studentUid, isActive = true, role = "student"),
                userSubscriptions = subs,
                nowMillis = System.currentTimeMillis()
            )
        )
        Unit
    }
}

