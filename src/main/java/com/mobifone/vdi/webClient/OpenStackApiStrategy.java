package com.mobifone.vdi.webClient;

import com.mobifone.vdi.common.Constants;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OpenStackApiStrategy implements ApiStrategy {

    WebClient webClient;

    // Cache token THEO REGION
    static final class TokenCache {
        volatile String token;
        volatile Instant expiry = Instant.EPOCH;
    }
    // key = region (ví dụ "yha_yoga", "" nếu không có region)
    ConcurrentHashMap<String, TokenCache> tokenByRegion = new ConcurrentHashMap<>();

    public OpenStackApiStrategy(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(Constants.OPENSTACK.URL).build();
    }

    @Override
    public boolean isApplicable(String serviceType) {
        return Constants.OPENSTACK.NAME_SERVICE.equalsIgnoreCase(serviceType);
    }

    // Helper ghép region + endpoint relative
    private String withRegion(String region, String endpoint) {
        String p = Optional.ofNullable(region).orElse("").trim();     // "yha_yoga" hoặc ""
        String e = (endpoint == null ? "" : endpoint);
        if (!e.startsWith("/")) e = "/" + e;
        if (p.isEmpty()) return e;
        if (!p.startsWith("/")) p = "/" + p;
        return p + e;
    }

    @Override
    public String callApi(HttpMethod method, String endpoint, Object requestBody, String region) {
        try {
            String finalPath = withRegion(region, endpoint); // GHÉP 1 LẦN DUY NHẤT

            WebClient.RequestBodySpec requestSpec = webClient
                    .method(method)
                    .uri(finalPath)
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        // endpoint NHẬN VÀO là relative (chưa có region) => so sánh trực tiếp
                        if (!endpoint.equals(Constants.OPENSTACK.ENDPOINT.AUTHENTICATION)) {
                            h.set("x-subject-token", getValidToken(region));
                        }
                    });

            Mono<String> mono = (method == HttpMethod.GET || requestBody == null)
                    ? requestSpec.retrieve()
                    .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                            .flatMap(err -> {
                                log.error("API GET error: {}", err);
                                return Mono.error(new RuntimeException(err));
                            }))
                    .bodyToMono(String.class)
                    : requestSpec.bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                            .flatMap(err -> {
                                log.error("API [{} {}] returned error: {}", method, finalPath, err);
                                return Mono.error(new RuntimeException("OpenStack API error: " + err));
                            }))
                    .bodyToMono(String.class);

            String result = mono.block(Duration.ofMinutes(5));
            log.info("API [{} {}] success", method, finalPath);
            return result;

        } catch (Exception e) {
            log.error("Call failed [{} {}]: {}", method, endpoint, e.getMessage());
            return e.getMessage();
        }
    }


    /** Lấy token hợp lệ theo region (mỗi region 1 token riêng) */
    private String getValidToken(String region) {
        String key = Optional.ofNullable(region).orElse("").trim(); // "" nếu không có region
        TokenCache cache = tokenByRegion.computeIfAbsent(key, k -> new TokenCache());

        Instant now = Instant.now();
        if (cache.token == null || now.isAfter(cache.expiry)) {
            log.info("Token for region='{}' expired or not found. Requesting new token...", key);
            String newToken = fetchToken(key);
            cache.token = newToken;
            cache.expiry = now.plus(Duration.ofHours(2)); // ví dụ 2h
        }
        return cache.token;
    }

    /** Gọi OpenStack AUTH theo region: POST {/{region}}/v3/auth/tokens */
    private String fetchToken(String region) {
        String payload = """
        {
          "auth": {
            "identity": {
              "methods": ["password"],
              "password": {
                "user": {
                  "name": "bucloud",
                  "password": "bucloud@123",
                  "domain": { "name": "Default" }
                }
              }
            },
            "scope": {
              "project": {
                "name": "Private.BUCLOUD.Test",
                "domain": { "name": "Default" }
              }
            }
          }
        }
        """;

        String authPath = withRegion(region, Constants.OPENSTACK.ENDPOINT.AUTHENTICATION); // /{region}/v3/auth/tokens

        return webClient.post()
                .uri(authPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        return Mono.justOrEmpty(resp.headers().header("x-subject-token").stream().findFirst());
                    } else {
                        return resp.bodyToMono(String.class).flatMap(err -> {
                            log.error("Auth failed (region='{}'): {}", region, err);
                            return Mono.error(new RuntimeException("Auth failed: " + err));
                        });
                    }
                })
                .block(Duration.ofSeconds(10));
    }
}