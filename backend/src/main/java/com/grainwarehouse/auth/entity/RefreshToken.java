package com.grainwarehouse.auth.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Một refresh token đã phát (ADR-0003 mục 1–3). Chỉ lưu SHA-256 của token, token gốc nằm trong cookie GW_RT.
 * Vòng đời một dòng: đang dùng → bị thay bằng dòng mới (rotation) hoặc bị thu hồi. Không bao giờ "sống lại".
 */
@Getter
@Entity
@Table(name = "refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    // Cột CHAR(64) cố định độ dài (SHA-256 hex luôn 64 ký tự); mặc định Hibernate hiểu String là VARCHAR
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private RevokeReason revokeReason;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    public RefreshToken(AppUser user, UUID familyId, String tokenHash, Instant expiresAt, Instant familyExpiresAt,
                        String userAgent) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.userAgent = userAgent;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isReplaced() {
        return replacedAt != null;
    }

    /** Hết hạn "không thao tác 8 giờ" hoặc hết hạn tuyệt đối của cả phiên. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
    }

    public void markReplaced(RefreshToken next, Instant at) {
        this.replacedBy = next;
        this.replacedAt = at;
    }
}
