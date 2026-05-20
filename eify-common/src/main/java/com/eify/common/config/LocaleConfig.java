package com.eify.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Configuration
public class LocaleConfig {

    private static final Set<String> SUPPORTED = Set.of("zh", "en");

    @Bean
    public LocaleResolver localeResolver() {
        return new HybridLocaleResolver();
    }

    static class HybridLocaleResolver extends AcceptHeaderLocaleResolver {
        private final CookieLocaleResolver cookieResolver;

        HybridLocaleResolver() {
            cookieResolver = new CookieLocaleResolver("eify_lang");
            cookieResolver.setCookieMaxAge(Duration.ofDays(365));
            cookieResolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
            setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            Locale cookieLocale = cookieResolver.resolveLocale(request);
            if (cookieLocale != null && SUPPORTED.contains(cookieLocale.getLanguage())) {
                return cookieLocale;
            }
            Locale headerLocale = super.resolveLocale(request);
            if (headerLocale != null && SUPPORTED.contains(headerLocale.getLanguage())) {
                return headerLocale;
            }
            return getDefaultLocale();
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            cookieResolver.setLocale(request, response, locale);
        }
    }
}
