package com.grainwarehouse.auth.config;

import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.web.CookieBearerTokenResolver;
import com.grainwarehouse.auth.web.JsonSecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.Locale;

/**
 * Cấu hình Spring Security (ADR-0003). Mỗi request đi qua chuỗi filter theo thứ tự:
 * CsrfFilter → BearerTokenAuthenticationFilter (đọc JWT từ cookie, xác minh bằng JwtDecoder) → AuthorizationFilter (so vai trò với quy tắc URL)
 * → controller. Không qua được bước nào thì dừng ở đó với 401/403, service không bao giờ chạy.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    static final String WAREHOUSE = "WAREHOUSE_MANAGER";
    static final String SALES = "SALES_STAFF";
    static final String ADMIN = "SYSTEM_ADMIN";

    /** Claim chứa danh sách vai trò trong access token (TokenService ghi, converter dưới đây đọc). */
    public static final String ROLES_CLAIM = "roles";
    public static final String USERNAME_CLAIM = "username";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errors,
                                                   AuthProperties properties, JwtDecoder jwtDecoder) throws Exception {
        http
                // Không giữ HttpSession: mọi request tự mang JWT trong cookie
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository(properties))
                        .csrfTokenRequestHandler(spaCsrfRequestHandler()))
                // Đăng nhập/đăng xuất qua AuthController (JSON), không dùng trang form/hộp Basic mặc định
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilter(cookieJwtFilter(jwtDecoder, errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(SecurityConfig::accessRules);
        // Không gọi .cors(): React và API cùng tên miền (Nginx / Vite proxy) nên không cần mở cho nguồn khác
        return http.build();
    }

    /**
     * Filter đọc access token từ cookie và xác minh bằng JwtDecoder — đúng các lớp mà oauth2ResourceServer() dùng,
     * nhưng tự lắp thay vì gọi DSL đó. Lý do: DSL tự BỎ kiểm CSRF cho mọi request có bearer token (giả định token
     * nằm ở header Authorization, thứ trình duyệt không tự gửi). Token của ta nằm trong cookie — trình duyệt TỰ gửi
     * kèm cả request do trang độc hại tạo ra — nên CSRF phải được giữ. AuthApiTest.shouldRequireCsrfOnWrites kiểm điều này.
     * Không khai báo filter này là @Bean: Spring Boot sẽ đăng ký nó thêm lần nữa làm servlet filter.
     */
    private static BearerTokenAuthenticationFilter cookieJwtFilter(JwtDecoder jwtDecoder, JsonSecurityErrorHandler errors) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setBearerTokenResolver(new CookieBearerTokenResolver());
        // Token hỏng / hết hạn → 401 với envelope chuẩn
        filter.setAuthenticationEntryPoint(errors);
        return filter;
    }

    /**
     * Ma trận phân quyền — MỘT chỗ duy nhất (ADR-0003 mục 5). Quy tắc khớp từ trên xuống, dừng ở quy tắc đầu tiên
     * khớp → quy tắc cụ thể phải đứng trước quy tắc chung. URL không có trong danh sách → từ chối (denyAll):
     * thêm endpoint mới mà quên khai báo quyền thì bị chặn, không vô tình mở cho mọi người.
     */
    private static void accessRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules) {
        rules
                // Spring Boot chuyển lỗi chưa bắt sang /error — để trang lỗi đó trả được 4xx/5xx thật, không thành 403
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/error", "/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/me/**").authenticated()

                .requestMatchers("/api/users", "/api/users/**").hasRole(ADMIN)

                // NCC của một SP thuộc quyền nhà cung cấp — đứng trước quy tắc chung của /api/products/**
                .requestMatchers(HttpMethod.GET, "/api/products/*/suppliers").hasAnyRole(WAREHOUSE, ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/products/**", "/api/warehouses/**")
                .authenticated()
                .requestMatchers("/api/categories/**", "/api/products/**", "/api/warehouses/**").hasRole(ADMIN)

                // Gồm cả trả tiền NCC (/api/inbound-receipts/{id}/payments) và công nợ NCC (BR-SPAY-06)
                .requestMatchers("/api/suppliers/**", "/api/inbound-receipts/**", "/api/supplier-debts/**")
                .hasAnyRole(WAREHOUSE, ADMIN)

                // Khách hàng (gồm bảng giá riêng) và phiếu bán: ai cũng xem được, bán hàng + quản trị mới ghi
                .requestMatchers(HttpMethod.GET, "/api/customers/**", "/api/sales-orders/**").authenticated()
                .requestMatchers("/api/customers/**", "/api/sales-orders/**").hasAnyRole(SALES, ADMIN)
                .requestMatchers("/api/customer-debts/**").hasAnyRole(SALES, ADMIN)

                // Tồn kho chỉ có API đọc; phương thức khác → 405 từ Spring MVC
                .requestMatchers("/api/inventory/**").authenticated()

                .requestMatchers("/api/attendance/**").hasAnyRole(WAREHOUSE, ADMIN)
                // Gồm tạm ứng (/api/employees/{id}/advances), trả lương (/api/payrolls/{tháng}/items/...) và công nợ nhân viên
                // (BR-EPAY-06): chỉ quản lý hệ thống
                .requestMatchers("/api/employees/**", "/api/payrolls/**", "/api/employee-debts/**").hasRole(ADMIN)
                // Sổ quỹ lộ cả doanh thu lẫn lương (BR-CASH-07): chỉ quản lý hệ thống
                .requestMatchers("/api/cash-book/**", "/api/cash-vouchers/**", "/api/cash-categories/**").hasRole(ADMIN)
                // Số dư đầu kỳ làm tồn tăng mà không qua nhập hàng (BR-OPN-09): chỉ quản lý hệ thống
                .requestMatchers("/api/opening-stocks", "/api/opening-stocks/**").hasRole(ADMIN)

                .anyRequest().denyAll();
    }

    /**
     * Double-submit cookie: cookie XSRF-TOKEN KHÔNG HttpOnly để JavaScript đọc rồi gửi lại trong header X-XSRF-TOKEN.
     * Trang web khác không đọc được cookie của tên miền này → không tạo được header → CsrfFilter trả 403.
     */
    private static CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(properties.cookieSecure()));
        return repository;
    }

    /**
     * Mẫu cho SPA trong tài liệu Spring Security 6.3:
     * - Handler thường (không XOR): header gửi đúng giá trị trong cookie. Che giấu kiểu XOR chỉ cần khi token
     *   được in vào HTML (tấn công BREACH); ở đây token chỉ đi qua cookie + header.
     * - setCsrfRequestAttributeName(null): nạp token ở MỌI request thay vì trì hoãn → cookie XSRF-TOKEN luôn được
     *   đặt ngay từ request GET đầu tiên, frontend có sẵn để gửi kèm request ghi.
     */
    private static CsrfTokenRequestAttributeHandler spaCsrfRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    /** Claim "roles": ["SALES_STAFF"] → authority ROLE_SALES_STAFF; authentication.getName() = username. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName(USERNAME_CLAIM);
        return converter;
    }

    /**
     * "{bcrypt}$2a$10$...": tiền tố ghi thuật toán đã dùng. Sau này đổi thuật toán (argon2...) thì mật khẩu cũ
     * vẫn kiểm tra được, không phải bắt mọi người đặt lại.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Spring Security hỏi bean này "người tên X là ai, mật khẩu băm là gì, có bị khóa không". */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository userRepository) {
        return username -> userRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .disabled(!user.isActive())
                        .roles(user.getRoles().stream().map(Enum::name).toArray(String[]::new))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    /**
     * Dùng ở POST /api/auth/login. DaoAuthenticationProvider: tải người dùng → so mật khẩu bằng PasswordEncoder.
     * Đổi thứ tự kiểm tra: mặc định kiểm "bị khóa" TRƯỚC mật khẩu → đoán bừa mật khẩu vẫn biết được tài khoản
     * tồn tại và đang bị khóa. Ở đây chỉ báo "bị khóa" khi mật khẩu đã đúng.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> {
        });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }
}
