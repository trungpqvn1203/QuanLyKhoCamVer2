package com.grainwarehouse.auth.service;

import com.grainwarehouse.auth.config.AuthProperties;
import com.grainwarehouse.auth.config.SecurityConfig;
import com.grainwarehouse.auth.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Tạo access token (JWT) và refresh token (chuỗi ngẫu nhiên) — ADR-0003 mục 1. */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    /**
     * Ví dụ payload: {"iss":"grain-warehouse","sub":"3","username":"lan.bh","roles":["SALES_STAFF"],
     * "iat":..., "exp": iat + 15 phút, "jti":"..."}. Payload chỉ được KÝ, không mã hóa — ai có token đều đọc được,
     * nên không đặt dữ liệu nhạy cảm vào đây.
     */
    public String issueAccessToken(AppUser user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(SecurityConfig.USERNAME_CLAIM, user.getUsername())
                .claim(SecurityConfig.ROLES_CLAIM, user.getRoles().stream().sorted().map(Enum::name).toList())
                .build();
        // Phải ghi rõ HS256: không ghi thì Nimbus mặc định tìm khóa RSA (RS256) và báo lỗi
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 256 bit ngẫu nhiên từ SecureRandom (không phải Random — Random đoán được chuỗi kế tiếp). */
    public String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * DB chỉ lưu SHA-256 của refresh token. Không cần BCrypt như mật khẩu: token ngẫu nhiên 256 bit
     * không thể đoán, còn BCrypt chậm cố ý để chống đoán mật khẩu do người đặt.
     */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }
}
