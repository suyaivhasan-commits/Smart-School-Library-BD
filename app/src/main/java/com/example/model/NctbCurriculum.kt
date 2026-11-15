package com.example.model

/**
 * Authentic National Curriculum and Textbook Board (NCTB) Bangladesh
 * textbook catalog mapping for Classes 5 to 10 with streams and subjects.
 */
object NctbCurriculum {

    data class NctbEntry(
        val classNumber: Int,
        val stream: String, // "ALL", "SCIENCE", "HUMANITIES", "BUSINESS"
        val subject: String,
        val books: List<String>
    )

    val curriculumEntries: List<NctbEntry> = listOf(
        // ====================== CLASS 5 ======================
        NctbEntry(5, "ALL", "বাংলা", listOf("আমার বাংলা বই")),
        NctbEntry(5, "ALL", "ইংরেজি", listOf("English for Today")),
        NctbEntry(5, "ALL", "প্রাথমিক গণিত", listOf("প্রাথমিক গণিত")),
        NctbEntry(5, "ALL", "প্রাথমিক বিজ্ঞান", listOf("প্রাথমিক বিজ্ঞান")),
        NctbEntry(5, "ALL", "বাংলাদেশ ও বিশ্বপরিচয়", listOf("বাংলাদেশ ও বিশ্বপরিচয়")),
        NctbEntry(5, "ALL", "ইসলাম ও নৈতিক শিক্ষা", listOf("ইসলাম ও নৈতিক শিক্ষা")),
        NctbEntry(5, "ALL", "হিন্দুধর্ম ও নৈতিক শিক্ষা", listOf("হিন্দুধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(5, "ALL", "বৌদ্ধধর্ম ও নৈতিক শিক্ষা", listOf("বৌদ্ধধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(5, "ALL", "খ্রিস্টধর্ম ও নৈতিক শিক্ষা", listOf("খ্রিস্টধর্ম ও নৈতিক শিক্ষা")),

        // ====================== CLASS 6 ======================
        NctbEntry(6, "ALL", "বাংলা", listOf("বাংলা")),
        NctbEntry(6, "ALL", "ইংরেজি", listOf("English for Today")),
        NctbEntry(6, "ALL", "গণিত", listOf("গণিত")),
        NctbEntry(6, "ALL", "বিজ্ঞান", listOf("বিজ্ঞান (অনুসন্ধানী পাঠ)", "বিজ্ঞান (অনুশীলনী পাঠ)")),
        NctbEntry(6, "ALL", "ইতিহাস ও সামাজিক বিজ্ঞান", listOf("ইতিহাস ও সামাজিক বিজ্ঞান")),
        NctbEntry(6, "ALL", "ডিজিটাল প্রযুক্তি", listOf("ডিজিটাল প্রযুক্তি")),
        NctbEntry(6, "ALL", "জীবন ও জীবিকা", listOf("জীবন ও জীবিকা")),
        NctbEntry(6, "ALL", "স্বাস্থ্য সুরক্ষা", listOf("স্বাস্থ্য সুরক্ষা")),
        NctbEntry(6, "ALL", "ধর্ম ও নৈতিক শিক্ষা", listOf("ইসলাম শিক্ষা", "হিন্দুধর্ম শিক্ষা", "বৌদ্ধধর্ম শিক্ষা", "খ্রিস্টধর্ম শিক্ষা")),
        NctbEntry(6, "ALL", "শিল্প ও সংস্কৃতি", listOf("শিল্প ও সংস্কৃতি")),

        // ====================== CLASS 7 ======================
        NctbEntry(7, "ALL", "বাংলা", listOf("বাংলা")),
        NctbEntry(7, "ALL", "ইংরেজি", listOf("English for Today")),
        NctbEntry(7, "ALL", "গণিত", listOf("গণিত")),
        NctbEntry(7, "ALL", "বিজ্ঞান", listOf("বিজ্ঞান (অনুসন্ধানী পাঠ)", "বিজ্ঞান (অনুশীলনী পাঠ)")),
        NctbEntry(7, "ALL", "ইতিহাস ও সামাজিক বিজ্ঞান", listOf("ইতিহাস ও সামাজিক বিজ্ঞান")),
        NctbEntry(7, "ALL", "ডিজিটাল প্রযুক্তি", listOf("ডিজিটাল প্রযুক্তি")),
        NctbEntry(7, "ALL", "জীবন ও জীবিকা", listOf("জীবন ও জীবিকা")),
        NctbEntry(7, "ALL", "স্বাস্থ্য সুরক্ষা", listOf("স্বাস্থ্য সুরক্ষা")),
        NctbEntry(7, "ALL", "ধর্ম ও নৈতিক শিক্ষা", listOf("ইসলাম শিক্ষা", "হিন্দুধর্ম শিক্ষা", "বৌদ্ধধর্ম শিক্ষা", "খ্রিস্টধর্ম শিক্ষা")),
        NctbEntry(7, "ALL", "শিল্প ও সংস্কৃতি", listOf("শিল্প ও সংস্কৃতি")),

        // ====================== CLASS 8 ======================
        NctbEntry(8, "ALL", "বাংলা", listOf("সাহিত্য কণিকা (বাংলা)", "আনন্দ পাঠ (বাংলা দ্রুত পঠন)")),
        NctbEntry(8, "ALL", "ইংরেজি", listOf("English for Today", "English Grammar and Composition")),
        NctbEntry(8, "ALL", "গণিত", listOf("গণিত")),
        NctbEntry(8, "ALL", "বিজ্ঞান", listOf("বিজ্ঞান (অনুসন্ধানী পাঠ)", "বিজ্ঞান (অনুশীলনী পাঠ)")),
        NctbEntry(8, "ALL", "ইতিহাস ও সামাজিক বিজ্ঞান", listOf("ইতিহাস ও সামাজিক বিজ্ঞান")),
        NctbEntry(8, "ALL", "বাংলাদেশ ও বিশ্বপরিচয়", listOf("বাংলাদেশ ও বিশ্বপরিচয়")),
        NctbEntry(8, "ALL", "ডিজিটাল প্রযুক্তি", listOf("ডিজিটাল প্রযুক্তি", "তথ্য ও যোগাযোগ প্রযুক্তি")),
        NctbEntry(8, "ALL", "জীবন ও জীবিকা", listOf("জীবন ও জীবিকা", "কৃষি শিক্ষা", "গার্হস্থ্য বিজ্ঞান")),
        NctbEntry(8, "ALL", "স্বাস্থ্য সুরক্ষা", listOf("স্বাস্থ্য সুরক্ষা", "শারীরিক শিক্ষা ও স্বাস্থ্য")),
        NctbEntry(8, "ALL", "ধর্ম ও নৈতিক শিক্ষা", listOf("ইসলাম ও নৈতিক শিক্ষা", "হিন্দুধর্ম ও নৈতিক শিক্ষা", "বৌদ্ধধর্ম ও নৈতিক শিক্ষা", "খ্রিস্টধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(8, "ALL", "শিল্প ও সংস্কৃতি", listOf("শিল্প ও সংস্কৃতি", "চারু ও কারুকলা")),

        // ====================== CLASS 9 ======================
        // Common
        NctbEntry(9, "ALL", "বাংলা ১ম পত্র", listOf("সাহিত্য সংকলন (বাংলা ১ম পত্র)", "সহপাঠ (উপন্যাস ও নাটক)")),
        NctbEntry(9, "ALL", "বাংলা ২য় পত্র", listOf("বাংলা ভাষার ব্যাকরণ ও নির্মিতি")),
        NctbEntry(9, "ALL", "ইংরেজি ১ম পত্র", listOf("English for Today (Classes 9-10)")),
        NctbEntry(9, "ALL", "ইংরেজি ২য় পত্র", listOf("English Grammar and Composition")),
        NctbEntry(9, "ALL", "গণিত", listOf("গণিত (Mathematics)")),
        NctbEntry(9, "ALL", "তথ্য ও যোগাযোগ প্রযুক্তি", listOf("তথ্য ও যোগাযোগ প্রযুক্তি")),
        NctbEntry(9, "ALL", "ইসলাম ও নৈতিক শিক্ষা", listOf("ইসলাম ও নৈতিক শিক্ষা")),
        NctbEntry(9, "ALL", "হিন্দুধর্ম ও নৈতিক শিক্ষা", listOf("হিন্দুধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(9, "ALL", "বৌদ্ধধর্ম ও নৈতিক শিক্ষা", listOf("বৌদ্ধধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(9, "ALL", "খ্রিস্টধর্ম ও নৈতিক শিক্ষা", listOf("খ্রিস্টধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(9, "ALL", "শারীরিক শিক্ষা, স্বাস্থ্যবিজ্ঞান ও খেলাধুলা", listOf("শারীরিক শিক্ষা, স্বাস্থ্যবিজ্ঞান ও খেলাধুলা")),
        NctbEntry(9, "ALL", "ক্যারিয়ার এডুকেশন", listOf("ক্যারিয়ার এডুকেশন")),
        // Science
        NctbEntry(9, "SCIENCE", "পদার্থবিজ্ঞান", listOf("পদার্থবিজ্ঞান")),
        NctbEntry(9, "SCIENCE", "রসায়ন", listOf("রসায়ন")),
        NctbEntry(9, "SCIENCE", "জীববিজ্ঞান", listOf("জীববিজ্ঞান")),
        NctbEntry(9, "SCIENCE", "উচ্চতর গণিত", listOf("উচ্চতর গণিত")),
        NctbEntry(9, "SCIENCE", "বাংলাদেশ ও বিশ্বপরিচয়", listOf("বাংলাদেশ ও বিশ্বপরিচয়")),
        // Humanities
        NctbEntry(9, "HUMANITIES", "বাংলাদেশের ইতিহাস ও বিশ্বসভ্যতা", listOf("বাংলাদেশের ইতিহাস ও বিশ্বসভ্যতা")),
        NctbEntry(9, "HUMANITIES", "ভূগোল ও পরিবেশ", listOf("ভূগোল ও পরিবেশ")),
        NctbEntry(9, "HUMANITIES", "পৌরনীতি ও নাগরিকতা", listOf("পৌরনীতি ও নাগরিকতা")),
        NctbEntry(9, "HUMANITIES", "অর্থনীতি", listOf("অর্থনীতি")),
        NctbEntry(9, "HUMANITIES", "বিজ্ঞান (সাধারণ বিজ্ঞান)", listOf("বিজ্ঞান (সাধারণ বিজ্ঞান)")),
        // Business
        NctbEntry(9, "BUSINESS", "হিসাববিজ্ঞান", listOf("হিসাববিজ্ঞান")),
        NctbEntry(9, "BUSINESS", "ফিন্যান্স ও ব্যাংকিং", listOf("ফিন্যান্স ও ব্যাংকিং")),
        NctbEntry(9, "BUSINESS", "ব্যবসায় উদ্যোগ", listOf("ব্যবসায় উদ্যোগ")),
        NctbEntry(9, "BUSINESS", "বিজ্ঞান (সাধারণ বিজ্ঞান)", listOf("বিজ্ঞান (সাধারণ বিজ্ঞান)")),

        // ====================== CLASS 10 ======================
        // Common
        NctbEntry(10, "ALL", "বাংলা ১ম পত্র", listOf("সাহিত্য সংকলন (বাংলা ১ম পত্র)", "সহপাঠ (উপন্যাস ও নাটক)")),
        NctbEntry(10, "ALL", "বাংলা ২য় পত্র", listOf("বাংলা ভাষার ব্যাকরণ ও নির্মিতি")),
        NctbEntry(10, "ALL", "ইংরেজি ১ম পত্র", listOf("English for Today (Classes 9-10)")),
        NctbEntry(10, "ALL", "ইংরেজি ২য় পত্র", listOf("English Grammar and Composition")),
        NctbEntry(10, "ALL", "গণিত", listOf("গণিত (Mathematics)")),
        NctbEntry(10, "ALL", "তথ্য ও যোগাযোগ প্রযুক্তি", listOf("তথ্য ও যোগাযোগ প্রযুক্তি")),
        NctbEntry(10, "ALL", "ইসলাম ও নৈতিক শিক্ষা", listOf("ইসলাম ও নৈতিক শিক্ষা")),
        NctbEntry(10, "ALL", "হিন্দুধর্ম ও নৈতিক শিক্ষা", listOf("হিন্দুধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(10, "ALL", "বৌদ্ধধর্ম ও নৈতিক শিক্ষা", listOf("বৌদ্ধধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(10, "ALL", "খ্রিস্টধর্ম ও নৈতিক শিক্ষা", listOf("খ্রিস্টধর্ম ও নৈতিক শিক্ষা")),
        NctbEntry(10, "ALL", "শারীরিক শিক্ষা, স্বাস্থ্যবিজ্ঞান ও খেলাধুলা", listOf("শারীরিক শিক্ষা, স্বাস্থ্যবিজ্ঞান ও খেলাধুলা")),
        NctbEntry(10, "ALL", "ক্যারিয়ার এডুকেশন", listOf("ক্যারিয়ার এডুকেশন")),
        // Science
        NctbEntry(10, "SCIENCE", "পদার্থবিজ্ঞান", listOf("পদার্থবিজ্ঞান")),
        NctbEntry(10, "SCIENCE", "রসায়ন", listOf("রসায়ন")),
        NctbEntry(10, "SCIENCE", "জীববিজ্ঞান", listOf("জীববিজ্ঞান")),
        NctbEntry(10, "SCIENCE", "উচ্চতর গণিত", listOf("উচ্চতর গণিত")),
        NctbEntry(10, "SCIENCE", "বাংলাদেশ ও বিশ্বপরিচয়", listOf("বাংলাদেশ ও বিশ্বপরিচয়")),
        // Humanities
        NctbEntry(10, "HUMANITIES", "বাংলাদেশের ইতিহাস ও বিশ্বসভ্যতা", listOf("বাংলাদেশের ইতিহাস ও বিশ্বসভ্যতা")),
        NctbEntry(10, "HUMANITIES", "ভূগোল ও পরিবেশ", listOf("ভূগোল ও পরিবেশ")),
        NctbEntry(10, "HUMANITIES", "পৌরনীতি ও নাগরিকতা", listOf("পৌরনীতি ও নাগরিকতা")),
        NctbEntry(10, "HUMANITIES", "অর্থনীতি", listOf("অর্থনীতি")),
        NctbEntry(10, "HUMANITIES", "বিজ্ঞান (সাধারণ বিজ্ঞান)", listOf("বিজ্ঞান (সাধারণ বিজ্ঞান)")),
        // Business
        NctbEntry(10, "BUSINESS", "হিসাববিজ্ঞান", listOf("হিসাববিজ্ঞান")),
        NctbEntry(10, "BUSINESS", "ফিন্যান্স ও ব্যাংকিং", listOf("ফিন্যান্স ও ব্যাংকিং")),
        NctbEntry(10, "BUSINESS", "ব্যবসায় উদ্যোগ", listOf("ব্যবসায় উদ্যোগ")),
        NctbEntry(10, "BUSINESS", "বিজ্ঞান (সাধারণ বিজ্ঞান)", listOf("বিজ্ঞান (সাধারণ বিজ্ঞান)"))
    )

    fun getSubjects(classNumber: Int, stream: String): List<String> {
        val matches = curriculumEntries.filter {
            it.classNumber == classNumber && (it.stream == stream || it.stream == "ALL" || stream == "ALL")
        }
        return matches.map { it.subject }.distinct()
    }

    fun getBooks(classNumber: Int, stream: String, subject: String): List<String> {
        val matches = curriculumEntries.filter {
            it.classNumber == classNumber &&
                (it.stream == stream || it.stream == "ALL" || stream == "ALL") &&
                it.subject.equals(subject.trim(), ignoreCase = true)
        }
        val result = matches.flatMap { it.books }.distinct()
        return if (result.isNotEmpty()) result else listOf(subject)
    }
}
