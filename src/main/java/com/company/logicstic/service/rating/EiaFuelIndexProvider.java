package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.FuelIndexObservation;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class EiaFuelIndexProvider implements FuelIndexProvider {
    // Official ULSD history links, not an inferred regional fallback hierarchy.
    private static final Map<String, String> AREAS = Map.ofEntries(
            Map.entry("US", "NUS"), Map.entry("PADD1", "R10"), Map.entry("PADD1A", "R1X"),
            Map.entry("PADD1B", "R1Y"), Map.entry("PADD1C", "R1Z"), Map.entry("PADD2", "R20"),
            Map.entry("PADD3", "R30"), Map.entry("PADD4", "R40"), Map.entry("PADD5", "R50"),
            Map.entry("CALIFORNIA", "SCA"));
    private final ObjectMapper json;
    private final String apiKey;
    private final URI endpoint;
    private final HttpClient http;

    @Autowired
    public EiaFuelIndexProvider(ObjectMapper json, @Value("${app.rating.eia.api-key:}") String apiKey) {
        this(json, apiKey, URI.create("https://api.eia.gov/v2/petroleum/pri/gnd/data/"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }
    EiaFuelIndexProvider(ObjectMapper json, String apiKey, URI endpoint, HttpClient http) {
        this.json = json; this.apiKey = apiKey; this.endpoint = endpoint; this.http = http;
    }
    public static String seriesFor(String region) {
        String area = AREAS.get(region == null ? "" : region);
        if (area == null) throw new BadRequestException("INVALID_RATE_POLICY", "Unsupported EIA ULSD region");
        return "EMD_EPD2DXL0_PTE_" + area + "_DPG";
    }
    @Override public List<FuelIndexObservation> observations(String region, LocalDate pricingDate) {
        String series = seriesFor(region);
        if (pricingDate == null) throw new BadRequestException("RATING_PRICING_DATE_REQUIRED", "Pickup business date is required");
        if (apiKey == null || apiKey.isBlank()) throw unavailable();
        // Latest two points allow detecting a contradictory duplicate latest period.
        String query = "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&frequency=weekly&data%5B0%5D=value&facets%5Bseries%5D%5B%5D=" + series
                + "&end=" + pricingDate + "&sort%5B0%5D%5Bcolumn%5D=period&sort%5B0%5D%5Bdirection%5D=desc&length=2";
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(endpoint + query))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            var root = json.readTree(response.body());
            var rows = root.path("response").path("data");
            String version = root.path("apiVersion").asText("");
            if (root.has("error") || root.has("warning") || !rows.isArray() || version.isBlank()) throw unavailable();
            Instant retrievedAt = Instant.now();
            List<FuelIndexObservation> result = new ArrayList<>();
            for (var row : rows) {
                if (!series.equals(row.path("series").asText()) || !"$/GAL".equals(row.path("units").asText())) throw unavailable();
                LocalDate date = LocalDate.parse(row.path("period").asText());
                BigDecimal value = new BigDecimal(row.path("value").asText());
                if (value.signum() < 0 || date.isAfter(pricingDate)) throw unavailable();
                // Never retain response.request: EIA echoes the API key in that section.
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(json.writeValueAsBytes(row)));
                result.add(new FuelIndexObservation("EIA", region, series, date, value, "USD", "GALLON",
                        "WEEKLY", "ULSD", true, retrievedAt, version, hash));
            }
            return List.copyOf(result);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (Exception ex) {
            // Do not expose exception/URI/body: any of these can contain credentials.
            throw unavailable();
        }
    }
    private static BadRequestException unavailable() {
        return new BadRequestException("FUEL_INDEX_UNAVAILABLE", "EIA fuel index is not available or its response is invalid");
    }
}
