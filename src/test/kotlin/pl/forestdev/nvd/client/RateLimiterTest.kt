package pl.forestdev.nvd.client

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

class RateLimiterTest : StringSpec({
    "should limit rate" {
        val limiter = RateLimiter(200)
        
        var count = 0
        val t = measureTimeMillis {
            runBlocking {
                repeat(3) {
                    limiter.withPermit { count++ }
                }
            }
        }
        
        count shouldBe 3
        t shouldBeGreaterThanOrEqual 400L
    }
})
