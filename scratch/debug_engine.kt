import com.example.irondiary.util.IronIntelligenceEngine
import com.example.irondiary.util.LocalDataBundle
import com.example.irondiary.data.DailyLog

fun main() {
    val logs = listOf(
        DailyLog(date = "2026-05-01", weight = 100f),
        DailyLog(date = "2026-05-08", weight = 99f)
    )
    val bundle = LocalDataBundle(logs, emptyList(), emptyList())
    val response = IronIntelligenceEngine.processQuery("When will I reach 90kg?", bundle)
    println("DEBUG RESPONSE: ${response.text}")
}
