package com.grainwarehouse.auth.service;

import com.grainwarehouse.auth.config.AuthProperties;
import com.grainwarehouse.auth.dto.ChangePasswordRequest;
import com.grainwarehouse.auth.dto.CurrentUserResponse;
import com.grainwarehouse.auth.dto.LoginRequest;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.RefreshToken;
import com.grainwarehouse.auth.entity.RevokeReason;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.repository.RefreshTokenRepository;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Đăng nhập, gia hạn (rotation), đăng xuất, đổi mật khẩu — ADR-0003 mục 1–3.
 * Kết quả gia hạn là một kiểu {@link RefreshResult} thay vì ném exception: khi phát hiện token bị dùng lại, việc
 * thu hồi cả family phải được COMMIT dù request trả 401. Ném exception trong @Transactional thì bị rollback mất.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    /** Hai cookie cần đặt sau đăng nhập / gia hạn; maxAge để trình duyệt tự bỏ cookie khi hết hạn. */
    public record SessionTokens(String accessToken, Duration accessMaxAge, String refreshToken, Duration refreshMaxAge) {
    }

    public record LoginResult(CurrentUserResponse user, SessionTokens tokens) {
    }

    public sealed interface RefreshResult {
        /** Đã phát cặp token mới, token cũ bị đánh dấu "đã thay". */
        record Renewed(CurrentUserResponse user, SessionTokens tokens) implements RefreshResult {
        }

        /** Token vừa bị thay trong thời gian ân hạn — tab khác đã gia hạn trước; cookie mới đã có trong trình duyệt. */
        record Race() implements RefreshResult {
        }

        /** Không hợp lệ / hết hạn / bị thu hồi → phải đăng nhập lại. */
        record Rejected() implements RefreshResult {
        }
    }

    /**
     * AuthenticationManager ném BadCredentialsException (sai tên hoặc mật khẩu — không phân biệt, để không lộ
     * tên đăng nhập nào tồn tại) hoặc DisabledException (đúng mật khẩu nhưng bị khóa); GlobalExceptionHandler → 401.
     */
    @Transactional
    public LoginResult login(LoginRequest request, String userAgent) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Instant now = clock.instant();
        user.setLastLoginAt(now);
        // Mỗi lần đăng nhập là một family mới → đăng nhập nhiều thiết bị độc lập nhau
        SessionTokens tokens = issue(user, UUID.randomUUID(), now.plus(properties.refreshAbsoluteTtl()), now, userAgent)
                .tokens();
        return new LoginResult(CurrentUserResponse.from(user), tokens);
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return new RefreshResult.Rejected();
        }
        Optional<RefreshToken> found = refreshTokenRepository.findForUpdateByTokenHash(TokenService.hash(rawRefreshToken));
        if (found.isEmpty()) {
            return new RefreshResult.Rejected();
        }
        RefreshToken current = found.get();
        Instant now = clock.instant();

        if (current.isRevoked()) {
            return new RefreshResult.Rejected();
        }
        if (current.isReplaced()) {
            if (!Duration.between(current.getReplacedAt(), now).minus(properties.refreshReuseGrace()).isPositive()) {
                return new RefreshResult.Race();
            }
            // Token đã thay từ lâu mà vẫn có người dùng → một trong hai bên đang giữ bản sao bị đánh cắp.
            // Không biết bên nào là thật → thu hồi cả family, mọi bên phải đăng nhập lại.
            refreshTokenRepository.revokeFamily(current.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
            log.warn("Refresh token bị dùng lại sau khi đã thay — thu hồi phiên family={} user={}",
                    current.getFamilyId(), current.getUser().getId());
            return new RefreshResult.Rejected();
        }
        AppUser user = current.getUser();
        if (current.isExpired(now) || !user.isActive()) {
            return new RefreshResult.Rejected();
        }

        Issued next = issue(user, current.getFamilyId(), current.getFamilyExpiresAt(), now, userAgent);
        current.markReplaced(next.entity(), now);
        return new RefreshResult.Renewed(CurrentUserResponse.from(user), next.tokens());
    }

    /** Thu hồi phiên của thiết bị này. Không có / sai cookie vẫn coi như đã đăng xuất (idempotent). */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT,
                        clock.instant()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(Long userId) {
        return CurrentUserResponse.from(findUser(userId));
    }

    /** Đổi mật khẩu → các thiết bị khác phải đăng nhập lại; thiết bị đang dùng giữ phiên. */
    @Transactional
    public void changePassword(Long userId, String rawRefreshToken, ChangePasswordRequest request) {
        AppUser user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        PasswordPolicy.checkPassword("newPassword", request.newPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new InvalidRequestException("newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        Instant now = clock.instant();
        Optional<RefreshToken> currentSession = rawRefreshToken == null ? Optional.empty()
                : refreshTokenRepository.findByTokenHash(TokenService.hash(rawRefreshToken))
                .filter(token -> token.getUser().getId().equals(userId));
        if (currentSession.isPresent()) {
            refreshTokenRepository.revokeOtherFamiliesOfUser(userId, currentSession.get().getFamilyId(),
                    RevokeReason.PASSWORD_CHANGED, now);
        } else {
            refreshTokenRepository.revokeAllOfUser(userId, RevokeReason.PASSWORD_CHANGED, now);
        }
    }

    private record Issued(RefreshToken entity, SessionTokens tokens) {
    }

    /** Hạn của refresh token mới = 8 giờ từ bây giờ, nhưng không vượt hạn tuyệt đối của family. */
    private Issued issue(AppUser user, UUID familyId, Instant familyExpiresAt, Instant now, String userAgent) {
        String rawRefresh = tokenService.newRefreshToken();
        Instant idleExpiry = now.plus(properties.refreshIdleTtl());
        Instant expiresAt = idleExpiry.isBefore(familyExpiresAt) ? idleExpiry : familyExpiresAt;
        RefreshToken entity = refreshTokenRepository.save(new RefreshToken(user, familyId, TokenService.hash(rawRefresh),
                expiresAt, familyExpiresAt, truncate(userAgent)));
        String access = tokenService.issueAccessToken(user, now);
        return new Issued(entity, new SessionTokens(access, properties.accessTokenTtl(), rawRefresh,
                Duration.between(now, expiresAt)));
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 200 ? userAgent : userAgent.substring(0, 200);
    }
}
