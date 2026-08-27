package com.ndmsystems.coala.resource_discovery

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

object ResourceDiscoveryHelperTest : Spek({

    describe("ResourceDiscoveryHelper") {

        it("keeps one entry per peer") {
            val helper = ResourceDiscoveryHelper()

            helper.addResult(result(0))
            helper.addResult(result(0))
            helper.addResult(result(1))

            assertEquals(2, helper.resultsList.size)
        }

        it("keeps the order peers answered in") {
            val helper = ResourceDiscoveryHelper()

            helper.addResult(result(2))
            helper.addResult(result(0))
            helper.addResult(result(1))

            assertEquals(listOf(result(2), result(0), result(1)), helper.resultsList.toList())
        }

        it("empties on clear") {
            val helper = ResourceDiscoveryHelper()
            helper.addResult(result(0))

            helper.clear()

            assertTrue(helper.resultsList.isEmpty())
        }

        it("hands out whole results while a discovery run is in progress") {
            val helper = ResourceDiscoveryHelper()
            val stop = AtomicBoolean(false)
            val writerFailure = AtomicReference<Throwable?>(null)
            // Stands in for the receiving thread appending answers while runs start and end.
            val writer = Thread {
                runCatching {
                    var written = 0
                    while (!stop.get()) {
                        helper.addResult(result(written % PEERS))
                        if (++written % PEERS == 0) helper.clear()
                    }
                }.onFailure { writerFailure.set(it) }
            }

            writer.start()
            try {
                repeat(READS) {
                    // Touching every element is the point: an unsynchronised ArrayList hands back
                    // an array padded with nulls when clear() lands between its size read and its
                    // copy, and the caller then trips over a null that its type says cannot exist.
                    helper.resultsList.toList().forEach { require(it.payload.isNotEmpty()) }
                }
            } finally {
                stop.set(true)
                writer.join()
            }

            assertNull(writerFailure.get())
        }
    }
})

private const val PEERS = 40
private const val READS = 20_000

private fun result(index: Int) =
    ResourceDiscoveryResult("""{"cid":"peer-$index"}""", InetSocketAddress("10.0.0.1", 5683 + index))
