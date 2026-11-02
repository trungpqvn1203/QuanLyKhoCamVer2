package com.grainwarehouse.portal.repository;

import com.grainwarehouse.auth.entity.RevokeReason;
import com.grainwarehouse.portal.entity.CustomerRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Cùng các truy vấn với RefreshTokenRepository của nhân viên (lý do từng chỗ: xem lớp đó). */
public interface CustomerRefreshTokenRepository extends JpaRepository<CustomerRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from CustomerRefreshToken t join fetch t.account a join fetch a.customer
            where t.tokenHash = :hash
            """)
    Optional<CustomerRefreshToken> findForUpdateByTokenHash(@Param("hash") String hash);

    Optional<CustomerRefreshToken> findByTokenHash(String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CustomerRefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") RevokeReason reason, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CustomerRefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.account.id = :accountId and t.revokedAt is null
            """)
    int revokeAllOfAccount(@Param("accountId") Long accountId, @Param("reason") RevokeReason reason,
                           @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CustomerRefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.account.id = :accountId and t.revokedAt is null and t.familyId <> :keptFamilyId
            """)
    int revokeOtherFamilies(@Param("accountId") Long accountId, @Param("keptFamilyId") UUID keptFamilyId,
                            @Param("reason") RevokeReason reason, @Param("at") Instant at);
}
