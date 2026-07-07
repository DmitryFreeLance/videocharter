package com.videocharter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.videocharter.model.Country;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CountryCatalogTest {

    @Test
    void shouldMovePopularCountriesToTheTop() {
        CountryCatalog catalog = new CountryCatalog();
        List<Country> countries = catalog.sortedByPopularity(Map.of(
                "JP", 12,
                "BR", 7,
                "US", 3
        ));

        assertEquals("JP", countries.get(0).code());
        assertEquals("BR", countries.get(1).code());
        assertEquals("US", countries.get(2).code());
    }
}
