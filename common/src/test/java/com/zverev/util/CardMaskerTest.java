package com.zverev.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardMaskerTest {

    @Test
    @DisplayName("long token - only last 4 chars visible")
    void longToken() {
        assertThat(CardMasker.mask("card-123456")).isEqualTo("**** 3456");
    }

    @Test
    @DisplayName("token of exactly 4 chars - visible as is")
    void fourChars() {
        assertThat(CardMasker.mask("1234")).isEqualTo("**** 1234");
    }

    @Test
    @DisplayName("short token - fully hidden")
    void shortToken() {
        assertThat(CardMasker.mask("abc")).isEqualTo("****");
    }

    @Test
    @DisplayName("null token - fully hidden")
    void nullToken() {
        assertThat(CardMasker.mask(null)).isEqualTo("****");
    }
}
