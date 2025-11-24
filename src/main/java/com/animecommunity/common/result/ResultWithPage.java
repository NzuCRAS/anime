package com.animecommunity.common.result;

import com.animecommunity.common.enums.ResultCode;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ResultWithPage<T> {
    // 和 Result 相同的基础字段
    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timestamp;

    @JsonIgnore
    private ResultCode resultCode;

    // 特化的 data 类型
    @JsonProperty("data")
    private PageData<T> pageData;

    private ResultWithPage(ResultCode resultCode, PageData<T> pageData, Boolean isSuccess) {
        this.timestamp = LocalDateTime.now();
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.message = resultCode.getMsg();
        this.pageData = pageData;
        this.isSuccess = isSuccess;
    }

    public static <T> ResultWithPage<T> success(PageData<T> pageData) {
        return new ResultWithPage<>(ResultCode.SUCCESS, pageData, true);
    }

    public static <T> ResultWithPage<T> fail(ResultCode resultCode) {
        return new ResultWithPage<>(resultCode, null, false);
    }
}
