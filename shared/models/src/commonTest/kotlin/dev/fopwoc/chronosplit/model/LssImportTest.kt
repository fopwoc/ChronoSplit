package dev.fopwoc.chronosplit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class LssImportTest {
    @Test
    fun importsGameCategoryPersonalBestAndGoldTimes() {
        val definition = parseLssRun(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Run version="1.8.0">
              <GameIcon />
              <GameName>Deadlock</GameName>
              <CategoryName>Custom Game Any%</CategoryName>
              <Metadata><Run id="" /><Platform usesEmulator="False" /></Metadata>
              <LayoutPath />
              <Offset>00:00:00.000000000</Offset>
              <AttemptCount>4</AttemptCount>
              <Segments>
                <Segment>
                  <Name>first &amp; ready</Name>
                  <Icon />
                  <SplitTimes><SplitTime name="Personal Best"><RealTime>00:00:01.000000000</RealTime></SplitTime></SplitTimes>
                  <BestSegmentTime><RealTime>00:00:00.900000000</RealTime></BestSegmentTime>
                </Segment>
                <Segment>
                  <Name>Second</Name>
                  <SplitTimes><SplitTime name="Personal Best"><RealTime>00:00:02.000000000</RealTime></SplitTime></SplitTimes>
                  <BestSegmentTime><RealTime>00:00:01.000000000</RealTime></BestSegmentTime>
                </Segment>
              </Segments>
            </Run>
            """.trimIndent(),
        )

        assertEquals("Deadlock", definition.gameName)
        assertEquals("Custom Game Any%", definition.categoryName)
        assertEquals("Deadlock", definition.title)
        assertEquals(4, definition.attemptCount)
        assertEquals(0, definition.offsetMilliseconds)
        assertEquals("first & ready", definition.segments.first().title)
        assertEquals(900, definition.segments.first().goldTimeMilliseconds)
        assertEquals(1_000, definition.segments.first().personalBestTimeMilliseconds)
        assertEquals(1_000, definition.segments[1].goldTimeMilliseconds)
        assertEquals(2_000, definition.segments[1].personalBestTimeMilliseconds)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun importsWrappedGameAndSegmentPngImages() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x01, 0x02, 0x03,
        )
        val wrappedImage = "A".repeat(212) + Base64.encode(
            byteArrayOf(0, 0) + png + byteArrayOf(0),
        )

        val definition = parseLssRun(
            """
            <Run version="1.8.0">
              <GameIcon><![CDATA[$wrappedImage]]></GameIcon>
              <GameName>Minecraft</GameName>
              <CategoryName>Any% Glitchless</CategoryName>
              <AttemptCount>283</AttemptCount>
              <Segments>
                <Segment>
                  <Name>Overworld</Name>
                  <Icon><![CDATA[$wrappedImage]]></Icon>
                  <SplitTimes />
                  <BestSegmentTime />
                </Segment>
              </Segments>
            </Run>
            """.trimIndent(),
        )

        val expected = Base64.encode(png)
        assertEquals(expected, definition.gameIconPngBase64)
        assertEquals(expected, definition.segments.single().iconPngBase64)
        assertEquals(283, definition.attemptCount)
    }

    @Test
    @OptIn(ExperimentalEncodingApi::class)
    fun exportedRunRoundTripsImagesSplitsAndAttemptHistory() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3,
        )
        val image = Base64.encode(png)
        val definition = RunDefinition(
            id = "minecraft-any",
            title = "Any% Glitchless",
            gameName = "Minecraft",
            categoryName = "Any% Glitchless",
            gameIconPngBase64 = image,
            attemptCount = 12,
            segments = listOf(
                SegmentDefinition("overworld", "Overworld", 900, 1_000, image),
                SegmentDefinition("nether", "Nether", 1_800, 3_000, image),
            ),
        )
        val attempt = AttemptRecord(
            id = "attempt-1",
            definition = definition,
            startedAtEpochMilliseconds = 1_735_732_800_000,
            completedAtEpochMilliseconds = 1_735_732_803_100,
            results = listOf(
                SegmentResult("overworld", 1_100, 1_100),
                SegmentResult("nether", 2_000, 3_100),
            ),
            elapsedMilliseconds = 3_100,
        )

        val exported = exportLssDocument(definition, listOf(attempt))
        val imported = parseLssDocument(exported)

        assertEquals("Minecraft", imported.definition.gameName)
        assertEquals(image, imported.definition.gameIconPngBase64)
        assertEquals(image, imported.definition.segments.first().iconPngBase64)
        assertEquals(3_000, imported.definition.segments.last().personalBestTimeMilliseconds)
        assertEquals(1, imported.attempts.size)
        assertEquals(3_100, imported.attempts.single().elapsedMilliseconds)
        assertEquals(2_000, imported.attempts.single().results.last().segmentDurationMilliseconds)
        assertEquals(1_735_732_800_000, imported.attempts.single().startedAtEpochMilliseconds)
        assertNotNull(imported.attempts.single().completedAtEpochMilliseconds)
    }
}
