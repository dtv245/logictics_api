package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;

/** The sole approved V1 FSC formula; all authored inputs are mandatory. */
public record IndexBasedFscPolicy(RatingMileageBasis mileageBasis, String indexProvider,
                                 String indexRegion, Integer maxIndexAgeDays, BigDecimal contractMpg,
                                 BigDecimal baseFuelPrice, String priceCurrency, String priceUnit) { }
