package com.mobifone.vdi.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RdpFileService {
    private static final String RDP_TEMPLATE = """
    full address:s:%s
    username:s:%s
    screen mode id:i:1
    desktopwidth:i:1920
    desktopheight:i:1080
    session bpp:i:32
    authentication level:i:2
    negotiate security layer:i:1
    remoteapplicationmode:i:0
    redirectdrives:i:1
    redirectprinters:i:1
    redirectcomports:i:1
    redirectsmartcards:i:1
    prompt for credentials:i:0
    """;

    public byte[] generateRdpFileContent(String ipAddress, String username) {
        String content = String.format(RDP_TEMPLATE, ipAddress, username);
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
