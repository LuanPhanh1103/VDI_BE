package com.mobifone.vdi.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.entity.ApiLog;
import com.mobifone.vdi.repository.ApiLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@Slf4j
public class LogApiAspect {

    private final ApiLogRepository apiLogRepository;
    private final ObjectMapper objectMapper;

    public LogApiAspect(ApiLogRepository apiLogRepository, ObjectMapper objectMapper) {
        this.apiLogRepository = apiLogRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(LogApi)")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
//        Instant startTime = Instant.now();
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");

        ZonedDateTime timeStartZoned = ZonedDateTime.now(zoneId);
        LocalDateTime timeStart = timeStartZoned.toLocalDateTime();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest httpServletRequest = attributes.getRequest();

        String endpoint = httpServletRequest.getRequestURI();
        String httpMethod = httpServletRequest.getMethod();

        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        Map<String, String> headersMap = new HashMap<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headersMap.put(headerName, httpServletRequest.getHeader(headerName));
        }
        String requestHeader = headersMap.toString();
        String requestParams = httpServletRequest.getQueryString();
        Object[] args = joinPoint.getArgs();
        String requestBody = null;
        if (args.length > 0) {
            requestBody = objectMapper.writeValueAsString(args[0]);
        }
        String responseBody = null;
        String responseStatus = null;
        try {
            Object result = joinPoint.proceed();
            log.debug("Response received: {}", result);
            responseBody = objectMapper.writeValueAsString(result);
            responseStatus = String.valueOf(HttpStatus.OK.value());
            return result;
        } catch (Exception e) {
            responseBody = e.getMessage();
            responseStatus = String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value());
            throw e;
        } finally {
            ZonedDateTime timeEndZoned = ZonedDateTime.now(zoneId);
            LocalDateTime timeEnd = timeEndZoned.toLocalDateTime();
            ApiLog apiLog = ApiLog.builder()
                    .startTime(timeStart)
                    .endTime(timeEnd)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .requestHeader(requestHeader)
                    .requestParams(requestParams)
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .responseStatus(responseStatus)
                    .ipAddress(getClientIp(httpServletRequest))
                    .build();
            log.info(String.valueOf(apiLog));
            apiLogRepository.save(apiLog);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}

