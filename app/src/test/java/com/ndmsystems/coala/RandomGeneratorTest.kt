package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.RandomGenerator.getRandom
import org.hamcrest.core.IsEqual
import org.hamcrest.core.IsNot
import org.junit.Assert
import org.junit.Test

class RandomGeneratorTest {
    @Test
    fun differentToken() {
        val token1 = getRandom(8)
        val token2 = getRandom(8)
        Assert.assertEquals(token1.size.toLong(), 8)
        Assert.assertEquals(token2.size.toLong(), 8)
        Assert.assertThat(token1, IsNot.not(IsEqual.equalTo(token2)))
    }
}