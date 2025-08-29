package com.mobifone.vdi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Uncategorized error", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "User existed", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1005, "User not existed", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_DOB(1008, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_EXITED(1009, "PERMISSION_NOT_EXITED", HttpStatus.BAD_REQUEST),
    PERMISSION_EXITED(1010, "PERMISSION_EXITED", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXITED(1011, "ROLE_NOT_EXITED", HttpStatus.BAD_REQUEST),
    ROLE_EXITED(1012, "ROLE_EXITED", HttpStatus.BAD_REQUEST),
    VIRTUAL_DESKTOP_EXITED(1013, "VIRTUAL_DESKTOP_EXITED", HttpStatus.BAD_REQUEST),
    VIRTUAL_DESKTOP_NOT_EXITED(1014, "VIRTUAL_DESKTOP_NOT_EXITED", HttpStatus.BAD_REQUEST),
    WRONG_PASSWORD(1015, "WRONG_PASSWORD", HttpStatus.BAD_REQUEST),
    API_IMAGES_ERR(1016, "API_IMAGES_ERR", HttpStatus.BAD_REQUEST),
    API_FLAVOR_ERR(1017, "API_FLAVOR_ERR", HttpStatus.BAD_REQUEST),
    API_INSTANCE_ERR(1018, "API_INSTANCE_ERR", HttpStatus.BAD_REQUEST),
    API_VNC_ERR(1019, "API_VNC_ERR", HttpStatus.BAD_REQUEST),
    ANSIBLE_JOB_NOT_FOUND(1020, "ANSIBLE_JOB_NOT_FOUND", HttpStatus.BAD_REQUEST),
    LOG_FILE_PATH(1021, "Log file path is missing", HttpStatus.BAD_REQUEST),
    LOG_FILE_READ(1022, "Cannot read log file", HttpStatus.BAD_REQUEST),
    PROJECT_NOT_EXISTED(1023, "PROJECT_NOT_EXISTED", HttpStatus.BAD_REQUEST),
    PROJECT_EXISTED(1024, "PROJECT_EXISTED", HttpStatus.BAD_REQUEST),
    PERMISSION_DENIED(1024, "PERMISSION_DENIED", HttpStatus.BAD_REQUEST),
    ANSIBLE_NOT_FOUND(1025, "ANSIBLE_NOT_FOUND", HttpStatus.BAD_REQUEST),

    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
