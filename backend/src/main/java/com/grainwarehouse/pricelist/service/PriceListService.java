package com.grainwarehouse.pricelist.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.pricelist.dto.CreatePriceListRequest;
import com.grainwarehouse.pricelist.dto.PriceListItemResponse;
import com.grainwarehouse.pricelist.dto.PriceListResponse;
import com.grainwarehouse.pricelist.dto.PriceListSearchCriteria;
import com.grainwarehouse.pricelist.dto.UpdatePriceListRequest;
import com.grainwarehouse.pricelist.entity.PriceList;
import com.grainwarehouse.pricelist.repository.PriceListItemRepository;
import com.grainwarehouse.pricelist.repository.PriceListRepository;
import com.grainwarehouse.pricelist.repository.PriceListSpecifications;
import com.grainwarehouse.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bảng giá bán có tên (BR-PRC-01..07): "Giá lẻ", "Giá đại lý", "Giá trang trại"...
 * Danh sách có phân trang + tìm kiếm để đáp ứng hàng trăm / hàng nghìn bảng giá.
 * Chỉ là giá gợi ý khi lập phiếu bán — giá thực bán chụp trên dòng phiếu (BR-SAL-08).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceListService {

    private final PriceListRepository priceListRepository;
    private final PriceListItemRepository itemRepository;
    private final ProductRepository productRepository;

    public PageResponse<PriceListResponse> list(PriceListSearchCriteria criteria, Pageable pageable) {
        Page<PriceList> page = priceListRepository.findAll(PriceListSpecifications.from(criteria), pageable);
        List<Long> ids = page.getContent().stream().map(PriceList::getId).toList();
        Map<Long, Long> itemCounts = ids.isEmpty()
                ? Map.of()
                : toCountMap(priceListRepository.countItemsByPriceListIds(ids));
        Map<Long, Long> customerCounts = ids.isEmpty()
                ? Map.of()
                : toCountMap(priceListRepository.countCustomersByPriceListIds(ids));
        return PageResponse.from(page, list -> PriceListResponse.from(
                list,
                itemCounts.getOrDefault(list.getId(), 0L),
                customerCounts.getOrDefault(list.getId(), 0L)));
    }

    public PriceListResponse get(Long id) {
        PriceList priceList = findOrThrow(id);
        return toSingleResponse(priceList);
    }

    @Transactional
    public PriceListResponse create(CreatePriceListRequest request) {
        String name = request.name().trim();
        priceListRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);

        PriceList priceList = new PriceList();
        priceList.setName(name);
        priceList.setDescription(blankToNull(request.description()));
        PriceList saved = priceListRepository.save(priceList);
        return PriceListResponse.from(saved, 0L, 0L);
    }

    @Transactional
    public PriceListResponse update(Long id, UpdatePriceListRequest request) {
        PriceList priceList = findOrThrow(id);
        String name = request.name().trim();
        priceListRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);

        priceList.setName(name);
        priceList.setDescription(blankToNull(request.description()));
        priceList.setActive(request.active());
        PriceList saved = priceListRepository.saveAndFlush(priceList);
        return toSingleResponse(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    /** Toàn bộ SP đã có giá trong một bảng giá (dùng khi mở chi tiết bảng giá hoặc lập phiếu bán). */
    public List<PriceListItemResponse> listItems(Long priceListId) {
        requirePriceList(priceListId);
        return itemRepository.findByPriceListId(priceListId).stream()
                .map(PriceListItemResponse::withProduct)
                .toList();
    }

    /** Giá của một nhóm SP trong mọi bảng giá. */
    public List<PriceListItemResponse> listItemsByProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return itemRepository.findByProductIdIn(productIds).stream()
                .map(PriceListItemResponse::compact)
                .toList();
    }

    /** Đặt hoặc ghi đè giá của một SP trong một bảng giá. */
    @Transactional
    public PriceListItemResponse setItemPrice(Long priceListId, Long productId, BigDecimal unitPrice) {
        requirePriceList(priceListId);
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + productId);
        }
        itemRepository.upsert(priceListId, productId, unitPrice);
        return itemRepository.findOne(priceListId, productId)
                .map(PriceListItemResponse::withProduct)
                .orElseThrow();
    }

    @Transactional
    public void removeItemPrice(Long priceListId, Long productId) {
        requirePriceList(priceListId);
        if (itemRepository.deleteOne(priceListId, productId) == 0) {
            throw new ResourceNotFoundException(
                    "Bảng giá id=" + priceListId + " chưa có giá cho sản phẩm id=" + productId);
        }
    }

    private PriceListResponse toSingleResponse(PriceList priceList) {
        List<Long> ids = List.of(priceList.getId());
        Map<Long, Long> itemCounts = toCountMap(priceListRepository.countItemsByPriceListIds(ids));
        Map<Long, Long> customerCounts = toCountMap(priceListRepository.countCustomersByPriceListIds(ids));
        return PriceListResponse.from(
                priceList,
                itemCounts.getOrDefault(priceList.getId(), 0L),
                customerCounts.getOrDefault(priceList.getId(), 0L));
    }

    private void throwDuplicate(PriceList existing) {
        String label = "Bảng giá '" + existing.getName() + "'";
        if (!existing.isActive()) {
            throw new DuplicateResourceException("name", label
                    + " đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException("name", label + " đã tồn tại");
    }

    private PriceList findOrThrow(Long id) {
        return priceListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng giá id=" + id));
    }

    private void requirePriceList(Long id) {
        if (!priceListRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy bảng giá id=" + id);
        }
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).longValue()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
