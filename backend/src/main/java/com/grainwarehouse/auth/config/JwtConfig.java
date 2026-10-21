package com.grainwarehouse.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

/**
 * Ký và xác minh access token bằng thư viện có sẵn (Nimbus qua Spring Security) — không tự viết code JWT.
 * HS256: cùng một khóa bí mật để ký và xác minh, vì chỉ một ứng dụng làm cả hai việc (ADR-0003 mục 1).
 */
@Slf4j
@Configuration
public class JwtConfig {

    /** HS256 cần khóa ≥ 256 bit; ngắn hơn thì đoán thử (brute force) khả thi. */
    private static final int MIN_SECRET_BYTES = 32;

    /** Lệch đồng hồ cho phép khi kiểm tra exp — nhỏ vì chỉ một máy chủ vừa ký vừa xác minh. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    /** Mọi chỗ cần "bây giờ" trong auth đều lấy từ đây → test đổi được thời gian mà không phải chờ thật. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecretKey jwtSigningKey(AuthProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            if (!properties.allowEphemeralSecret()) {
                throw new IllegalStateException("Chưa đặt JWT_SECRET (tối thiểu " + MIN_SECRET_BYTES + " byte) — xem .env.example");
            }
            log.warn("JWT_SECRET chưa đặt: dùng khóa ngẫu nhiên tạm thời (chỉ dev). Khởi động lại backend = phải đăng nhập lại");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET quá ngắn: cần tối thiểu " + MIN_SECRET_BYTES + " byte");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Resource Server dùng bean này cho mọi request: kiểm chữ ký (đúng khóa, đúng HS256), exp/nbf, iss.
     * Không truy vấn DB — vì vậy access token đã phát không thu hồi tức thì được, chỉ hết hạn sau 15 phút.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestamps, new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }
}
