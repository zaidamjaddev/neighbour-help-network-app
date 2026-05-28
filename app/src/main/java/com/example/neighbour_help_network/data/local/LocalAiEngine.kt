package com.example.neighbour_help_network.data.local

import java.util.Locale

/**
 * LocalAiEngine — Client-side "AI" processing singleton.
 *
 * Component 1: Keyword-driven Contextual Natural Language Classifier
 *   Analyzes free-text help descriptions and produces a structured
 *   AiAnalysisResult containing predicted category, urgency score,
 *   urgency level label, and automated semantic tags.
 *
 * Component 2: Inline Translation Simulator
 *   Passes strings through a curated phrase map to produce
 *   Urdu translations for common chat phrases, simulating
 *   a multilingual support feature without an external API.
 */
object LocalAiEngine {

    data class AiAnalysisResult(
        val predictedCategory: String,
        val urgencyScore: Int,
        val urgencyLevel: String,
        val automatedTags: List<String>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // AI Component 1: Keyword-driven Contextual Natural Language Classifier
    // ─────────────────────────────────────────────────────────────────────────

    fun analyzeHelpDescription(description: String): AiAnalysisResult {
        val text = description.lowercase(Locale.getDefault())
        val tags = mutableListOf<String>()

        // ── Rule matrices ──────────────────────────────────────────────────
        val medicalKeywords = listOf(
            "medicine", "doctor", "pharmacy", "hospital",
            "prescription", "pain", "clinic", "oxygen",
            "bp", "insulin", "fever", "injection", "blood pressure",
            "ambulance", "first aid", "bandage", "wound"
        )
        val groceryKeywords = listOf(
            "groceries", "food", "milk", "store", "market",
            "bread", "ration", "vegetables", "cooking",
            "eggs", "flour", "sugar", "rice", "fruit",
            "water", "baby food", "supplies"
        )
        val transportKeywords = listOf(
            "ride", "drive", "lift", "car", "flat tire",
            "mechanic", "transport", "vehicle", "pick up",
            "drop", "petrol", "taxi", "bus", "tow"
        )
        val elderlyKeywords = listOf(
            "elderly", "grandfather", "grandmother", "old man",
            "wheelchair", "walker", "age", "senior",
            "grandparent", "old woman", "retired", "nursing"
        )
        val repairKeywords = listOf(
            "fix", "repair", "broken", "leaking", "electricity",
            "plumber", "pipe", "wiring", "switch", "light",
            "fan", "ac", "appliance", "gas", "stove"
        )

        // ── Critical danger modifiers ──────────────────────────────────────
        val dangerKeywords = listOf(
            "urgent", "emergency", "bleeding", "chest pain",
            "stuck", "choking", "accident", "dying", "fainted",
            "sos", "critical", "help now", "immediately",
            "fire", "smoke", "gas leak", "collapse"
        )

        var category = "🤝 General Assistance"
        var baseUrgency = 20

        // ── Pattern matching to dynamically weight categories ──────────────
        when {
            medicalKeywords.any { text.contains(it) } -> {
                category = "🏥 Medical & Pharmacy"
                baseUrgency += 45
                tags.add("health")
                tags.add("medical-need")
            }
            groceryKeywords.any { text.contains(it) } -> {
                category = "🛒 Food & Groceries"
                baseUrgency += 15
                tags.add("supplies")
                tags.add("essential")
            }
            transportKeywords.any { text.contains(it) } -> {
                category = "🚗 Transport & Mobility"
                baseUrgency += 25
                tags.add("vehicle")
                tags.add("mobility")
            }
            elderlyKeywords.any { text.contains(it) } -> {
                category = "🧓 Elderly Care"
                baseUrgency += 35
                tags.add("senior-citizen")
                tags.add("care-priority")
            }
            repairKeywords.any { text.contains(it) } -> {
                category = "🔧 Home Repair"
                baseUrgency += 20
                tags.add("maintenance")
                tags.add("repair")
            }
        }

        // ── Apply critical modifiers: danger keyword count boosts urgency ──
        val dangerMatchCount = dangerKeywords.count { text.contains(it) }
        baseUrgency += (dangerMatchCount * 20)
        if (baseUrgency > 100) baseUrgency = 100

        // ── Classify urgency level from computed score ─────────────────────
        val level = when {
            baseUrgency >= 75 -> "🔴 CRITICAL / SOS"
            baseUrgency >= 45 -> "🟡 MEDIUM URGENCY"
            else              -> "🟢 LOW URGENCY"
        }

        if (dangerMatchCount > 0) tags.add("time-sensitive")

        return AiAnalysisResult(
            predictedCategory = category,
            urgencyScore      = baseUrgency,
            urgencyLevel      = level,
            automatedTags     = tags.distinct()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI Component 2: Inline Translation Simulator
    // ─────────────────────────────────────────────────────────────────────────

    fun simulateTranslation(text: String, targetLanguage: String): String {
        val lowerText = text.lowercase(Locale.getDefault()).trim()

        // ── Curated Urdu phrase map (script + romanization) ───────────────
        val translationsUrdu = mapOf(
            "hello"                     to "السلام علیکم (As-salamu alaykum)",
            "hi"                        to "ہیلو (Hi)",
            "i am on my way"            to "میں راستے میں ہوں (Main rastay mein hoon)",
            "where are you?"            to "آپ کہاں ہیں؟ (Aap kahan hain?)",
            "i need help"               to "مجھے مدد کی ضرورت ہے (Mujhe madad ki zaroorat hai)",
            "thank you"                 to "شکریہ (Shukriya)",
            "i am coming in 5 minutes"  to "میں 5 منٹ میں آ رہا ہوں (Main 5 minute mein aa raha hoon)",
            "please help me"            to "براہ کرم میری مدد کریں (Barahe karam meri madad karein)",
            "i am here"                 to "میں یہاں ہوں (Main yahan hoon)",
            "call me"                   to "مجھے کال کریں (Mujhe call karein)",
            "okay"                      to "ٹھیک ہے (Theek hai)",
            "no problem"                to "کوئی بات نہیں (Koi baat nahin)",
            "are you okay?"             to "کیا آپ ٹھیک ہیں؟ (Kya aap theek hain?)",
            "wait for me"               to "میرا انتظار کریں (Mera intezar karein)",
            "i will be there soon"      to "میں جلد وہاں پہنچ جاؤں گا (Main jald wahan pahunch jaunga)"
        )

        return when (targetLanguage.lowercase(Locale.getDefault())) {
            "urdu" -> translationsUrdu[lowerText]
                ?: "[اردو ترجمہ]: $text"
            else   -> "[Translated]: $text"
        }
    }
}
