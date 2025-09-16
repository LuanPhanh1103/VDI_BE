package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.configuration.InfraRabbitConfig;
import com.mobifone.vdi.dto.response.InfraSuccessEvent;
import com.mobifone.vdi.utils.ProvisionSignalBus;
import com.rabbitmq.client.Channel;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InfraEventListener {

    ProvisionPersistService persistService;
    ProvisionSignalBus signalBus;

    ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    @RabbitListener(queues = InfraRabbitConfig.INFRA_QUEUE,
            containerFactory = "infraListenerFactory")
    public void onInfra(Message msg, Channel channel) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        try {
            log.info("[InfraEvent] raw body: {}", body);

            String jsonCandidate = extractJson(body);

            // Thử parse JSON
            JsonNode node = tryParseJson(body);

            // ✅ THÊM MỚI: pattern DELETE RESULT
            // ví dụ: {"identifier": "098765432", "result": true}
            if (node != null && node.has("identifier") && node.has("result") && !node.has("created_resources")) {
                String taskId = node.path("identifier").asText(null);
                boolean ok = node.path("result").asBoolean(false);
                if (taskId != null) {
                    // Lưu kết quả delete (rawMessage = body)
                    persistService.handleDeleteResult(taskId, ok, body);
                } else {
                    log.warn("Delete-result message missing identifier");
                }
                channel.basicAck(tag, false);
                return;
            }

            if (jsonCandidate != null) {
                // SUCCESS path (JSON)
                InfraSuccessEvent event = om.readValue(jsonCandidate, InfraSuccessEvent.class);
                if (event.getIdentifier() != null) {
                    persistService.handleSuccess(event);
                    // ✅ LẤY ENTITY & COMPLETE ĐÚNG CHỮ KÝ 2 THAM SỐ
                    persistService.findEntity(event.getIdentifier())
                            .ifPresent(t -> signalBus.complete(event.getIdentifier(), t));
                } else {
                    log.warn("Success event missing identifier");
                }
            } else {
                // ERROR path (text thường)
                String taskId = extractIdentifier(body);
                String error = extractErrorMessage(body);
                if (taskId != null) {
                    persistService.handleError(taskId, error);
                    // ✅ LẤY ENTITY & COMPLETE
                    persistService.findEntity(taskId)
                            .ifPresent(t -> signalBus.complete(taskId, t));
                } else {
                    log.warn("Error message không có identifier → bỏ qua");
                }
            }

            channel.basicAck(tag, false);
        } catch (Exception ex) {
            log.error("Infra message processing failed", ex);
            // có thể “đánh thức” tương lai với lỗi, nếu bạn muốn Orchestrator dừng ngay:
            // signalBus.completeExceptionally("unknown", ex);
            channel.basicNack(tag, false, false);
        }
    }

    private JsonNode tryParseJson(String s) {
        try { return om.readTree(s); } catch (Exception ignored) { return null; }
    }

    private String extractJson(String s) {
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        if (i >= 0 && j > i) return s.substring(i, j + 1);
        return null;
    }

    private String extractIdentifier(String s) {
        Pattern p = Pattern.compile("identifier['\\\"]\\s*[:=]\\s*['\\\"]([^'\\\"]+)['\\\"]");
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private String extractErrorMessage(String s) {
        // rút gọn + bỏ ANSI để tránh lỗi tràn cột nếu chưa migrate TEXT
        String cut = s.length() > 1500 ? s.substring(0, 1500) + "...(truncated)" : s;
        return cut.replaceAll("\\u001B\\[[;\\d]*m", "");
    }
}
