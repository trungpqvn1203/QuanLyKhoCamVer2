package com.grainwarehouse.store.repository;

import com.grainwarehouse.store.entity.StoreInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreInfoRepository extends JpaRepository<StoreInfo, Short> {
}
