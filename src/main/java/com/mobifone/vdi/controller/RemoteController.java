package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.service.RdpFileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/generateRdp")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RemoteController {
    RdpFileService rdpFileService;

    @LogApi
    @GetMapping(
            value = "/downloadRdp",
            produces = "application/x-rdp; charset=UTF-8"
    )
    public ResponseEntity<byte[]> downloadRdpFile(
            @RequestParam String ipAddress,
            @RequestParam String username,
            @RequestParam int port,
            @RequestParam(required = false, defaultValue = "connection.rdp") String fileName) {

        // Bảo đảm có đuôi .rdp
        String safeName = fileName.endsWith(".rdp") ? fileName : fileName + ".rdp";

        byte[] data = rdpFileService.generateRdpFileContent(ipAddress, username, port);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentLength(data.length)
                .contentType(MediaType.parseMediaType("application/x-rdp; charset=UTF-8"))
                .body(data);
    }


}
