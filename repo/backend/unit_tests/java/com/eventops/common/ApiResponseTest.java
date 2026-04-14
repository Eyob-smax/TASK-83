package com.eventops.common;

import com.eventops.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_setsFieldsCorrectly() {
        ApiResponse<String> resp = ApiResponse.success("data");
        assertTrue(resp.isSuccess());
        assertEquals("data", resp.getData());
        assertNotNull(resp.getTimestamp());
        assertNull(resp.getErrors());
    }

    @Test
    void successWithMessage_setsMessage() {
        ApiResponse<String> resp = ApiResponse.success("data", "OK");
        assertTrue(resp.isSuccess());
        assertEquals("OK", resp.getMessage());
    }

    @Test
    void error_setsFieldsCorrectly() {
        var errors = List.of(new ApiResponse.ApiError("field", "CODE", "msg"));
        ApiResponse<Object> resp = ApiResponse.error("Failed", errors);
        assertFalse(resp.isSuccess());
        assertEquals("Failed", resp.getMessage());
        assertNull(resp.getData());
        assertEquals(1, resp.getErrors().size());
        assertEquals("CODE", resp.getErrors().get(0).getCode());
    }
}
