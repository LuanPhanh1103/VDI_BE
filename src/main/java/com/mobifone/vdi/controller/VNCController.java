package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.request.VNCRequest;
import com.mobifone.vdi.service.VNCService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vnc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VNCController {
    VNCService vncService;

    @LogApi
    @PostMapping("/vncfile")
    public ResponseEntity<byte[]> downloadVncFile(@RequestBody VNCRequest request) {
        String filename = request.getName();
        if (filename == null || filename.trim().isEmpty()) {
            filename = "connect.vnc";
        } else if (!filename.endsWith(".vnc")) {
            filename += ".vnc";
        }

        byte[] fileContent = vncService.generateVncFileContent(request.getIp(), Integer.parseInt(request.getPort()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }
}
