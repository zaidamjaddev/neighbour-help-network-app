package com.example.neighbour_help_network.data.local

import java.util.Locale

/**
 * LocalAiEngine — Client-side processing singleton.
 */
object LocalAiEngine {

    data class AiAnalysisResult(
        val predictedCategory: String,
        val urgencyScore: Int,
        val urgencyLevel: String,
        val automatedTags: List<String>
    )

    fun analyzeHelpDescription(description: String): AiAnalysisResult {
        val text = description.lowercase(Locale.getDefault())
        val tags = mutableListOf<String>()

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

        val dangerKeywords = listOf(
            "urgent", "emergency", "bleeding", "chest pain",
            "stuck", "choking", "accident", "dying", "fainted",
            "sos", "critical", "help now", "immediately",
            "fire", "smoke", "gas leak", "collapse"
        )

        var category = "General Assistance"
        var baseUrgency = 20

        when {
            medicalKeywords.any { text.contains(it) } -> {
                category = "Medical & Pharmacy"
                baseUrgency += 45
                tags.add("health")
                tags.add("medical-need")
            }
            groceryKeywords.any { text.contains(it) } -> {
                category = "Food & Groceries"
                baseUrgency += 15
                tags.add("supplies")
                tags.add("essential")
            }
            transportKeywords.any { text.contains(it) } -> {
                category = "Transport & Mobility"
                baseUrgency += 25
                tags.add("vehicle")
                tags.add("mobility")
            }
            elderlyKeywords.any { text.contains(it) } -> {
                category = "Elderly Care"
                baseUrgency += 35
                tags.add("senior-citizen")
                tags.add("care-priority")
            }
            repairKeywords.any { text.contains(it) } -> {
                category = "Home Repair"
                baseUrgency += 20
                tags.add("maintenance")
                tags.add("repair")
            }
        }

        val dangerMatchCount = dangerKeywords.count { text.contains(it) }
        baseUrgency += (dangerMatchCount * 20)
        if (baseUrgency > 100) baseUrgency = 100

        val level = when {
            baseUrgency >= 75 -> "CRITICAL / SOS"
            baseUrgency >= 45 -> "MEDIUM URGENCY"
            else              -> "LOW URGENCY"
        }

        if (dangerMatchCount > 0) tags.add("time-sensitive")

        return AiAnalysisResult(
            predictedCategory = category,
            urgencyScore      = baseUrgency,
            urgencyLevel      = level,
            automatedTags     = tags.distinct()
        )
    }

    fun simulateTranslation(text: String, targetLanguage: String): String {
        val lowerText = text.lowercase(Locale.getDefault()).trim()

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
                ?: "[Urdu]: $text"
            else   -> "[Translated]: $text"
        }
    }
}
