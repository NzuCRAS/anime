// src/main/java/com/anime/config/RestAuthenticationEntryPoint.java
package com.anime.common.exception;

import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> r = Result.fail(ResultCode.UNAUTHORIZED, "Unauthorized: " + (authException != null ? authException.getMessage() : "authentication required"));
        response.getWriter().write(mapper.writeValueAsString(r));
    }
}