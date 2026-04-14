package com.eventops.common;

import com.eventops.common.dto.PagedResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PagedResponseTest {

    @Test
    void totalPages_calculatedCorrectly() {
        PagedResponse<String> resp = new PagedResponse<>(List.of("a","b"), 0, 10, 25);
        assertEquals(3, resp.getTotalPages());
    }

    @Test
    void totalPages_zeroWhenSizeZero() {
        PagedResponse<String> resp = new PagedResponse<>(List.of(), 0, 0, 0);
        assertEquals(0, resp.getTotalPages());
    }

    @Test
    void totalPages_exactDivision() {
        PagedResponse<String> resp = new PagedResponse<>(List.of("a"), 0, 5, 10);
        assertEquals(2, resp.getTotalPages());
    }
}
