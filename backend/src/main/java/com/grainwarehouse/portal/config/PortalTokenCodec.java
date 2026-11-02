package com.grainwarehouse.portal.config;

import com.grainwarehouse.portal.entity.CustomerAccount;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ký và xác minh access token của khách (ADR-0005 mục 1) — hai lớp tách khỏi token nhân viên:
 * <ol>
 *   <li><b>Khóa khác</b>: HMAC-SHA256(khóa nhân viên, "portal-v1"). Biết token khách không suy ra được khóa nhân viên
 *       và ngược lại; decoder nhân viên không xác minh nổi chữ ký token khách.</li>
 *   <li><b>`iss` khác</b>: kể cả sau này ai đó lỡ dùng chung khóa, validator issuer vẫn chặn.</li>
 * </ol>
 * Cố ý KHÔNG khai báo encoder/decoder là @Bean kiểu JwtEncoder/JwtDecoder: Spring tiêm theo kiểu, có hai bean cùng
 * kiểu thì TokenService và SecurityConfig của nhân viên không biết lấy cái nào (hoặc tệ hơn, lấy nhầm).
 */
@Component
public class PortalTokenCodec {

    public static final String CUSTOMER_ID_CLAIM = "customerId";
    public static final String PHONE_CLAIM = "phone";
    public static final String ROLES_CLAIM = "roles";
    public static final String CUSTOMER_ROLE = "CUSTOMER";

    private static final byte[] KEY_LABEL = "portal-v1".getBytes(StandardCharsets.UTF_8);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final PortalProperties properties;

    public PortalTokenCodec(SecretKey jwtSigningKey, PortalProperties properties, Clock clock) {
        this.properties = properties;
        SecretKey portalKey = derive(jwtSigningKey);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(portalKey));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(portalKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                timestamps, new JwtIssuerValidator(properties.issuer())));
        this.decoder = nimbus;
    }

    /** sub = id tài khoản app; customerId = khách — mọi API portal lấy khách TỪ ĐÂY, không từ URL / body. */
    public String issueAccessToken(CustomerAccount account, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(account.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(CUSTOMER_ID_CLAIM, account.getCustomer().getId())
                .claim(PHONE_CLAIM, account.getCustomer().getPhone())
                .claim(ROLES_CLAIM, List.of(CUSTOMER_ROLE))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    // Dẫn xuất khóa bằng HMAC (cùng ý với HKDF-Expand một bước): không thêm biến môi trường, vẫn là khóa khác
    private static SecretKey derive(SecretKey staffKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(staffKey);
            return new SecretKeySpec(mac.doFinal(KEY_LABEL), "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không dẫn xuất được khóa ký token khách", e);
        }
    }
}
