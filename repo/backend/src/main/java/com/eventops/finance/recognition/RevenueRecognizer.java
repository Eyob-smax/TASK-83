package com.eventops.finance.recognition;

import com.eventops.domain.finance.RevenueRecognitionMethod;
import java.time.LocalDate;

public class RevenueRecognizer {

    public record RecognitionPeriod(LocalDate startDate, LocalDate endDate) {}

    public RecognitionPeriod computePeriod(RevenueRecognitionMethod method,
                                            LocalDate postingDate,
                                            LocalDate sessionStartDate,
                                            LocalDate sessionEndDate) {
        return switch (method) {
            case IMMEDIATE -> new RecognitionPeriod(postingDate, postingDate);
            case OVER_SESSION_DATES -> new RecognitionPeriod(
                sessionStartDate != null ? sessionStartDate : postingDate,
                sessionEndDate != null ? sessionEndDate : postingDate
            );
        };
    }
}
