package com.grainwarehouse.portal.service;

import com.grainwarehouse.auth.config.AuthProperties;
import com.grainwarehouse.auth.dto.ChangePasswordRequest;
import com.grainwarehouse.auth.entity.RevokeReason;
import com.grainwarehouse.auth.service.AuthService.SessionTokens;
import com.grainwarehouse.auth.service.PasswordPolicy;
import com.grainwarehouse.auth.service.TokenService;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.text.PhoneNumbers;
import com.grainwarehouse.portal.config.PortalProperties;
import com.grainwarehouse.portal.config.PortalTokenCodec;
import com.grainwarehouse.portal.dto.PortalLoginRequest;
import com.grainwarehouse.portal.dto.PortalMeResponse;
import com.grainwarehouse.portal.entity.CustomerAccount;
import com.grainwarehouse.portal.entity.CustomerRefreshToken;
import com.grainwarehouse.portal.repository.CustomerAccountRepository;
import com.grainwarehouse.portal.repository.CustomerRefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Đăng nhập / gia hạn / đăng xuất / đổi mật khẩu của app khách (ADR-0005, BR-POR-01..05) — cùng cơ chế
 * rotation + phát hiện dùng lại như AuthService của nhân viên, thêm khóa tạm khi sai mật khẩu (BR-POR-04).
 */
@Slf4j
@Service
public class PortalAuthService {

    private final CustomerAccountRepository accountRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final PortalTokenCodec tokenCodec;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final PortalProperties properties;
    private final AuthProperties authProperties;
    private final Clock clock;
    // So mật khẩu với một hash giả khi SĐT không có tài khoản: thời gian phản hồi như SĐT có thật → không dò
    // được SĐT nào đã có tài khoản bằng cách đo thời gian (BCrypt chậm cố ý, bỏ qua bước này là lộ ngay)
    private final String dummyHash;

    public PortalAuthService(CustomerAccountRepository accountRepository,
                             CustomerRefreshTokenRepository refreshTokenRepository, PortalTokenCodec tokenCodec,
                             TokenService tokenService, PasswordEncoder passwordEncoder, PortalProperties properties,
                             AuthProperties authProperties, Clock clock) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenCodec = tokenCodec;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.authProperties = authProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public record LoginResult(PortalMeResponse me, SessionTokens tokens) {
    }

    public sealed interface RefreshResult {
        record Renewed(PortalMeResponse me, SessionTokens tokens) implements RefreshResult {
        }

        record Race() implements RefreshResult {
        }

        record Rejected() implements RefreshResult {
        }
    }

    /**
     * noRollbackFor: sai mật khẩu ném BadCredentialsException nhưng số lần sai PHẢI được commit (BR-POR-04) —
     * rollback thì không bao giờ khóa tạm được. Khóa dòng tài khoản trước khi đếm (xem repository).
     * "Bị khóa" chỉ báo khi mật khẩu đúng (như BR-AUTH-04); riêng khóa tạm do sai mật khẩu thì báo ngay, để khách
     * biết chờ thay vì thử tiếp.
     */
    @Transactional(noRollbackFor = AuthenticationException.class)
    public LoginResult login(PortalLoginRequest request, String userAgent) {
        Instant now = clock.instant();
        Optional<CustomerAccount> found = accountRepository.findForUpdateByPhone(PhoneNumbers.normalize(request.phone().trim()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BadCredentialsException("Sai số điện thoại hoặc mật khẩu");
        }
        CustomerAccount account = found.get();
        if (account.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, account.getLockedUntil()).toMinutes() + 1);
            throw new LockedException("Nhập sai mật khẩu nhiều lần. Thử lại sau khoảng " + minutes + " phút");
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            account.recordFailedLogin(now, properties.maxFailedLogins(), properties.lockDuration());
            throw new BadCredentialsException("Sai số điện thoại hoặc mật khẩu");
        }
        if (!account.isActive() || !account.getCustomer().isActive()) {
            throw new LockedException("Tài khoản app đã bị khóa. Vui lòng liên hệ cửa hàng");
        }
        account.recordSuccessfulLogin(now);
        SessionTokens tokens = issue(account, UUID.randomUUID(), now.plus(properties.refreshAbsoluteTtl()), now,
                userAgent).tokens();
        return new LoginResult(PortalMeResponse.from(account), tokens);
    }

    /** Cùng thuật toán với AuthService.refresh — lý do từng bước: xem lớp đó. */
    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return new RefreshResult.Rejected();
        }
        Optional<CustomerRefreshToken> found =
                refreshTokenRepository.findForUpdateByTokenHash(TokenService.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return new RefreshResult.Rejected();
        }
        CustomerRefreshToken current = found.get();
        Instant now = clock.instant();
        if (current.isRevoked()) {
            return new RefreshResult.Rejected();
        }
        if (current.isReplaced()) {
            if (!Duration.between(current.getReplacedAt(), now).minus(authProperties.refreshReuseGrace()).isPositive()) {
                return new RefreshResult.Race();
            }
            refreshTokenRepository.revokeFamily(current.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token app khách bị dùng lại sau khi đã thay — thu hồi phiên family={} account={}",
                    current.getFamilyId(), current.getAccount().getId());
            return new RefreshResult.Rejected();
        }
        CustomerAccount account = current.getAccount();
        if (current.isExpired(now) || !account.isActive() || !account.getCustomer().isActive()) {
            return new RefreshResult.Rejected();
        }
        Issued next = issue(account, current.getFamilyId(), current.getFamilyExpiresAt(), now, userAgent);
        current.markReplaced(next.entity(), now);
        return new RefreshResult.Renewed(PortalMeResponse.from(account), next.tokens());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT,
                        clock.instant()));
    }

    /** BR-POR-03, 05: đổi xong được đặt hàng; các thiết bị khác phải đăng nhập lại, thiết bị này giữ phiên. */
    @Transactional
    public void changePassword(Long accountId, String rawRefreshToken, ChangePasswordRequest request) {
        CustomerAccount account = accountRepository.findWithCustomerById(accountId)
                .orElseThrow(() -> new InvalidRequestException("currentPassword", "Tài khoản không còn tồn tại"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidRequestException("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        PasswordPolicy.checkPassword("newPassword", request.newPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new InvalidRequestException("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        account.changePassword(passwordEncoder.encode(request.newPassword()));

        Instant now = clock.instant();
        Optional<CustomerRefreshToken> currentSession = rawRefreshToken == null ? Optional.empty()
                : refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken))
                .filter(token -> token.getAccount().getId().equals(accountId));
        if (currentSession.isPresent()) {
            refreshTokenRepository.revokeOtherFamilies(accountId, currentSession.get().getFamilyId(),
                    RevokeReason.PASSWORD_CHANGED, now);
        } else {
            refreshTokenRepository.revokeAllOfAccount(accountId, RevokeReason.PASSWORD_CHANGED, now);
        }
    }

    private record Issued(CustomerRefreshToken entity, SessionTokens tokens) {
    }

    private Issued issue(CustomerAccount account, UUID familyId, Instant familyExpiresAt, Instant now, String userAgent) {
        String rawRefresh = tokenService.newRefreshToken();
        Instant idleExpiry = now.plus(properties.refreshIdleTtl());
        Instant expiresAt = idleExpiry.isBefore(familyExpiresAt) ? idleExpiry : familyExpiresAt;
        CustomerRefreshToken entity = refreshTokenRepository.save(new CustomerRefreshToken(account, familyId,
                TokenService.hash(rawRefresh), expiresAt, familyExpiresAt, truncate(userAgent)));
        String access = tokenCodec.issueAccessToken(account, now);
        return new Issued(entity, new SessionTokens(access, properties.accessTokenTtl(), rawRefresh,
                Duration.between(now, expiresAt)));
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 200 ? userAgent : userAgent.substring(0, 200);
    }
}
