package com.aitable.common;

/**
 * 业务异常。
 *
 * <p>Service 层遇到"能预期到的错误"（例如房间不存在、座位已满）时抛出这个异常，
 * 由 {@link GlobalExceptionHandler} 统一转换成 {@link Result} 返回给前端，
 * 这样 Controller 里就不用到处写 try/catch。</p>
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAIL.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
