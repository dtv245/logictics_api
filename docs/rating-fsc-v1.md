# Rating V1 FSC implementation

Status: 6D implementation; verification is recorded in the progress checkpoint.

Uses confirmed RATE-DEC-002…005. No production price, MPG, region or age defaults.

## Provider identity

[Official EIA ULSD table and history links](https://www.eia.gov/dnav/pet/pet_pri_gnd_a_epd2dxl0_pte_dpgal_w.htm)
were verified on 2026-10-04. Product is tax-inclusive weekly retail on-highway
ultra-low-sulfur diesel, USD/gallon. This implementation requests the explicit
ULSD series rather than relying on historical all-types equivalence.

| Policy region | ULSD series |
|---|---|
| US | EMD_EPD2DXL0_PTE_NUS_DPG |
| PADD1 | EMD_EPD2DXL0_PTE_R10_DPG |
| PADD1A | EMD_EPD2DXL0_PTE_R1X_DPG |
| PADD1B | EMD_EPD2DXL0_PTE_R1Y_DPG |
| PADD1C | EMD_EPD2DXL0_PTE_R1Z_DPG |
| PADD2 | EMD_EPD2DXL0_PTE_R20_DPG |
| PADD3 | EMD_EPD2DXL0_PTE_R30_DPG |
| PADD4 | EMD_EPD2DXL0_PTE_R40_DPG |
| PADD5 | EMD_EPD2DXL0_PTE_R50_DPG |
| CALIFORNIA | EMD_EPD2DXL0_PTE_SCA_DPG |

[EIA API documentation](https://www.eia.gov/opendata/documentation.php) defines
the v2 petroleum/pri/gnd data route, weekly frequency, series facet, end-date,
explicit period descending sort, and string decimal values. Runtime requests
the latest two points bounded by pricingDate; selection rejects a duplicate
latest observation instead of guessing which revision wins. Provider-period
LocalDate is retained verbatim. Later fetches may receive revised prices.

Configure `APP_RATING_EIA_API_KEY` / `app.rating.eia.api-key` through existing
secret configuration. An absent key, invalid response, wrong series/unit,
future observation or transport failure yields FUEL_INDEX_UNAVAILABLE. No
request/body/URI exception is propagated: EIA echoes its API key in request
metadata. Only selected data-row SHA-256 and provider API version, retrieval
instant, series and financial inputs are retained for subsequent acceptance.
Connect/read timeouts are transport controls, not financial policy defaults.
No external ingestion, tenant observation write or financial snapshot occurs
on preview/retrieval; no transaction is held during HTTP retrieval.

## Calculation

RatingPolicyV1 uses DECIMAL128 intermediates. For INDEX_BASED_MPG only:
`max(0, observation.value - baseFuelPrice) / contractMpg`, then scale 6 HALF_UP.
That rounded authoritative unit rate is multiplied by explicit component
eligible miles, then currency minor-unit scale HALF_UP. JDK currency metadata
provides scale; unknown/pseudo currencies without minor units reject. No global
scale 2. Linehaul monetary min/max are applied before final money rounding.

Output retains versioned FSC policy, observation provenance, mileage provenance,
raw unit calculation, rounded unit, unrounded total, rounded money and rounding
policy/version. It is an immutable calculation value, **not** an accepted
persisted financial snapshot. The latter belongs to 6F and freezes provider
revisions together with exact Load business pricingDate/source.

## Verification scope

Real local HTTP fixture tests exercise the production parser/query shape and
error sanitization without contacting EIA. Calculator tests cover nonfuture
selection, age boundaries, region identity, duplicates, missing/invalid inputs,
explicit zero, per-mile rounding before total, non-two-digit currencies,
min/max and independent revision results. Full existing PostgreSQL regression
is required. No schema change is needed for these read-only/pure components;
V1–V23 remain untouched. Live EIA credential/network deployment is a separate
environment qualification, not claimed by fixture tests.
