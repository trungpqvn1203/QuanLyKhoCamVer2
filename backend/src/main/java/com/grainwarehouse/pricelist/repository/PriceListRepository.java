package com.grainwarehouse.pricelist.repository;

import com.grainwarehouse.pricelist.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceListRepository extends JpaRepository<PriceList, Long>, JpaSpecificationExecutor<PriceList> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được trạng thái ngừng dùng
    Optional<PriceList> findByNameIgnoreCase(String name);

    Optional<PriceList> findByNameIgnoreCaseAndIdNot(String name, Long id);

    /** [priceListId, số SP đã có giá] cho một trang bảng giá. */
    @Query("select i.priceList.id, count(i) from PriceListItem i where i.priceList.id in :ids group by i.priceList.id")
    List<Object[]> countItemsByPriceListIds(@Param("ids") Collection<Long> ids);

    /** [priceListId, số khách đang gán] cho một trang bảng giá. */
    @Query("select c.priceList.id, count(c) from Customer c where c.priceList.id in :ids group by c.priceList.id")
    List<Object[]> countCustomersByPriceListIds(@Param("ids") Collection<Long> ids);
}
