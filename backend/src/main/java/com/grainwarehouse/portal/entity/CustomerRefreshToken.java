package com.grainwarehouse.portal.entity;

import com.grainwarehouse.auth.entity.RevokeReason;
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
 * Refresh token của app khách — cùng vòng đời với RefreshToken của nhân viên (ADR-0003 mục 1–3): chỉ lưu SHA-256,
 * rotation theo family, thu hồi được. Bảng riêng (V27) để phiên khách và phiên nhân viên không bao giờ lẫn nhau.
 */
@Getter
@Entity
@Table(name = "customer_refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_account_id", nullable = false, updatable = false)
    private CustomerAccount account;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false, updatable = false)
    private Instant familyExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private CustomerRefreshToken replacedBy;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private RevokeReason revokeReason;

    @Column(name = "user_agent", length = 200)
    private String userAgent;

    public CustomerRefreshToken(CustomerAccount account, UUID familyId, String tokenHash, Instant expiresAt,
                                Instant familyExpiresAt, String userAgent) {
        this.account = account;
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

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
    }

    public void markReplaced(CustomerRefreshToken next, Instant at) {
        this.replacedBy = next;
        this.replacedAt = at;
    }
}
