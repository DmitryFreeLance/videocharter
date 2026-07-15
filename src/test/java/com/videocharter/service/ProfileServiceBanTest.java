package com.videocharter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.videocharter.model.UserAccount;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileServiceBanTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAssignThirtyDaysToLegacyBanWithoutDate() {
        StateStore stateStore = new StateStore(tempDir.resolve("state.json"));
        ProfileService service = new ProfileService(stateStore, new CountryCatalog(), new DailyQuotaService(), Set.of());

        stateStore.mutate(state -> {
            UserAccount account = new UserAccount();
            account.setUserId(1001L);
            account.setBanned(true);
            state.getUsers().put(1001L, account);
            return null;
        });

        ProfileService.BanStatus status = service.getBanStatus(1001L, LocalDate.of(2026, 7, 15));
        assertTrue(status.active());
        assertEquals(LocalDate.of(2026, 8, 14), status.until());
    }

    @Test
    void shouldAutoUnbanWhenDateIsReached() {
        StateStore stateStore = new StateStore(tempDir.resolve("state.json"));
        ProfileService service = new ProfileService(stateStore, new CountryCatalog(), new DailyQuotaService(), Set.of());

        stateStore.mutate(state -> {
            UserAccount account = new UserAccount();
            account.setUserId(1002L);
            account.setBanned(true);
            account.setBannedUntil(LocalDate.of(2026, 8, 14));
            state.getUsers().put(1002L, account);
            return null;
        });

        assertTrue(service.getBanStatus(1002L, LocalDate.of(2026, 8, 13)).active());
        assertFalse(service.getBanStatus(1002L, LocalDate.of(2026, 8, 14)).active());
        assertEquals(null, service.getBanStatus(1002L, LocalDate.of(2026, 8, 14)).until());
    }
}
