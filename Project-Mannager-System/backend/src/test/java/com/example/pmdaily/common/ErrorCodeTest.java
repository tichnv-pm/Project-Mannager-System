package com.example.pmdaily.common;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void allCodes_haveStatusAndMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getStatus()).as("status của %s", code).isNotNull();
            assertThat(code.getDefaultMessage())
                    .as("message của %s", code)
                    .isNotBlank();
        }
    }

    @Test
    void codeNames_areUniqueAndUpperCase() {
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet()))
                .hasSize(ErrorCode.values().length);
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.name()).isEqualTo(code.name().toUpperCase());
        }
    }
}
