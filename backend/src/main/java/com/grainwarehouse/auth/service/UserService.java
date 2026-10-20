package com.grainwarehouse.auth.service;

import com.grainwarehouse.auth.dto.CreateUserRequest;
import com.grainwarehouse.auth.dto.ResetPasswordRequest;
import com.grainwarehouse.auth.dto.UpdateUserRequest;
import com.grainwarehouse.auth.dto.UserResponse;
import com.grainwarehouse.auth.dto.UserSearchCriteria;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.RevokeReason;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.repository.RefreshTokenRepository;
import com.grainwarehouse.auth.repository.UserSpecifications;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

/** Quản lý tài khoản — chỉ SYSTEM_ADMIN gọi được (SecurityConfig). Quy tắc BR-AUTH trong rules.md. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(UserSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(userRepository.findAll(UserSpecifications.from(criteria), pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = PasswordPolicy.normalizeUsername("username", request.username());
        PasswordPolicy.checkPassword("password", request.password());
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Tên đăng nhập '" + username + "' đã được dùng");
        }
        AppUser user = new AppUser(username);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.replaceRoles(request.roles());
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Đổi vai trò hoặc khóa → thu hồi mọi phiên của người đó: access token cũ còn hiệu lực tối đa 15 phút
     * (giới hạn đã chấp nhận ở ADR-0003), sau đó không gia hạn được nữa.
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long currentUserId) {
        AppUser user = find(id);
        boolean deactivating = user.isActive() && !request.active();
        boolean removingAdmin = user.getRoles().contains(Role.SYSTEM_ADMIN) && !request.roles().contains(Role.SYSTEM_ADMIN);
        if (deactivating && id.equals(currentUserId)) {
            throw new BusinessException("Không thể tự khóa tài khoản đang đăng nhập");
        }
        // BR-AUTH-05: luôn còn ít nhất một quản lý hệ thống đang hoạt động, nếu không sẽ không ai mở lại được
        if (user.isActive() && user.getRoles().contains(Role.SYSTEM_ADMIN) && (deactivating || removingAdmin)
                && userRepository.countActiveWithRoleExcluding(Role.SYSTEM_ADMIN, id) == 0) {
            throw new BusinessException("Phải còn ít nhất một tài khoản Quản lý hệ thống đang hoạt động");
        }
        boolean rolesChanged = !user.getRoles().equals(Set.copyOf(request.roles()));

        user.setFullName(request.fullName().trim());
        user.replaceRoles(request.roles());
        user.setActive(request.active());
        if (deactivating || rolesChanged) {
            refreshTokenRepository.revokeAllOfUser(id, RevokeReason.ADMIN_ACTION, clock.instant());
        }
        return UserResponse.from(user);
    }

    /** Quản lý đặt mật khẩu mới cho người quên mật khẩu → người đó đăng nhập lại trên mọi thiết bị. */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = find(id);
        PasswordPolicy.checkPassword("newPassword", request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllOfUser(id, RevokeReason.ADMIN_ACTION, clock.instant());
    }

    private AppUser find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }
}
