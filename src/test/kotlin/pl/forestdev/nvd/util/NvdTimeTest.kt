package pl.forestdev.nvd.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class NvdTimeTest : StringSpec({
    "windows should return correct number of pairs" {
        val start = "2023-01-01T00:00:00.000"
        val end = "2023-01-11T00:00:00.000"
        // 10 days, maxDays = 3
        val windows = NvdTime.windows(start, end, maxDays = 3)
        
        windows shouldHaveSize 4
        windows[0] shouldBe ("2023-01-01T00:00:00.000" to "2023-01-04T00:00:00.000")
        windows[1] shouldBe ("2023-01-04T00:00:00.000" to "2023-01-07T00:00:00.000")
        windows[2] shouldBe ("2023-01-07T00:00:00.000" to "2023-01-10T00:00:00.000")
        windows[3] shouldBe ("2023-01-10T00:00:00.000" to "2023-01-11T00:00:00.000")
    }

    "windows should return empty list if start is after end" {
        val start = "2023-01-11T00:00:00.000"
        val end = "2023-01-01T00:00:00.000"
        NvdTime.windows(start, end) shouldHaveSize 0
    }

    "format should return correct string" {
        val instant = Instant.parse("2023-01-01T12:00:00Z")
        NvdTime.format(instant) shouldBe "2023-01-01T12:00:00.000"
    }
})
