package com.ndmsystems.coala.layers.arq

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


/*
 * Created by Evgenii Stepanov on 04.09.19
 */

object SlidingWindowTest : Spek({


    describe("testing simple sliding window") {
        val size = 10
        val slidingWindow by memoized { SlidingWindow<Byte>(size) }

        beforeEach {
            val dummyData = ByteArray(size).mapIndexed { index, _ -> index.toByte() }
            dummyData.forEachIndexed { index, byte -> slidingWindow.set(index, byte) }
        }


        context("test boundary elements to expected values") {

            it("min value should equals") {
                slidingWindow.set(0, Byte.MIN_VALUE)
                val value = slidingWindow.getValue(0)

                assertEquals(Byte.MIN_VALUE, value)
            }

            it("mid value should equals") {
                slidingWindow.set(slidingWindow.size / 2, 0)
                val value = slidingWindow.getValue(slidingWindow.size / 2)

                assertEquals(0, value)
            }

            it("max value should equals") {
                slidingWindow.set(slidingWindow.tail(), Byte.MAX_VALUE)
                val value = slidingWindow.getValue(slidingWindow.tail())

                assertEquals(Byte.MAX_VALUE, value)
            }

        }
    }

    describe("test advance() function") {

        val size = 10
        val slidingWindow by memoized { SlidingWindow<Byte>(size) }

        beforeEach {
            ByteArray(size)
                    .mapIndexed { index, _ -> index.toByte() }
                    .forEachIndexed { index, byte -> slidingWindow.set(index, byte) }

        }

        it("advance should return value") {
            val firstByte = slidingWindow.advance()

            assertNotNull(firstByte)
        }

        it("test advance() called twice then offset equals 2") {
            slidingWindow.apply {
                advance()
                advance()
            }

            assertEquals(2, slidingWindow.offset)
        }

        for (i in 0 until size) {

            it("test advance() called $i times then element at position [$i] equals null") {
                for (j in 0..i) slidingWindow.advance()

                (slidingWindow.getValue(slidingWindow.size - slidingWindow.offset))
            }
        }

    }

    describe("testing offset when adding items") {
        val slidingWindow = SlidingWindow<Byte>(3, 3)
        val testByte = 128.toByte()

        it("test when size = 3 and offset = 3 then added element should be at 0 position") {
            slidingWindow.set(3, testByte)

            assertEquals(testByte, slidingWindow.getValue(0))
        }

    }

    describe("test tail() function") {

        val size = 5
        val offset = 3
        val slidingWindow by memoized { SlidingWindow<Byte>(size, offset) }

        it("test tail() when size = size and offset = 3 then tails equal 7") {

            val tail = slidingWindow.tail()

            assertEquals(7, tail)
        }

    }


})