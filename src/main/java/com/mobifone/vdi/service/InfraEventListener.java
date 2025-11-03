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

    // thêm helper mới
    private String sanitizeForJson(String s) {
        if (s == null) return null;
        // bỏ ANSI
        String noAnsi = s.replaceAll("\\u001B\\[[;\\d]*m", "");
        // bỏ các escape kiểu \x1b, \x1B, \xNN
        String noHexEsc = noAnsi.replaceAll("\\\\x[0-9A-Fa-f]{2}", "");
        return noHexEsc;
    }

    private JsonNode tryParseJsonStrict(String s) {
        try { return om.readTree(s); } catch (Exception ignored) { return null; }
    }

    @RabbitListener(queues = InfraRabbitConfig.INFRA_QUEUE, containerFactory = "infraListenerFactory")
    public void onInfra(Message msg, Channel channel) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);

        try {
            log.info("[InfraEvent] raw body: {}", body);

            // 0) Luôn sanitize trước
            String cleaned = sanitizeForJson(body);

            // 1) Trường hợp delete-result chuẩn JSON: {"identifier": "...", "result": true}
            JsonNode nodeWhole = tryParseJson(cleaned); // nới lỏng (ALLOW_SINGLE_QUOTES đã bật)
            if (nodeWhole != null && nodeWhole.has("identifier") && nodeWhole.has("result") && !nodeWhole.has("created_resources")) {
                String taskId = nodeWhole.path("identifier").asText(null);
                boolean ok = nodeWhole.path("result").asBoolean(false);
                if (taskId != null) {
                    persistService.handleDeleteResult(taskId, ok, body); // lưu raw để trace
                } else {
                    log.warn("Delete-result message missing identifier");
                }
                channel.basicAck(tag, false);
                return;
            }

            // 2) Thử cắt phần JSON ở trong text (nếu có)
            String jsonCandidate = extractJson(cleaned);
            JsonNode jsonCandidateNode = jsonCandidate != null ? tryParseJsonStrict(jsonCandidate) : null;

            // 2a) SUCCESS chuẩn: có "created_resources"
            if (jsonCandidateNode != null && jsonCandidateNode.has("created_resources")) {
                InfraSuccessEvent event = om.readValue(jsonCandidate, InfraSuccessEvent.class);
                if (event.getIdentifier() != null) {
                    persistService.handleSuccess(event);
                    persistService.findEntity(event.getIdentifier())
                            .ifPresent(t -> signalBus.complete(event.getIdentifier(), t));
                } else {
                    log.warn("Success event missing identifier");
                }
                channel.basicAck(tag, false);
                return;
            }

            // 3) Fallback (ERROR): bất kỳ tình huống còn lại
            String taskId = extractIdentifier(cleaned);             // lấy bằng regex từ text
            String error  = extractErrorMessage(cleaned);           // đã strip ANSI + clamp
            if (taskId != null) {
                persistService.handleError(taskId, error);
                persistService.findEntity(taskId)
                        .ifPresent(t -> signalBus.complete(taskId, t));
            } else {
                // Không lấy được identifier thì vẫn ack để tránh lặp, nhưng log cảnh báo
                log.warn("Error message missing identifier → cannot persist; body={}", cleaned);
            }
            channel.basicAck(tag, false);
            return;
        } catch (Exception ex) {
            // ĐỪNG nack/requeue=false rồi bỏ qua → sẽ mất cơ hội lưu error
            // Ở đây chỉ log và ack để không lặp vô hạn; nếu muốn có DLQ, cấu hình ở broker/policy.
            log.error("Infra message processing failed (final fallback ack). body={}", body, ex);
            channel.basicAck(tag, false);
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
