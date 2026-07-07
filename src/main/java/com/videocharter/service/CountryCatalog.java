package com.videocharter.service;

import com.videocharter.model.Country;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CountryCatalog {

    private static final List<String> COUNTRY_CODES = List.of(
            "US", "GB", "DE", "FR", "IT", "ES", "PT", "NL", "BE", "CH", "AT", "SE", "NO", "DK", "FI",
            "PL", "CZ", "SK", "HU", "RO", "BG", "GR", "TR", "UA", "KZ", "RU", "AE", "SA", "IL", "EG",
            "IN", "PK", "BD", "TH", "VN", "MY", "SG", "ID", "PH", "JP", "KR", "CN", "HK", "TW", "AU",
            "NZ", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "ZA", "NG", "KE", "MA", "TN"
    );

    private static final List<String> COUNTRY_NAMES = List.of(
            "United States", "United Kingdom", "Germany", "France", "Italy", "Spain", "Portugal", "Netherlands",
            "Belgium", "Switzerland", "Austria", "Sweden", "Norway", "Denmark", "Finland", "Poland",
            "Czech Republic", "Slovakia", "Hungary", "Romania", "Bulgaria", "Greece", "Turkey", "Ukraine",
            "Kazakhstan", "Russia", "United Arab Emirates", "Saudi Arabia", "Israel", "Egypt", "India",
            "Pakistan", "Bangladesh", "Thailand", "Vietnam", "Malaysia", "Singapore", "Indonesia", "Philippines",
            "Japan", "South Korea", "China", "Hong Kong", "Taiwan", "Australia", "New Zealand", "Canada",
            "Mexico", "Brazil", "Argentina", "Chile", "Colombia", "Peru", "South Africa", "Nigeria",
            "Kenya", "Morocco", "Tunisia"
    );

    private final List<Country> countries;

    public CountryCatalog() {
        this.countries = buildCountries();
    }

    public List<Country> sortedByPopularity(Map<String, Integer> popularity) {
        return countries.stream()
                .sorted(Comparator
                        .comparingInt((Country country) -> popularity.getOrDefault(country.code(), 0))
                        .reversed()
                        .thenComparing(Country::name))
                .toList();
    }

    public Optional<Country> findByCode(String code) {
        return countries.stream().filter(country -> country.code().equalsIgnoreCase(code)).findFirst();
    }

    private List<Country> buildCountries() {
        return java.util.stream.IntStream.range(0, COUNTRY_CODES.size())
                .mapToObj(index -> new Country(COUNTRY_CODES.get(index), COUNTRY_NAMES.get(index), flagEmoji(COUNTRY_CODES.get(index))))
                .toList();
    }

    private String flagEmoji(String countryCode) {
        StringBuilder builder = new StringBuilder();
        for (char symbol : countryCode.toUpperCase().toCharArray()) {
            builder.appendCodePoint(127397 + symbol);
        }
        return builder.toString();
    }
}
