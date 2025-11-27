package com.anime.common.result;

import com.anime.common.enums.ResultCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class Result<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("code")
    private Integer code;      // 从 ResultCode 中提取数字码

    @JsonProperty("message")
    private String message;    // 从 ResultCode 中提取消息

    @JsonProperty("data")
    private T data;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timestamp;

    //保留原始的 ResultCode 用于内部逻辑，但不序列化
    @JsonIgnore
    private ResultCode resultCode;

    private Result(ResultCode resultCode, T data, Boolean isSuccess) {
        this.timestamp = LocalDateTime.now();
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.message = resultCode.getMsg();
        this.data = data;
        this.isSuccess = isSuccess;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS, data, true);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode, null, false);
    }
}
