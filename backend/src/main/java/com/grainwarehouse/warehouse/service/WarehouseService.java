package com.grainwarehouse.warehouse.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.warehouse.dto.CreateWarehouseRequest;
import com.grainwarehouse.warehouse.dto.UpdateWarehouseRequest;
import com.grainwarehouse.warehouse.dto.WarehouseResponse;
import com.grainwarehouse.warehouse.dto.WarehouseSearchCriteria;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import com.grainwarehouse.warehouse.repository.WarehouseSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public PageResponse<WarehouseResponse> list(WarehouseSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                warehouseRepository.findAll(WarehouseSpecifications.from(criteria), pageable), WarehouseResponse::from);
    }

    public WarehouseResponse get(Long id) {
        return WarehouseResponse.from(findOrThrow(id));
    }

    @Transactional
    public WarehouseResponse create(CreateWarehouseRequest request) {
        String name = request.name().trim();
        warehouseRepository.findByNameIgnoreCase(name).ifPresent(this::throwDuplicate);

        Warehouse warehouse = new Warehouse();
        warehouse.setName(name);
        warehouse.setType(request.type());
        warehouse.setAddress(request.address().trim());
        // Sau save(), code đã có giá trị nhờ @Generated (BR-WH-02)
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);
        String name = request.name().trim();
        warehouseRepository.findByNameIgnoreCaseAndIdNot(name, id).ifPresent(this::throwDuplicate);

        warehouse.setName(name);
        warehouse.setType(request.type());
        warehouse.setAddress(request.address().trim());
        warehouse.setActive(request.active());
        return WarehouseResponse.from(warehouseRepository.saveAndFlush(warehouse));
    }

    /**
     * BR-WH-04: không xóa cứng, chỉ ngừng hoạt động.
     * TODO BR-WH-05: chặn khi kho còn tồn hàng — bổ sung khi có bảng inventory (Tuần 2).
     */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    /**
     * BR-WH-03, BR-WH-04: tên trùng (kể cả với kho đã ngừng hoạt động) bị từ chối.
     * Nếu bản ghi trùng đang ngừng hoạt động → hướng dẫn mở lại để giữ nguyên mã và lịch sử.
     */
    private void throwDuplicate(Warehouse existing) {
        String label = "Kho '" + existing.getName() + "' (" + existing.getCode() + ")";
        if (!existing.isActive()) {
            throw new DuplicateResourceException(label
                    + " đã tồn tại nhưng đang ngừng hoạt động. Hãy mở lại kho thay vì tạo mới");
        }
        throw new DuplicateResourceException(label + " đã tồn tại");
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho id=" + id));
    }
}
