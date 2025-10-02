package com.mobifone.vdi.webClient;

import org.springframework.http.HttpMethod;

public interface ApiStrategy {
    boolean isApplicable(String serviceType);
    // Giữ nguyên cho compatibility (nếu nơi nào chưa cần region)
    default String callApi(HttpMethod method, String endpoint, Object requestBody) {
        return callApi(method, endpoint, requestBody, null);
    }

    // Overload có region (mới)
    String callApi(HttpMethod method, String endpoint, Object requestBody, String region);
}
