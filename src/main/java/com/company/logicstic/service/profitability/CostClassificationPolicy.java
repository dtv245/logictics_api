package com.company.logicstic.service.profitability;

/** Category describes the cost; behavior describes how this policy uses it. */
public interface CostClassificationPolicy {
    String name();
    String version();
    Classification classify(Input input);

    record Input(String category, String sourceType, String costBasis, String allocationMethod) {}
    record Classification(CostBehavior behavior, String reason) {}
}
