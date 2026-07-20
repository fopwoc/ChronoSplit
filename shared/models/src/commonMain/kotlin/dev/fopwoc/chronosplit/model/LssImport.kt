package dev.fopwoc.chronosplit.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Thrown when an imported LiveSplit run cannot be understood. */
class LssImportException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Imports the portable XML run format written by LiveSplit.
 *
 * The parser intentionally lives in common code so the same `.lss` file produces the
 * same configuration on Android and iOS. It accepts the standard Run/Segments structure
 * and ignores optional fields that are not needed by the timer.
 */
data class LssRunDocument(
    val definition: RunDefinition,
    val attempts: List<AttemptRecord>,
)

fun parseLssRun(content: String): RunDefinition = parseLssDocument(content).definition

fun parseLssDocument(content: String): LssRunDocument {
    val root = try {
        parseXml(content.removePrefix("\uFEFF"))
    } catch (error: LssImportException) {
        throw error
    } catch (error: Throwable) {
        throw LssImportException("Run is not valid XML", error)
    }

    if (root.name != "Run") {
        throw LssImportException("Run XML must have a Run root element")
    }

    val gameName = root.childText("GameName")?.takeIf(String::isNotBlank)
    val categoryName = root.childText("CategoryName")?.takeIf(String::isNotBlank)
    val title = gameName ?: categoryName ?: "Imported Run"
    val segmentsNode = root.child("Segments")
        ?: throw LssImportException("Run must contain a Segments element")
    val segmentNodes = segmentsNode.children("Segment")
    if (segmentNodes.isEmpty()) {
        throw LssImportException("Run must contain at least one segment")
    }

    val segments = segmentNodes.mapIndexed { index, node ->
        val name = node.childText("Name")?.takeIf(String::isNotBlank)
            ?: throw LssImportException("Segment ${index + 1} must have a name")
        SegmentDefinition(
            id = "segment-${index + 1}",
            title = name,
            goldTimeMilliseconds = node.child("BestSegmentTime")?.lssTimeMilliseconds(),
            personalBestTimeMilliseconds = node.personalBestTimeMilliseconds(),
            iconPngBase64 = node.childText("Icon")?.decodeLssImage(),
        )
    }

    val importedId = root.attributes["id"]
        ?: root.child("Metadata")?.child("Run")?.attributes?.get("id")
    val definition = RunDefinition(
        id = importedId?.takeIf(String::isNotBlank) ?: configurationIdForTitle(title),
        title = title,
        segments = segments,
        gameName = gameName,
        categoryName = categoryName,
        gameIconPngBase64 = root.childText("GameIcon")?.decodeLssImage(),
        attemptCount = root.childText("AttemptCount")?.toIntOrNull() ?: 0,
        offsetMilliseconds = root.child("Offset")?.lssTimeMilliseconds() ?: 0,
    )
    return LssRunDocument(
        definition = definition,
        attempts = root.importAttemptHistory(definition, segmentNodes),
    )
}

private fun XmlNode.importAttemptHistory(
    definition: RunDefinition,
    segmentNodes: List<XmlNode>,
): List<AttemptRecord> {
    val historyNodes = child("AttemptHistory")?.children("Attempt").orEmpty()
    val segmentHistory = segmentNodes.map { segment ->
        segment.child("SegmentHistory")?.children("Time").orEmpty().associate { time ->
            time.attributes["id"].orEmpty() to time.lssTimeMilliseconds()
        }
    }

    return historyNodes.mapNotNull { attempt ->
        val attemptIndex = attempt.attributes["id"]?.toIntOrNull() ?: return@mapNotNull null
        var elapsed = 0L
        val results = buildList {
            for ((segmentIndex, segment) in definition.segments.withIndex()) {
                val duration = segmentHistory.getOrNull(segmentIndex)?.get(attemptIndex.toString()) ?: break
                elapsed += duration
                add(
                    SegmentResult(
                        segmentId = segment.id,
                        segmentDurationMilliseconds = duration,
                        elapsedAtEndMilliseconds = elapsed,
                        isBestSegment = segment.goldTimeMilliseconds?.let { duration <= it } == true,
                    ),
                )
            }
        }
        val total = attempt.lssTimeMilliseconds() ?: results.lastOrNull()?.elapsedAtEndMilliseconds
        val started = attempt.attributes["started"]?.parseLssDateMilliseconds() ?: 0L
        val ended = attempt.attributes["ended"]?.parseLssDateMilliseconds()
        AttemptRecord(
            id = "${definition.id}-lss-$attemptIndex",
            definition = definition,
            startedAtEpochMilliseconds = started,
            completedAtEpochMilliseconds = ended ?: started.takeIf { total != null && it > 0 },
            results = results,
            elapsedMilliseconds = total,
        )
    }
}

fun exportLssDocument(
    definition: RunDefinition,
    attempts: List<AttemptRecord> = emptyList(),
): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<Run version=\"1.8.0\">\n")
    appendLssImage("GameIcon", definition.gameIconPngBase64, "  ")
    appendXmlElement("GameName", definition.gameName.orEmpty(), "  ")
    appendXmlElement("CategoryName", definition.categoryName ?: definition.title, "  ")
    append("  <Metadata><Run id=\"")
    append(definition.id.escapeXmlAttribute())
    append("\" /><Platform usesEmulator=\"False\" /><Region /><Variables /></Metadata>\n")
    append("  <LayoutPath />\n")
    appendXmlElement("Offset", formatLssDuration(definition.offsetMilliseconds), "  ")
    appendXmlElement("AttemptCount", maxOf(definition.attemptCount, attempts.size).toString(), "  ")
    append("  <AttemptHistory>\n")
    attempts.sortedBy(AttemptRecord::startedAtEpochMilliseconds).forEachIndexed { index, attempt ->
        val id = index + 1
        append("    <Attempt id=\"").append(id).append('"')
        if (attempt.startedAtEpochMilliseconds > 0) {
            append(" started=\"")
                .append(formatLssDate(attempt.startedAtEpochMilliseconds))
                .append("\" isStartedSynced=\"True\"")
        }
        attempt.completedAtEpochMilliseconds?.takeIf { it > 0 }?.let { completed ->
            append(" ended=\"").append(formatLssDate(completed)).append("\" isEndedSynced=\"True\"")
        }
        val elapsed = attempt.elapsedMilliseconds ?: attempt.results.lastOrNull()?.elapsedAtEndMilliseconds
        if (elapsed == null) {
            append(" />\n")
        } else {
            append(">\n")
            appendXmlElement("RealTime", formatLssDuration(elapsed), "      ")
            append("    </Attempt>\n")
        }
    }
    append("  </AttemptHistory>\n")
    append("  <Segments>\n")
    definition.segments.forEachIndexed { segmentIndex, segment ->
        append("    <Segment>\n")
        appendXmlElement("Name", segment.title, "      ")
        appendLssImage("Icon", segment.iconPngBase64, "      ")
        append("      <SplitTimes>\n")
        append("        <SplitTime name=\"Personal Best\">")
        segment.personalBestTimeMilliseconds?.let { split ->
            append("<RealTime>").append(formatLssDuration(split)).append("</RealTime>")
        }
        append("</SplitTime>\n")
        append("      </SplitTimes>\n")
        append("      <BestSegmentTime>")
        segment.goldTimeMilliseconds?.let { best ->
            append("<RealTime>").append(formatLssDuration(best)).append("</RealTime>")
        }
        append("</BestSegmentTime>\n")
        append("      <SegmentHistory>\n")
        attempts.sortedBy(AttemptRecord::startedAtEpochMilliseconds).forEachIndexed { index, attempt ->
            attempt.results.getOrNull(segmentIndex)?.let { result ->
                append("        <Time id=\"").append(index + 1).append("\"><RealTime>")
                    .append(formatLssDuration(result.segmentDurationMilliseconds))
                    .append("</RealTime></Time>\n")
            }
        }
        append("      </SegmentHistory>\n")
        append("    </Segment>\n")
    }
    append("  </Segments>\n")
    append("  <AutoSplitterSettings />\n")
    append("</Run>\n")
}

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeLssImage(): String? {
    val encoded = trim()
    if (encoded.length < LssImageHeaderLength + 4) return null

    return runCatching {
        val wrappedImage = Base64.decode(encoded.substring(LssImageHeaderLength))
        if (wrappedImage.size < 4) return null
        val image = wrappedImage.copyOfRange(2, wrappedImage.lastIndex)
        if (!image.startsWith(PngSignature)) return null
        Base64.encode(image)
    }.getOrNull()
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

@OptIn(ExperimentalEncodingApi::class)
private fun StringBuilder.appendLssImage(
    tag: String,
    pngBase64: String?,
    indent: String,
) {
    val png = pngBase64?.let { runCatching { Base64.decode(it) }.getOrNull() }
    if (png == null || !png.startsWith(PngSignature)) {
        append(indent).append('<').append(tag).append(" />\n")
        return
    }
    val header = Base64.decode(LssImageHeaderBase64)
    val length = png.size
    val wrapped = header + byteArrayOf(
        (length and 0xFF).toByte(),
        ((length ushr 8) and 0xFF).toByte(),
        ((length ushr 16) and 0xFF).toByte(),
        ((length ushr 24) and 0xFF).toByte(),
        0x02,
    ) + png + byteArrayOf(0x0B)
    append(indent).append('<').append(tag).append("><![CDATA[")
        .append(Base64.encode(wrapped))
        .append("]]></").append(tag).append(">\n")
}

private fun StringBuilder.appendXmlElement(tag: String, value: String, indent: String) {
    append(indent).append('<').append(tag).append('>')
        .append(value.escapeXmlText())
        .append("</").append(tag).append(">\n")
}

private fun String.escapeXmlText(): String = buildString(length) {
    this@escapeXmlText.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                else -> character
            },
        )
    }
}

private fun String.escapeXmlAttribute(): String = escapeXmlText()
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun formatLssDuration(milliseconds: Long): String {
    val sign = if (milliseconds < 0) "-" else ""
    val absolute = kotlin.math.abs(milliseconds)
    val hours = absolute / 3_600_000
    val minutes = absolute / 60_000 % 60
    val seconds = absolute / 1_000 % 60
    val fraction = absolute % 1_000
    return "$sign${hours.toString().padStart(2, '0')}:" +
        "${minutes.toString().padStart(2, '0')}:" +
        "${seconds.toString().padStart(2, '0')}." +
        "${fraction.toString().padStart(3, '0')}0000"
}

private val LssDateFormat = LocalDateTime.Format {
    monthNumber(Padding.ZERO)
    char('/')
    day(Padding.ZERO)
    char('/')
    year()
    char(' ')
    hour(Padding.ZERO)
    char(':')
    minute(Padding.ZERO)
    char(':')
    second(Padding.ZERO)
}

private fun String.parseLssDateMilliseconds(): Long? = runCatching {
    LssDateFormat.parse(this).toInstant(TimeZone.UTC).toEpochMilliseconds()
}.getOrNull()

private fun formatLssDate(epochMilliseconds: Long): String = LssDateFormat.format(
    Instant.fromEpochMilliseconds(epochMilliseconds).toLocalDateTime(TimeZone.UTC),
)

private const val LssImageHeaderLength = 212
private const val LssImageHeaderBase64 =
    "AAEAAAD/////AQAAAAAAAAAMAgAAAFFTeXN0ZW0uRHJhd2luZywgVmVyc2lvbj00LjAuMC4wLCBDdWx0dXJlPW5ldXRyYWwsIFB1YmxpY0tleVRva2VuPWIwM2Y1ZjdmMTFkNTBhM2EFAQAAABVTeXN0ZW0uRHJhd2luZy5CaXRtYXABAAAABERhdGEHAgIAAAAJAwAAAA8DAAAA"
private val PngSignature = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

private fun XmlNode.personalBestTimeMilliseconds(): Long? {
    val splitTimes = child("SplitTimes") ?: return null
    val personalBest = splitTimes.children("SplitTime")
        .firstOrNull { it.attributes["name"] == "Personal Best" }
        ?: splitTimes.children("SplitTime").firstOrNull()
    return personalBest?.lssTimeMilliseconds()
}

private fun XmlNode.lssTimeMilliseconds(): Long? {
    val time = child("RealTime") ?: child("GameTime") ?: return null
    return parseLssDurationMilliseconds(time.textValue())
}

private fun parseLssDurationMilliseconds(value: String): Long? {
    val raw = value.trim()
    if (raw.isEmpty()) return null

    val negative = raw.startsWith('-')
    val unsigned = raw.removePrefix("-").removePrefix("+")
    val parts = unsigned.split(':')
    if (parts.size !in 1..3) {
        throw LssImportException("Unsupported LiveSplit time: $value")
    }

    val secondsPart = parts.last()
    val secondsAndFraction = secondsPart.split('.', limit = 2)
    val seconds = secondsAndFraction[0].toLongOrNull()
        ?: throw LssImportException("Unsupported LiveSplit time: $value")
    val milliseconds = secondsAndFraction.getOrNull(1)
        ?.let { fraction ->
            if (fraction.isEmpty() || fraction.any { it !in '0'..'9' }) {
                throw LssImportException("Unsupported LiveSplit time: $value")
            }
            fraction.take(3).padEnd(3, '0').toLong()
        }
        ?: 0L
    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
    val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
    if (parts.dropLast(1).any { it.toLongOrNull() == null } || seconds !in 0..59 || minutes !in 0..59) {
        throw LssImportException("Unsupported LiveSplit time: $value")
    }

    val total = ((hours * 60 + minutes) * 60 + seconds) * 1000 + milliseconds
    return if (negative) -total else total
}

private data class XmlNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlNode>,
    val text: String,
) {
    fun child(childName: String): XmlNode? = children.firstOrNull { it.name == childName }

    fun children(childName: String): List<XmlNode> = children.filter { it.name == childName }

    fun textValue(): String = text.trim()

    fun childText(childName: String): String? = child(childName)?.textValue()
}

private class MutableXmlNode(
    val name: String,
    val attributes: Map<String, String>,
) {
    val children = mutableListOf<XmlNode>()
    val text = StringBuilder()

    fun freeze(): XmlNode = XmlNode(name, attributes, children.toList(), text.toString())
}

private fun parseXml(content: String): XmlNode {
    val stack = mutableListOf<MutableXmlNode>()
    var root: XmlNode? = null
    var index = 0

    fun attach(node: XmlNode) {
        val parent = stack.lastOrNull()
        if (parent == null) {
            if (root != null) throw LssImportException("XML must contain one root element")
            root = node
        } else {
            parent.children += node
        }
    }

    while (index < content.length) {
        if (content[index] != '<') {
            val textEnd = content.indexOf('<', index).let { if (it == -1) content.length else it }
            val text = decodeXmlEntities(content.substring(index, textEnd))
            if (stack.isNotEmpty()) stack.last().text.append(text)
            else if (text.isNotBlank()) throw LssImportException("Text found outside XML root")
            index = textEnd
            continue
        }

        when {
            content.startsWith("<!--", index) -> {
                val end = content.indexOf("-->", index + 4)
                if (end == -1) throw LssImportException("Unclosed XML comment")
                index = end + 3
            }

            content.startsWith("<?", index) -> {
                val end = content.indexOf("?>", index + 2)
                if (end == -1) throw LssImportException("Unclosed XML declaration")
                index = end + 2
            }

            content.startsWith("<![CDATA[", index) -> {
                val end = content.indexOf("]]>", index + 9)
                if (end == -1) throw LssImportException("Unclosed CDATA section")
                if (stack.isNotEmpty()) stack.last().text.append(content.substring(index + 9, end))
                index = end + 3
            }

            content.startsWith("</", index) -> {
                val end = content.indexOf('>', index + 2)
                if (end == -1) throw LssImportException("Unclosed XML closing tag")
                val name = content.substring(index + 2, end).trim()
                val node = stack.removeLastOrNull()
                    ?: throw LssImportException("Unexpected closing tag: $name")
                if (node.name != name) {
                    throw LssImportException("Closing tag $name does not match ${node.name}")
                }
                attach(node.freeze())
                index = end + 1
            }

            content.startsWith("<!", index) -> {
                val end = content.indexOf('>', index + 2)
                if (end == -1) throw LssImportException("Unclosed XML declaration")
                index = end + 1
            }

            else -> {
                val end = findTagEnd(content, index + 1)
                val rawTag = content.substring(index + 1, end).trim()
                val selfClosing = rawTag.endsWith('/')
                val header = rawTag.removeSuffix("/").trim()
                val (name, attributes) = parseTagHeader(header)
                val node = MutableXmlNode(name, attributes)
                if (selfClosing) attach(node.freeze()) else stack += node
                index = end + 1
            }
        }
    }

    if (stack.isNotEmpty()) throw LssImportException("XML contains an unclosed element")
    return root ?: throw LssImportException("XML does not contain a root element")
}

private fun findTagEnd(content: String, start: Int): Int {
    var quote: Char? = null
    for (index in start until content.length) {
        val character = content[index]
        if (quote == null && (character == '\'' || character == '"')) quote = character
        else if (quote == character) quote = null
        else if (quote == null && character == '>') return index
    }
    throw LssImportException("Unclosed XML opening tag")
}

private fun parseTagHeader(header: String): Pair<String, Map<String, String>> {
    var index = 0
    fun skipWhitespace() {
        while (index < header.length && header[index].isWhitespace()) index += 1
    }

    skipWhitespace()
    val nameStart = index
    while (index < header.length && !header[index].isWhitespace()) index += 1
    val name = header.substring(nameStart, index).removeSuffix("/")
    if (name.isBlank()) throw LssImportException("XML element has no name")

    val attributes = mutableMapOf<String, String>()
    while (index < header.length) {
        skipWhitespace()
        if (index >= header.length) break
        val attributeStart = index
        while (index < header.length && !header[index].isWhitespace() && header[index] != '=') index += 1
        val attributeName = header.substring(attributeStart, index)
        skipWhitespace()
        if (index >= header.length || header[index] != '=') {
            throw LssImportException("XML attribute $attributeName has no value")
        }
        index += 1
        skipWhitespace()
        if (index >= header.length || (header[index] != '\'' && header[index] != '"')) {
            throw LssImportException("XML attribute $attributeName is not quoted")
        }
        val quote = header[index]
        index += 1
        val valueStart = index
        while (index < header.length && header[index] != quote) index += 1
        if (index >= header.length) throw LssImportException("Unclosed XML attribute $attributeName")
        attributes[attributeName] = decodeXmlEntities(header.substring(valueStart, index))
        index += 1
    }
    return name to attributes
}

private fun decodeXmlEntities(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
