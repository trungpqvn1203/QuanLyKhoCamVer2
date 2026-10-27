package com.grainwarehouse.stockcount.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.stockcount.dto.StockCountReasonRequest;
import com.grainwarehouse.stockcount.dto.StockCountReasonResponse;
import com.grainwarehouse.stockcount.dto.UpdateStockCountReasonRequest;
import com.grainwarehouse.stockcount.entity.StockCountReason;
import com.grainwarehouse.stockcount.repository.StockCountReasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lý do lệch kiểm kê (BR-CNT-05). Cùng cách với danh mục thu/chi: tên không trùng, không xóa cứng. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockCountReasonService {

    private final StockCountReasonRepository stockCountReasonRepository;

    public List<StockCountReasonResponse> list() {
        return stockCountReasonRepository.findAll(Sort.by("name")).stream()
                .map(StockCountReasonResponse::from)
                .toList();
    }

    @Transactional
    public StockCountReasonResponse create(StockCountReasonRequest request) {
        String name = request.name().trim();
        stockCountReasonRepository.findByNameIgnoreCase(name).ifPresent(StockCountReasonService::throwDuplicate);
        return StockCountReasonResponse.from(stockCountReasonRepository.save(new StockCountReason(name)));
    }

    @Transactional
    public StockCountReasonResponse update(Long id, UpdateStockCountReasonRequest request) {
        StockCountReason reason = stockCountReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lý do lệch id=" + id));
        String name = request.name().trim();
        stockCountReasonRepository.findByNameIgnoreCaseAndIdNot(name, id)
                .ifPresent(StockCountReasonService::throwDuplicate);
        reason.setName(name);
        reason.setActive(request.active());
        // saveAndFlush: ép UPDATE chạy ngay để @UpdateTimestamp cập nhật updatedAt trước khi trả response
        return StockCountReasonResponse.from(stockCountReasonRepository.saveAndFlush(reason));
    }

    /** Tên trùng (kể cả lý do đã ngừng dùng) bị từ chối, kèm hướng dẫn mở lại. */
    private static void throwDuplicate(StockCountReason existing) {
        String label = "Lý do '" + existing.getName() + "'";
        if (!existing.isActive()) {
            throw new DuplicateResourceException("name",
                    label + " đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới");
        }
        throw new DuplicateResourceException("name", label + " đã tồn tại");
    }
}
