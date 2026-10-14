package com.grainwarehouse.common.exception;

/** Không tìm thấy bản ghi → HTTP 404, mã RESOURCE_NOT_FOUND. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
