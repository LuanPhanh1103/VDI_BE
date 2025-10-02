package com.mobifone.vdi.service;

import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PortAllocatorService {
    VirtualDesktopService virtualDesktopService; // thay vì repo
    SecureRandom rnd = new SecureRandom();

    public int allocateUnique() {
        for (int i = 0; i < 50; i++) {
            int p = 10000 + rnd.nextInt(40001); // [10000,50000]
            if (!virtualDesktopService.isAnyPublicPortUsed(p)) return p;
        }
        for (int p = 10000; p <= 50000; p++) {
            if (!virtualDesktopService.isAnyPublicPortUsed(p)) return p;
        }
        throw new AppException(ErrorCode.NO_AVAILABLE_PORT);
    }
}


