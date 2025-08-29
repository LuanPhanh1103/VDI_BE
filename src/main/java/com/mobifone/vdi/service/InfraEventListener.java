package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.configuration.InfraRabbitConfig;
import com.mobifone.vdi.dto.response.InfraSuccessEvent;
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
    ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // ✅

    @RabbitListener(queues = InfraRabbitConfig.INFRA_QUEUE,
            containerFactory = "infraListenerFactory")
    public void onInfra(Message msg, Channel channel) throws Exception {
        long tag = msg.getMessageProperties().getDeliveryTag();
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        try {
            log.info("[InfraEvent] {}", body);

            if (looksLikeJson(body)) {
                InfraSuccessEvent event = om.readValue(body, InfraSuccessEvent.class);
                if (event.getIdentifier() != null) {
                    persistService.handleSuccess(event);
                } else {
                    log.warn("Success event missing identifier");
                }
            } else {
                String taskId = extractIdentifier(body);
                String error = extractErrorMessage(body);
                if (taskId != null) {
                    persistService.handleError(taskId, error);
                } else {
                    log.warn("Error message without identifier");
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            log.error("Infra message processing failed", ex);
            channel.basicNack(tag, false, false);
        }
    }

    private boolean looksLikeJson(String s) {
        String t = s.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    private String extractIdentifier(String s) {
        Pattern p = Pattern.compile("identifier['\\\"]\\s*[:=]\\s*['\\\"]([^'\\\"]+)['\\\"]");
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private String extractErrorMessage(String s) {
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
