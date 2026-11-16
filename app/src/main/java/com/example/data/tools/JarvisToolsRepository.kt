package com.example.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class WeatherReport(
    val city: String,
    val country: String,
    val temperatureC: Double,
    val apparentTempC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val conditionLabel: String,
    val highC: Double,
    val lowC: Double,
)

data class SearchHit(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String,
)

data class NewsArticle(
    val title: String,
    val source: String,
    val publishedAt: String,
    val url: String,
)

class JarvisToolsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeatherByCity(cityQuery: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCity = cityQuery.trim().ifBlank { "San Francisco" }
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
                .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                .build()
            val geoBody = client.newCall(geoReq).execute().use { resp ->
                if (!resp.isSuccessful) error("Geocoding failed (${resp.code})")
                resp.body?.string() ?: error("Empty geocoding response")
            }
            val geoJson = JSONObject(geoBody)
            val results = geoJson.optJSONArray("results")
                ?: error("Location '$cleanCity' not found.")
            if (results.length() == 0) error("Location '$cleanCity' not found.")
            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val resolvedName = place.optString("name", cleanCity)
            val country = place.optString("country", "")

            fetchWeatherByCoordinates(lat, lon, resolvedName, country).getOrThrow()
        }
    }

    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        cityName: String = "Current Location",
        country: String = "",
    ): Result<WeatherReport> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Weather telemetry failed (${resp.code})")
                resp.body?.string() ?: error("Empty weather response")
            }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val maxArr = daily.getJSONArray("temperature_2m_max")
            val minArr = daily.getJSONArray("temperature_2m_min")
            val code = current.optInt("weather_code", 0)

            WeatherReport(
                city = cityName,
                country = country,
                temperatureC = current.optDouble("temperature_2m", 0.0),
                apparentTempC = current.optDouble("apparent_temperature", 0.0),
                humidityPercent = current.optInt("relative_humidity_2m", 0),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
                conditionLabel = mapWmoCode(code),
                highC = if (maxArr.length() > 0) maxArr.getDouble(0) else current.optDouble("temperature_2m", 0.0),
                lowC = if (minArr.length() > 0) minArr.getDouble(0) else current.optDouble("temperature_2m", 0.0),
            )
        }
    }

    suspend fun searchWeb(query: String): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim()
            require(cleanQuery.isNotBlank()) { "Search query cannot be blank" }
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val hits = mutableListOf<SearchHit>()

            // 1. DuckDuckGo Instant Answer API (Free, no key)
            runCatching {
                val ddgReq = Request.Builder()
                    .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                    .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                    .build()
                val ddgBody = client.newCall(ddgReq).execute().use { it.body?.string().orEmpty() }
                if (ddgBody.isNotBlank()) {
                    val ddgJson = JSONObject(ddgBody)
                    val abstractText = ddgJson.optString("AbstractText", "")
                    val abstractUrl = ddgJson.optString("AbstractURL", "")
                    val heading = ddgJson.optString("Heading", cleanQuery)
                    if (abstractText.isNotBlank()) {
                        hits.add(
                            SearchHit(
                                title = heading,
                                snippet = abstractText,
                                url = abstractUrl.ifBlank { "https://duckduckgo.com/?q=$encoded" },
                                source = ddgJson.optString("AbstractSource", "DuckDuckGo"),
                            )
                        )
                    }
                    val related = ddgJson.optJSONArray("RelatedTopics")
                    if (related != null) {
                        for (i in 0 until minOf(3, related.length())) {
                            val item = related.optJSONObject(i) ?: continue
                            val text = item.optString("Text", "")
                            val firstUrl = item.optString("FirstURL", "")
                            if (text.isNotBlank() && firstUrl.isNotBlank()) {
                                hits.add(
                                    SearchHit(
                                        title = text.substringBefore(" - ").take(80),
                                        snippet = text,
                                        url = firstUrl,
                                        source = "DuckDuckGo",
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Wikipedia Open Search API (Free, no key)
            runCatching {
                val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=4&format=json&utf8=1&srsearch=$encoded"
                val wikiReq = Request.Builder()
                    .url(wikiUrl)
                    .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                    .build()
                val wikiBody = client.newCall(wikiReq).execute().use { it.body?.string().orEmpty() }
                if (wikiBody.isNotBlank()) {
                    val wikiJson = JSONObject(wikiBody)
                    val searchArr = wikiJson.optJSONObject("query")?.optJSONArray("search")
                    if (searchArr != null) {
                        for (i in 0 until searchArr.length()) {
                            val obj = searchArr.getJSONObject(i)
                            val title = obj.optString("title", "")
                            val rawSnippet = obj.optString("snippet", "")
                                .replace(Regex("<[^>]*>"), "")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")
                            val pageUrl = "https://en.wikipedia.org/wiki/${URLEncoder.encode(title.replace(" ", "_"), "UTF-8")}"
                            if (title.isNotBlank()) {
                                hits.add(
                                    SearchHit(
                                        title = title,
                                        snippet = rawSnippet,
                                        url = pageUrl,
                                        source = "Wikipedia",
                                    )
                                )
                            }
                        }
                    }
                }
            }

            hits.distinctBy { it.url }.take(6)
        }
    }

    suspend fun fetchLatestNews(topic: String = ""): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (topic.isBlank()) {
                "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
            } else {
                val encoded = URLEncoder.encode(topic.trim(), "UTF-8")
                "https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en"
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "JARVIS-Android-Assistant/1.0")
                .build()
            val xml = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("News feed unavailable (${resp.code})")
                resp.body?.string() ?: error("Empty news feed")
            }
            parseRssFeed(xml).take(10)
        }
    }

    private fun parseRssFeed(xml: String): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        var insideItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var source = "News"

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "item" -> {
                            insideItem = true
                            title = ""
                            link = ""
                            pubDate = ""
                            source = "News"
                        }
                        "title" -> if (insideItem) title = parser.nextText().trim()
                        "link" -> if (insideItem) link = parser.nextText().trim()
                        "pubDate" -> if (insideItem) pubDate = parser.nextText().trim()
                        "source" -> if (insideItem) source = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" && insideItem) {
                        if (title.isNotBlank() && link.isNotBlank()) {
                            articles.add(
                                NewsArticle(
                                    title = title.substringBeforeLast(" - "),
                                    source = source.ifBlank { title.substringAfterLast(" - ", "News") },
                                    publishedAt = pubDate.take(22),
                                    url = link,
                                )
                            )
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    fun evaluateMath(expression: String): Result<Double> = runCatching {
        val parser = MathExpressionParser(expression)
        parser.parse()
    }

    fun openUrl(context: Context, url: String): Result<Unit> = runCatching {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openWebSearch(context: Context, query: String): Result<Unit> = runCatching {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        openUrl(context, "https://duckduckgo.com/?q=$encoded").getOrThrow()
    }

    fun openEmailDraft(context: Context, recipient: String, subject: String, body: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (recipient.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSetAlarm(context: Context, hour: Int, minute: Int, label: String): Result<Unit> = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Reminder" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openCalendarEventDraft(context: Context, title: String, description: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "JARVIS Event" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openMapsSearch(context: Context, place: String): Result<Unit> = runCatching {
        val encoded = URLEncoder.encode(place.trim(), "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Atmospheric Nominal"
    }
}

private class MathExpressionParser(private val str: String) {
    private var pos = -1
    private var ch = 0

    private fun nextChar() {
        ch = if (++pos < str.length) str[pos].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (ch == ' '.code) nextChar()
        if (ch == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    fun parse(): Double {
        nextChar()
        val x = parseExpression()
        if (pos < str.length) throw IllegalArgumentException("Unexpected character: '${ch.toChar()}'")
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            when {
                eat('+'.code) -> x += parseTerm()
                eat('-'.code) -> x -= parseTerm()
                else -> return x
            }
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            when {
                eat('*'.code) -> x *= parseFactor()
                eat('/'.code) -> {
                    val denom = parseFactor()
                    if (denom == 0.0) throw ArithmeticException("Division by zero")
                    x /= denom
                }
                eat('%'.code) -> x %= parseFactor()
                else -> return x
            }
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = pos
        if (eat('('.code)) {
            x = parseExpression()
            if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
        } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
            while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
            x = str.substring(startPos, pos).toDouble()
        } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
            while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
            val func = str.substring(startPos, pos).lowercase()
            if (func == "pi") {
                x = Math.PI
            } else if (func == "e") {
                x = Math.E
            } else {
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    "abs" -> abs(x)
                    else -> throw IllegalArgumentException("Unknown function: $func")
                }
            }
        } else {
            throw IllegalArgumentException("Invalid syntax at position $pos")
        }

        if (eat('^'.code)) x = x.pow(parseFactor())
        return x
    }
}
