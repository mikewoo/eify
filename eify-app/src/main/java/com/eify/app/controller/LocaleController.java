package com.eify.app.controller;

import com.eify.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/locale")
public class LocaleController {

    private final LocaleResolver localeResolver;

    public LocaleController(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public Result<Map<String, String>> getCurrentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return Result.success(Map.of("locale", locale.toString()));
    }

    @PutMapping
    public Result<Void> setLocale(@RequestBody Map<String, String> body,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        String localeStr = body.get("locale");
        Locale locale = localeStr != null && localeStr.startsWith("en")
                ? Locale.US
                : Locale.SIMPLIFIED_CHINESE;
        localeResolver.setLocale(request, response, locale);
        return Result.success();
    }
}
