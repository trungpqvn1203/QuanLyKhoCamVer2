package com.grainwarehouse.customer.repository;

import com.grainwarehouse.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được khách đang giữ SĐT và trạng thái
    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByPhoneAndIdNot(String phone, Long id);
}
