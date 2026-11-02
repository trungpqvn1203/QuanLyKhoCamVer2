package com.grainwarehouse.portal.repository;

import com.grainwarehouse.portal.entity.CustomerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    /**
     * Đăng nhập: khóa dòng tài khoản trước khi đếm sai mật khẩu (BR-POR-04). Không khóa thì 5 request sai cùng lúc
     * cùng đọc "đã sai 0 lần" rồi cùng ghi 1 → không bao giờ bị khóa tạm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerAccount a join fetch a.customer c where c.phone = :phone")
    Optional<CustomerAccount> findForUpdateByPhone(@Param("phone") String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerAccount a join fetch a.customer where a.customer.id = :customerId")
    Optional<CustomerAccount> findForUpdateByCustomerId(@Param("customerId") Long customerId);

    @Query("select a from CustomerAccount a join fetch a.customer where a.customer.id = :customerId")
    Optional<CustomerAccount> findByCustomerId(@Param("customerId") Long customerId);

    @Query("select a from CustomerAccount a join fetch a.customer where a.id = :id")
    Optional<CustomerAccount> findWithCustomerById(@Param("id") Long id);
}
