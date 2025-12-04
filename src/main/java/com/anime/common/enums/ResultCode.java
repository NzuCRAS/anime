package com.anime.common.enums;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统错误"),
    BAD_REQUEST(4001, "错误的请求");

    private final int code;
    private final String msg;

    ResultCode(int errorCode, String errorMessage) {
        this.code = errorCode;
        this.msg = errorMessage;
    }

    public static int getCode(ResultCode resultCode) {
        return resultCode.code;
    }

    public static String getMsg(ResultCode resultCode) {
        return resultCode.msg;
    }
}
