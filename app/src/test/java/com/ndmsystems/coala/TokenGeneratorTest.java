package com.ndmsystems.coala;

import com.ndmsystems.coala.helpers.TokenGenerator;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

/**
 * Created by bas on 15.11.16.
 */

public class TokenGeneratorTest {

    @Test
    public void differentToken() {
        byte[] token1 = TokenGenerator.getToken();
        byte[] token2 = TokenGenerator.getToken();

        Assert.assertEquals(token1.length, 8);
        Assert.assertEquals(token2.length, 8);
        assertThat(token1, not(equalTo(token2)));
    }
}
