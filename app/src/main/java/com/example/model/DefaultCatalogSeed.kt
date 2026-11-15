package com.example.model

object DefaultCatalogSeed {

    val defaultEducationLevels: List<EducationLevel> = listOf(
        EducationLevel(
            id = "level_ssc",
            code = "SSC",
            nameBn = "SSC",
            nameEn = "SSC",
            subtitleBn = "মাধ্যমিক শিক্ষা",
            subtitleEn = "Secondary Education",
            descriptionBn = "নবম ও দশম শ্রেণির মাধ্যমিক পর্যায়ের পাঠ্যবই, গাইড এবং পরীক্ষার প্রস্তুতি",
            minClassNumber = 9,
            maxClassNumber = 10,
            sortOrder = 1,
            isActive = true
        ),
        EducationLevel(
            id = "level_hsc",
            code = "HSC",
            nameBn = "HSC",
            nameEn = "HSC",
            subtitleBn = "উচ্চমাধ্যমিক শিক্ষা",
            subtitleEn = "Higher Secondary Education",
            descriptionBn = "একাদশ ও দ্বাদশ শ্রেণির উচ্চমাধ্যমিক পর্যায়ের পাঠ্যবই ও সহায়ক উপকরণ",
            minClassNumber = 11,
            maxClassNumber = 12,
            sortOrder = 2,
            isActive = true
        ),
        EducationLevel(
            id = "level_graduate",
            code = "GRADUATE",
            nameBn = "Graduate",
            nameEn = "Graduate",
            subtitleBn = "উচ্চশিক্ষার স্তর",
            subtitleEn = "Higher Education Level",
            descriptionBn = "উচ্চশিক্ষা পর্যায়ের শ্রেণি ও বিশেষায়িত একাডেমিক পাঠ্যবই",
            minClassNumber = 13,
            maxClassNumber = 30,
            sortOrder = 3,
            isActive = true
        )
    )

    val defaultClasses: List<SchoolClass> = listOf(
        SchoolClass(id = "class_5", classNumber = 5, nameBn = "পঞ্চম শ্রেণি", nameEn = "Class 5", hasStreams = false, isActive = true, sortOrder = 1),
        SchoolClass(id = "class_6", classNumber = 6, nameBn = "ষষ্ঠ শ্রেণি", nameEn = "Class 6", hasStreams = false, isActive = true, sortOrder = 2),
        SchoolClass(id = "class_7", classNumber = 7, nameBn = "সপ্তম শ্রেণি", nameEn = "Class 7", hasStreams = false, isActive = true, sortOrder = 3),
        SchoolClass(id = "class_8", classNumber = 8, nameBn = "অষ্টম শ্রেণি", nameEn = "Class 8", hasStreams = false, isActive = true, sortOrder = 4),
        SchoolClass(id = "class_9", classNumber = 9, nameBn = "নবম শ্রেণি", nameEn = "Class 9", hasStreams = true, isActive = true, sortOrder = 5, educationLevelId = "level_ssc", educationLevelNameBn = "SSC", educationLevelNameEn = "SSC"),
        SchoolClass(id = "class_10", classNumber = 10, nameBn = "দশম শ্রেণি", nameEn = "Class 10", hasStreams = true, isActive = true, sortOrder = 6, educationLevelId = "level_ssc", educationLevelNameBn = "SSC", educationLevelNameEn = "SSC")
    )

    val defaultStreams: List<AcademicStream> = listOf(
        AcademicStream(id = "stream_science", code = "SCIENCE", nameBn = "বিজ্ঞান বিভাগ", nameEn = "Science", isActive = true, sortOrder = 1),
        AcademicStream(id = "stream_humanities", code = "HUMANITIES", nameBn = "মানবিক বিভাগ", nameEn = "Humanities", isActive = true, sortOrder = 2),
        AcademicStream(id = "stream_business", code = "BUSINESS", nameBn = "ব্যবসায় শিক্ষা বিভাগ", nameEn = "Business Studies", isActive = true, sortOrder = 3)
    )

    /**
     * Exact 10 Predefined Annual Subscription Packages for Smart School Library BD:
     * Classes 5, 6, 7, 8 (General) + Classes 9, 10 (Science, Humanities, Business Studies).
     * Single subscription unlocks BOTH Original Textbooks AND Guides/Notes for that class/stream.
     * Valid through December 31, 11:59:59 PM (Asia/Dhaka).
     */
    val defaultAnnualPackages: List<SubscriptionPlan> = listOf(
        SubscriptionPlan(
            id = "plan_annual_c5",
            nameBn = "পঞ্চম শ্রেণি — বার্ষিক প্যাকেজ",
            nameEn = "Class 5 — Annual Plan",
            descriptionBn = "পঞ্চম শ্রেণির সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 5,
            stream = "ALL",
            durationMonths = 12,
            priceBdt = 250,
            isActive = true,
            benefits = listOf(
                "পঞ্চম শ্রেণির সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "সকল অধ্যায়ভিত্তিক গাইড, সৃজনশীল প্রশ্নোত্তর ও নোট আনলক",
                "মডেল টেস্ট ও বার্ষিক পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c6",
            nameBn = "ষষ্ঠ শ্রেণি — বার্ষিক প্যাকেজ",
            nameEn = "Class 6 — Annual Plan",
            descriptionBn = "ষষ্ঠ শ্রেণির সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 6,
            stream = "ALL",
            durationMonths = 12,
            priceBdt = 250,
            isActive = true,
            benefits = listOf(
                "ষষ্ঠ শ্রেণির সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "সকল অধ্যায়ভিত্তিক গাইড, সৃজনশীল প্রশ্নোত্তর ও নোট আনলক",
                "মডেল টেস্ট ও বার্ষিক পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c7",
            nameBn = "সপ্তম শ্রেণি — বার্ষিক প্যাকেজ",
            nameEn = "Class 7 — Annual Plan",
            descriptionBn = "সপ্তম শ্রেণির সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 7,
            stream = "ALL",
            durationMonths = 12,
            priceBdt = 250,
            isActive = true,
            benefits = listOf(
                "সপ্তম শ্রেণির সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "সকল অধ্যায়ভিত্তিক গাইড, সৃজনশীল প্রশ্নোত্তর ও নোট আনলক",
                "মডেল টেস্ট ও বার্ষিক পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c8",
            nameBn = "অষ্টম শ্রেণি — বার্ষিক প্যাকেজ",
            nameEn = "Class 8 — Annual Plan",
            descriptionBn = "অষ্টম শ্রেণির সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 8,
            stream = "ALL",
            durationMonths = 12,
            priceBdt = 250,
            isActive = true,
            benefits = listOf(
                "অষ্টম শ্রেণির সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "সকল অধ্যায়ভিত্তিক গাইড, সৃজনশীল প্রশ্নোত্তর ও নোট আনলক",
                "মডেল টেস্ট ও বার্ষিক পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c9_science",
            nameBn = "নবম শ্রেণি (বিজ্ঞান বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 9 Science — Annual Plan",
            descriptionBn = "নবম শ্রেণির বিজ্ঞান বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 9,
            stream = "SCIENCE",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "নবম শ্রেণির বিজ্ঞান বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "পদার্থ, রসায়ন, জীববিজ্ঞান ও গণিতসহ সকল গাইড ও সৃজনশীল আনলক",
                "মডেল টেস্ট ও পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c9_humanities",
            nameBn = "নবম শ্রেণি (মানবিক বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 9 Humanities — Annual Plan",
            descriptionBn = "নবম শ্রেণির মানবিক বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 9,
            stream = "HUMANITIES",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "নবম শ্রেণির মানবিক বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "ইতিহাস, ভূগোল, পৌরনীতি ও অর্থনীতিসহ সকল গাইড ও নোট আনলক",
                "মডেল টেস্ট ও পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c9_business",
            nameBn = "নবম শ্রেণি (ব্যবসায় শিক্ষা বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 9 Business Studies — Annual Plan",
            descriptionBn = "নবম শ্রেণির ব্যবসায় শিক্ষা বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 9,
            stream = "BUSINESS",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "নবম শ্রেণির ব্যবসায় শিক্ষা বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "হিসাববিজ্ঞান, ফিন্যান্স ও ব্যবসায় উদ্যোগসহ সকল গাইড ও নোট আনলক",
                "মডেল টেস্ট ও পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c10_science",
            nameBn = "দশম শ্রেণি (বিজ্ঞান বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 10 Science — Annual Plan",
            descriptionBn = "দশম শ্রেণির বিজ্ঞান বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 10,
            stream = "SCIENCE",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "দশম শ্রেণির বিজ্ঞান বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "পদার্থ, রসায়ন, জীববিজ্ঞান ও গণিতসহ সকল গাইড ও সৃজনশীল আনলক",
                "এসএসসি ও টেস্ট পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c10_humanities",
            nameBn = "দশম শ্রেণি (মানবিক বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 10 Humanities — Annual Plan",
            descriptionBn = "দশম শ্রেণির মানবিক বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 10,
            stream = "HUMANITIES",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "দশম শ্রেণির মানবিক বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "ইতিহাস, ভূগোল, পৌরনীতি ও অর্থনীতিসহ সকল গাইড ও নোট আনলক",
                "এসএসসি ও টেস্ট পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        ),
        SubscriptionPlan(
            id = "plan_annual_c10_business",
            nameBn = "দশম শ্রেণি (ব্যবসায় শিক্ষা বিভাগ) — বার্ষিক প্যাকেজ",
            nameEn = "Class 10 Business Studies — Annual Plan",
            descriptionBn = "দশম শ্রেণির ব্যবসায় শিক্ষা বিভাগের সকল মূল পাঠ্যবই এবং গাইড ও নোট সম্পূর্ণ শিক্ষাবর্ষের জন্য আনলক",
            classNumber = 10,
            stream = "BUSINESS",
            durationMonths = 12,
            priceBdt = 350,
            isActive = true,
            benefits = listOf(
                "দশম শ্রেণির ব্যবসায় শিক্ষা বিভাগের সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "হিসাববিজ্ঞান, ফিন্যান্স ও ব্যবসায় উদ্যোগসহ সকল গাইড ও নোট আনলক",
                "এসএসসি ও টেস্ট পরীক্ষার পূর্ণ প্রস্তুতি",
                "৩১ ডিসেম্বর রাত ১১:৫৯:৫৯ পর্যন্ত পূর্ণ শিক্ষাবর্ষ মেয়াদ"
            )
        )
    )

    data class GkCategoryMeta(val code: String, val nameBn: String, val nameEn: String)

    val gkCategories: List<GkCategoryMeta> = listOf(
        GkCategoryMeta("BANGLADESH", "বাংলাদেশ বিষয়াবলি", "Bangladesh Affairs"),
        GkCategoryMeta("INTERNATIONAL", "আন্তর্জাতিক বিষয়াবলি", "International Affairs"),
        GkCategoryMeta("SCIENCE_TECH", "বিজ্ঞান ও প্রযুক্তি", "Science & Technology"),
        GkCategoryMeta("HISTORY", "ইতিহাস", "History"),
        GkCategoryMeta("GEOGRAPHY", "ভূগোল", "Geography"),
        GkCategoryMeta("SPORTS", "খেলাধুলা", "Sports"),
        GkCategoryMeta("CURRENT_AFFAIRS", "সাম্প্রতিক বিষয়াবলি", "Current Affairs")
    )

    data class GuideTypeMeta(val code: String, val nameBn: String, val nameEn: String)

    val guideTypes: List<GuideTypeMeta> = listOf(
        GuideTypeMeta("COMPLETE_GUIDE", "সম্পূর্ণ গাইড", "Complete Guide"),
        GuideTypeMeta("CHAPTER_NOTE", "অধ্যায়ভিত্তিক নোট", "Chapter Notes"),
        GuideTypeMeta("SHORT_QUESTION", "সংক্ষিপ্ত প্রশ্ন", "Short Questions"),
        GuideTypeMeta("CREATIVE_QUESTION", "সৃজনশীল প্রশ্ন", "Creative Questions (CQ)"),
        GuideTypeMeta("MCQ", "বহুনির্বাচনি (MCQ)", "MCQ"),
        GuideTypeMeta("EXPLANATION", "ব্যাখ্যা ও বিশ্লেষণ", "Explanations"),
        GuideTypeMeta("PRACTICE", "অনুশীলনী চর্চা", "Practice Exercises"),
        GuideTypeMeta("IMPORTANT_QUESTION", "গুরুত্বপূর্ণ প্রশ্ন", "Important Questions"),
        GuideTypeMeta("SUGGESTION", "পরীক্ষার সাজেশন", "Exam Suggestions")
    )

    /**
     * Clearly labeled DEMO General Knowledge items shown ONLY when Firestore `general_knowledge`
     * has no published items yet.
     */
    val demoGkItems: List<GeneralKnowledgeItem> = listOf(
        GeneralKnowledgeItem(
            id = "demo_gk_1",
            category = "BANGLADESH",
            questionBn = "বাংলাদেশের জাতীয় ফুল কোনটি?",
            questionEn = "What is the national flower of Bangladesh?",
            answerBn = "শাপলা (সাদা শাপলা)",
            answerEn = "Water Lily (White Shapla)",
            explanationBn = "সাদা শাপলা (Nymphaea nouchali) বাংলাদেশের জাতীয় ফুল এবং এটি দেশের জলজ প্রকৃতির প্রতীক।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_2",
            category = "BANGLADESH",
            questionBn = "বাংলাদেশের সংবিধান কবে কার্যকর হয়?",
            questionEn = "When did the Constitution of Bangladesh come into force?",
            answerBn = "১৬ ডিসেম্বর ১৯৭২",
            answerEn = "16 December 1972",
            explanationBn = "১৯৭২ সালের ৪ নভেম্বর গণপরিষদে সংবিধান গৃহীত হয় এবং ১৬ ডিসেম্বর ১৯৭২ থেকে কার্যকর হয়।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_3",
            category = "INTERNATIONAL",
            questionBn = "জাতিসংঘের সদর দপ্তর কোথায় অবস্থিত?",
            questionEn = "Where is the headquarters of the United Nations located?",
            answerBn = "নিউইয়র্ক, যুক্তরাষ্ট্র",
            answerEn = "New York, USA",
            explanationBn = "১৯৪৫ সালের ২৪ অক্টোবর প্রতিষ্ঠিত জাতিসংঘের প্রধান সদর দপ্তর যুক্তরাষ্ট্রের নিউইয়র্ক শহরে অবস্থিত।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_4",
            category = "SCIENCE_TECH",
            questionBn = "আলোর বেগ প্রতি সেকেন্ডে প্রায় কত?",
            questionEn = "What is the approximate speed of light per second?",
            answerBn = "৩ × ১০⁸ মিটার/সেকেন্ড (প্রায় ৩ লক্ষ কিমি/সেকেন্ড)",
            answerEn = "3 × 10^8 m/s",
            explanationBn = "শূন্য মাধ্যমে আলোর বেগ সেকেন্ডে প্রায় ২,৯৯,৭৯২ কিলোমিটার বা ৩ × ১০⁸ মিটার।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_5",
            category = "HISTORY",
            questionBn = "আন্তর্জাতিক মাতৃভাষা দিবস হিসেবে ২১শে ফেব্রুয়ারি কোন সংস্থা ঘোষণা করে?",
            questionEn = "Which organization proclaimed 21 February as International Mother Language Day?",
            answerBn = "ইউনেস্কো (UNESCO)",
            answerEn = "UNESCO",
            explanationBn = "১৯৯৯ সালের ১৭ নভেম্বর ইউনেস্কো ২১শে ফেব্রুয়ারিকে আন্তর্জাতিক মাতৃভাষা দিবস হিসেবে স্বীকৃতি দেয়।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_6",
            category = "GEOGRAPHY",
            questionBn = "পৃথিবীর বৃহত্তম ম্যানগ্রোভ বন কোনটি?",
            questionEn = "Which is the largest mangrove forest in the world?",
            answerBn = "সুন্দরবন",
            answerEn = "The Sundarbans",
            explanationBn = "বঙ্গোপসাগর উপকূলবর্তী সুন্দরবন বিশ্বের একক বৃহত্তম প্রাকৃতিক ম্যানগ্রোভ বন।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_7",
            category = "SPORTS",
            questionBn = "বাংলাদেশ কত সালে টেস্ট ক্রিকেটের মর্যাদা লাভ করে?",
            questionEn = "In which year did Bangladesh achieve Test cricket status?",
            answerBn = "২০০০ সালে",
            answerEn = "Year 2000",
            explanationBn = "২০০০ সালের ২৬ জুন বাংলাদেশ দশম টেস্ট খেলুড়ে দেশ হিসেবে আইসিসির পূর্ণ সদস্যপদ লাভ করে।",
            isPublished = true,
            isDemo = true
        ),
        GeneralKnowledgeItem(
            id = "demo_gk_8",
            category = "CURRENT_AFFAIRS",
            questionBn = "বাংলাদেশের দীর্ঘতম সেতুর নাম কী?",
            questionEn = "What is the name of the longest bridge in Bangladesh?",
            answerBn = "পদ্মা বহুমুখী সেতু (৬.১৫ কিমি)",
            answerEn = "Padma Multipurpose Bridge (6.15 km)",
            explanationBn = "পদ্মা নদীর ওপর নির্মিত ৬.১৫ কিলোমিটার দীর্ঘ পদ্মা সেতু বাংলাদেশের দীর্ঘতম সেতু।",
            isPublished = true,
            isDemo = true
        )
    )

    /**
     * Clearly labeled DEMO MCQ practice test used only when Firestore `exam_preparation` has no tests yet.
     * Answer keys are kept in memory for local DEMO grading ONLY AFTER user confirms submission.
     */
    data class DemoExamBundle(
        val exam: ExamTest,
        val correctIndices: List<Int>,
        val explanations: List<String>
    )

    val demoExamBundles: List<DemoExamBundle> = listOf(
        DemoExamBundle(
            exam = ExamTest(
                id = "demo_model_test_1",
                titleBn = "সাধারণ জ্ঞান ও বাংলা মডেল টেস্ট (DEMO)",
                titleEn = "General Knowledge & Bangla Model Test (DEMO)",
                examType = "MODEL",
                classNumber = 5,
                stream = "ALL",
                subjectNameBn = "সমন্বিত মডেল টেস্ট",
                chapter = "নমুনা পরীক্ষা",
                durationMinutes = 10,
                totalMarks = 5,
                questionCount = 5,
                questions = listOf(
                    mapOf(
                        "questionText" to "বাংলাদেশের রাজধানী কোথায়?",
                        "optionA" to "চট্টগ্রাম",
                        "optionB" to "ঢাকা",
                        "optionC" to "খুলনা",
                        "optionD" to "রাজশাহী"
                    ),
                    mapOf(
                        "questionText" to "বাংলাদেশের জাতীয় পাখি কোনটি?",
                        "optionA" to "কোকিল",
                        "optionB" to "ময়না",
                        "optionC" to "দোয়েল",
                        "optionD" to "টিয়া"
                    ),
                    mapOf(
                        "questionText" to "স্বাধীনতা দিবস কবে পালিত হয়?",
                        "optionA" to "২১ ফেব্রুয়ারি",
                        "optionB" to "২৬ মার্চ",
                        "optionC" to "১৬ ডিসেম্বর",
                        "optionD" to "১৪ এপ্রিল"
                    ),
                    mapOf(
                        "questionText" to "পানির রাসায়নিক সংকেত কোনটি?",
                        "optionA" to "CO2",
                        "optionB" to "O2",
                        "optionC" to "H2O",
                        "optionD" to "NaCl"
                    ),
                    mapOf(
                        "questionText" to "ত্রিভুজের তিন কোণের সমষ্টি কত ডিগ্রি?",
                        "optionA" to "৯০°",
                        "optionB" to "১৮০°",
                        "optionC" to "২৭০°",
                        "optionD" to "৩৬০°"
                    )
                ),
                isPublished = true,
                isDemo = true
            ),
            correctIndices = listOf(1, 2, 1, 2, 1),
            explanations = listOf(
                "ঢাকা বাংলাদেশের রাজধানী এবং বৃহত্তম মহানগরী।",
                "দোয়েল বাংলাদেশের জাতীয় পাখি।",
                "২৬শে মার্চ বাংলাদেশের মহান স্বাধীনতা ও জাতীয় দিবস।",
                "দুই পরমাণু হাইড্রোজেন ও এক পরমাণু অক্সিজেন মিলে পানি (H2O) গঠিত হয়।",
                "জ্যামিতিক উপপাদ্য অনুসারে যেকোনো ত্রিভুজের তিন কোণের সমষ্টি দুই সমকোণ বা ১৮০ ডিগ্রি।"
            )
        )
    )
}
