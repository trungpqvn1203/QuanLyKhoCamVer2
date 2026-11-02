package com.grainwarehouse.portal.config;

import com.grainwarehouse.auth.config.AuthProperties;
import com.grainwarehouse.auth.config.SecurityConfig;
import com.grainwarehouse.auth.web.JsonSecurityErrorHandler;
import com.grainwarehouse.portal.web.PortalCookieTokenResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chuỗi filter RIÊNG cho app khách (ADR-0005): chỉ áp cho /api/portal/** và đứng TRƯỚC chuỗi nhân viên
 * (chuỗi đó khớp mọi URL). Request /api/portal/** không bao giờ đi qua quy tắc của nhân viên và ngược lại.
 * CSRF dùng chung cơ chế double-submit của SecurityConfig (cookie XSRF-TOKEN không mang quyền gì).
 */
@Configuration
@EnableConfigurationProperties(PortalProperties.class)
public class PortalSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
                                                 AuthProperties authProperties, PortalTokenCodec codec) throws Exception {
        http
                .securityMatcher("/api/portal/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(SecurityConfig.csrfTokenRepository(authProperties))
                        .csrfTokenRequestHandler(SecurityConfig.spaCsrfRequestHandler()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilter(portalJwtFilter(codec, errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.POST, "/api/portal/auth/login", "/api/portal/auth/refresh",
                                "/api/portal/auth/logout").permitAll()
                        // Mọi API còn lại của portal: chỉ token khách (ROLE_CUSTOMER từ claim roles)
                        .anyRequest().hasRole(PortalTokenCodec.CUSTOMER_ROLE));
        return http.build();
    }

    /** Cùng cách lắp với SecurityConfig.cookieJwtFilter (không qua DSL oauth2ResourceServer — lý do: xem lớp đó). */
    private static BearerTokenAuthenticationFilter portalJwtFilter(PortalTokenCodec codec, JsonSecurityErrorHandler errors) {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(PortalTokenCodec.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName(PortalTokenCodec.PHONE_CLAIM);

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(codec.decoder());
        provider.setJwtAuthenticationConverter(converter);
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setBearerTokenResolver(new PortalCookieTokenResolver());
        filter.setAuthenticationEntryPoint(errors);
        return filter;
    }
}
