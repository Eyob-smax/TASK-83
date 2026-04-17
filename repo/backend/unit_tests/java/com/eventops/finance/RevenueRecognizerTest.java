package com.eventops.finance;

import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.finance.recognition.RevenueRecognizer;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class RevenueRecognizerTest {

    private final RevenueRecognizer recognizer = new RevenueRecognizer();

    @Test
    void immediate_returnsSameDateForBoth() {
        LocalDate postingDate = LocalDate.of(2026, 4, 15);
        var period = recognizer.computePeriod(RevenueRecognitionMethod.IMMEDIATE, postingDate, null, null);
        assertEquals(postingDate, period.startDate());
        assertEquals(postingDate, period.endDate());
    }

    @Test
    void overSessionDates_usesSessionDates() {
        LocalDate posting = LocalDate.of(2026, 4, 1);
        LocalDate sessionStart = LocalDate.of(2026, 4, 10);
        LocalDate sessionEnd = LocalDate.of(2026, 4, 12);
        var period = recognizer.computePeriod(RevenueRecognitionMethod.OVER_SESSION_DATES, posting, sessionStart, sessionEnd);
        assertEquals(sessionStart, period.startDate());
        assertEquals(sessionEnd, period.endDate());
    }

    @Test
    void overSessionDates_fallsBackToPostingDate_whenSessionDatesNull() {
        LocalDate posting = LocalDate.of(2026, 4, 1);
        var period = recognizer.computePeriod(RevenueRecognitionMethod.OVER_SESSION_DATES, posting, null, null);
        assertEquals(posting, period.startDate());
        assertEquals(posting, period.endDate());
    }

    @Test
    void overSessionDates_usesStartOnly_whenEndIsNull() {
        LocalDate posting = LocalDate.of(2026, 4, 1);
        LocalDate sessionStart = LocalDate.of(2026, 4, 10);
        var period = recognizer.computePeriod(RevenueRecognitionMethod.OVER_SESSION_DATES, posting, sessionStart, null);
        assertEquals(sessionStart, period.startDate());
        assertEquals(posting, period.endDate());
    }

    @Test
    void overSessionDates_usesEndOnly_whenStartIsNull() {
        LocalDate posting = LocalDate.of(2026, 4, 1);
        LocalDate sessionEnd = LocalDate.of(2026, 4, 12);
        var period = recognizer.computePeriod(RevenueRecognitionMethod.OVER_SESSION_DATES, posting, null, sessionEnd);
        assertEquals(posting, period.startDate());
        assertEquals(sessionEnd, period.endDate());
    }

    @Test
    void recognitionPeriod_record_equalityAndGetters() {
        var a = new RevenueRecognizer.RecognitionPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));
        var b = new RevenueRecognizer.RecognitionPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));
        assertEquals(a, b);
        assertEquals(LocalDate.of(2026, 4, 1), a.startDate());
        assertEquals(LocalDate.of(2026, 4, 2), a.endDate());
    }
}
