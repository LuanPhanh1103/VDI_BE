package com.mobifone.vdi.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VNCService {
    public byte[] generateVncFileContent(String ip, int port) {
        String file = "[Connection]" + System.lineSeparator() +
                "Host=" + ip + System.lineSeparator() +
                "Port=" + port + System.lineSeparator();

        return file.getBytes();
    }
}
