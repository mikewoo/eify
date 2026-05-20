package com.eify.common.util;

import com.eify.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageUtil {

    private final MessageSource messageSource;

    public String get(ErrorCode errorCode) {
        return get(errorCode, (Object[]) null);
    }

    public String get(ErrorCode errorCode, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        String key = errorCode.name() + "_zh";
        return messageSource.getMessage(key, args, errorCode.getMessage(), locale);
    }

    public String get(String key) {
        return get(key, (Object[]) null);
    }

    public String get(String key, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }
}
