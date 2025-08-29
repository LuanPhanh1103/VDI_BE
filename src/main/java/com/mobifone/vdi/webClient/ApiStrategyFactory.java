package com.mobifone.vdi.webClient;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiStrategyFactory {
    private final List<ApiStrategy> strategies;

    public ApiStrategy getStrategy(String serviceType) {
        return strategies.stream()
                .filter(strategy -> strategy.isApplicable(serviceType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No API strategy found for service: " + serviceType));
    }
}
