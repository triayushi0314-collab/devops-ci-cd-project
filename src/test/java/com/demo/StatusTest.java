package com.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class StatusTest {
    @Test
    void healthMessageIsOk() {
        assertEquals("OK", Status.message());
    }
}
