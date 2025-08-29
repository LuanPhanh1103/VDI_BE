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
import java.util.concurrent.atomic.AtomicReference;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OpenStackApiStrategy implements ApiStrategy {

    WebClient webClient;

    // Lưu token và thời gian hết hạn
    AtomicReference<String> cachedToken = new AtomicReference<>();
    AtomicReference<Instant> tokenExpiryTime = new AtomicReference<>(Instant.EPOCH); // mặc định là hết hạn

    public OpenStackApiStrategy(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(Constants.OPENSTACK.URL).build();
    }

    @Override
    public boolean isApplicable(String serviceType) {
        return Constants.OPENSTACK.NAME_SERVICE.equalsIgnoreCase(serviceType);
    }

    @Override
    public String callApi(HttpMethod method, String endpoint, Object requestBody) {
        try {
            WebClient.RequestBodySpec requestSpec = webClient
                    .method(method)
                    .uri(endpoint)
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        // Thêm token nếu không phải gọi auth
                        if (!endpoint.contains("/v3/auth/tokens")) {
                            headers.set("x-subject-token", getValidToken());
                        }
                    });
            log.info("haha: {}", requestSpec);
            Mono<String> responseMono;

            if (method == HttpMethod.GET || requestBody == null) {
                responseMono = requestSpec.retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                res -> res.bodyToMono(String.class)
                                        .flatMap(err -> {
                                            log.error("API GET error: {}", err);
                                            return Mono.error(new RuntimeException(err));
                                        })
                        )
                        .bodyToMono(String.class);
            } else {
                responseMono = requestSpec
                        .bodyValue(requestBody)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                res -> res.bodyToMono(String.class)
                                        .flatMap(err -> {
                                            log.error("API [{} {}] returned error: {}", method, endpoint, err);
                                            return Mono.error(new RuntimeException("OpenStack API error: " + err));
                                        })
                        )
                        .bodyToMono(String.class);
            }

            String result = responseMono.block(Duration.ofMinutes(5));
            log.info("API [{} {}] success", method, endpoint);
            return result;

        } catch (Exception e) {
            log.error("Call failed [{} {}]: {}", method, endpoint, e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Trả về token hợp lệ hiện tại, nếu hết hạn thì gọi lại
     */
    private String getValidToken() {
        Instant now = Instant.now();

        // Nếu token chưa tồn tại hoặc hết hạn
        if (cachedToken.get() == null || now.isAfter(tokenExpiryTime.get())) {
            log.info("Token expired or not found. Requesting new token...");
            String newToken = fetchToken();
            cachedToken.set(newToken);
            tokenExpiryTime.set(now.plus(Duration.ofHours(2))); // hiệu lực 2 giờ
        }

        return cachedToken.get();
    }

    /**
     * Gọi OpenStack API để lấy token
     */
    private String fetchToken() {
        String payload = """
        {
          "auth": {
            "identity": {
              "methods": ["password"],
              "password": {
                "user": {
                  "name": "user01",
                  "password": "hoanganh",
                  "domain": { "name": "Default" }
                }
              }
            },
            "scope": {
              "project": {
                "name": "normal-project-01",
                "domain": { "name": "Default" }
              }
            }
          }
        }
        """;

        return webClient
                .post()
                .uri(Constants.OPENSTACK.ENDPOINT.AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.justOrEmpty(response.headers().header("x-subject-token").stream().findFirst());
                    } else {
                        return response.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("Auth failed: {}", err);
                                    return Mono.error(new RuntimeException("Auth failed: " + err));
                                });
                    }
                })
                .block(Duration.ofSeconds(10));
    }
}
