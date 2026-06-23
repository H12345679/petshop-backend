package com.petshop.common;

import lombok.Getter;

/**
 * 业务异常。各模块 throw new BusinessException("xxx") 即可，
 * 由 GlobalExceptionHandler 统一捕获转成 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }
}
