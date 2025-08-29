package com.mobifone.vdi.webClient;

import org.springframework.http.HttpMethod;

public interface ApiStrategy {
    boolean isApplicable(String serviceType);
    String callApi(HttpMethod method, String endpoint, Object requestBody);
}
