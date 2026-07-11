package com.videocharter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.videocharter.model.UserAccount;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyQuotaServiceTest {

    private final DailyQuotaService service = new DailyQuotaService();

    @Test
    void shouldReturnCorrectFreeLimitsByWeekday() {
        assertEquals(10, service.freeLimit(LocalDate.of(2026, 7, 6)));
        assertEquals(10, service.freeLimit(LocalDate.of(2026, 7, 7)));
        assertEquals(15, service.freeLimit(LocalDate.of(2026, 7, 8)));
        assertEquals(15, service.freeLimit(LocalDate.of(2026, 7, 9)));
        assertEquals(15, service.freeLimit(LocalDate.of(2026, 7, 10)));
        assertEquals(20, service.freeLimit(LocalDate.of(2026, 7, 11)));
        assertEquals(20, service.freeLimit(LocalDate.of(2026, 7, 12)));
    }

    @Test
    void shouldShowAdOnEveryPostLimitView() {
        UserAccount account = new UserAccount();
        LocalDate monday = LocalDate.of(2026, 7, 6);

        for (int index = 1; index <= 10; index++) {
            DailyQuotaService.ViewingDecision decision = service.registerView(account, monday);
            assertFalse(decision.adRequired(), "Free views should not trigger ads");
        }

        assertTrue(service.registerView(account, monday).adRequired(), "11th view should trigger ad");
        assertTrue(service.registerView(account, monday).adRequired(), "12th view should trigger ad");
        assertTrue(service.registerView(account, monday).adRequired(), "13th view should trigger ad");
    }
}
