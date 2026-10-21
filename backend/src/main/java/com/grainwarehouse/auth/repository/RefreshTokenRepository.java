package com.grainwarehouse.auth.repository;

import com.grainwarehouse.auth.entity.RefreshToken;
import com.grainwarehouse.auth.entity.RevokeReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * SELECT ... FOR UPDATE: hai request gia hạn cùng một token thì request sau CHỜ request trước commit,
     * rồi mới đọc — thấy token đã bị thay, không phát được 2 token mới từ 1 token cũ.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdateByTokenHash(@Param("hash") String hash);

    Optional<RefreshToken> findByTokenHash(String hash);

    // Câu UPDATE hàng loạt đi thẳng xuống DB, KHÔNG cập nhật các entity Hibernate đang giữ trong bộ nhớ.
    // flushAutomatically: ghi thay đổi đang chờ trước; clearAutomatically: bỏ bản cũ trong bộ nhớ sau đó,
    // để lần đọc kế tiếp trong cùng transaction thấy revoked_at mới (không có nó, AuthApiTest đọc nhầm bản cũ).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") RevokeReason reason, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.user.id = :userId and t.revokedAt is null
            """)
    int revokeAllOfUser(@Param("userId") Long userId, @Param("reason") RevokeReason reason, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :at, t.revokeReason = :reason
            where t.user.id = :userId and t.revokedAt is null and t.familyId <> :keptFamilyId
            """)
    int revokeOtherFamiliesOfUser(@Param("userId") Long userId, @Param("keptFamilyId") UUID keptFamilyId,
                                  @Param("reason") RevokeReason reason, @Param("at") Instant at);
}
