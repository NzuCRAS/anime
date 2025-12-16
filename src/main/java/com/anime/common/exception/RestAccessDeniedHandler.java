// src/main/java/com/anime/config/RestAccessDeniedHandler.java
package com.anime.common.exception;

import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import java.io.IOException;

public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public RestAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> r = Result.fail(ResultCode.FORBIDDEN, "Forbidden: " + (accessDeniedException != null ? accessDeniedException.getMessage() : "access denied"));
        response.getWriter().write(mapper.writeValueAsString(r));
    }
}