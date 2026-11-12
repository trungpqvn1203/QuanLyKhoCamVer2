package com.grainwarehouse.delivery.service;

import com.grainwarehouse.common.exception.DuplicateResourceException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.delivery.dto.VehicleRequest;
import com.grainwarehouse.delivery.dto.VehicleResponse;
import com.grainwarehouse.delivery.dto.VehicleSearchCriteria;
import com.grainwarehouse.delivery.entity.Vehicle;
import com.grainwarehouse.delivery.repository.VehicleRepository;
import com.grainwarehouse.delivery.repository.VehicleSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/** Danh mục xe giao hàng (BR-VEH-01..04). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {

    // Khớp biểu thức của uk_vehicle_plate (V31): [[:space:].-]
    private static final Pattern PLATE_SEPARATORS = Pattern.compile("[\\s.\\-]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private final VehicleRepository vehicleRepository;

    public PageResponse<VehicleResponse> list(VehicleSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(vehicleRepository.findAll(VehicleSpecifications.from(criteria), pageable),
                VehicleResponse::from);
    }

    public VehicleResponse get(Long id) {
        return VehicleResponse.from(findOrThrow(id));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        apply(vehicle, request, null);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = findOrThrow(id);
        apply(vehicle, request, id);
        if (request.active() != null) {
            vehicle.setActive(request.active());
        }
        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    /** BR-VEH-04: không xóa cứng — chuyến cũ vẫn trỏ tới xe. Xe ngừng dùng không chọn được cho chuyến mới. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    /** BR-VEH-01: dạng so trùng — "29h 123.45", "29H-123.45", "29H12345" đều thành "29H12345". */
    static String normalize(String plate) {
        return PLATE_SEPARATORS.matcher(plate).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** Dạng lưu và in ra: viết hoa, gộp khoảng trắng — "29h  123.45" → "29H 123.45". */
    static String clean(String plate) {
        return MULTI_SPACE.matcher(plate.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    private void apply(Vehicle vehicle, VehicleRequest request, Long excludeId) {
        String plate = clean(request.plateNumber());
        vehicleRepository.findByNormalizedPlate(normalize(plate), excludeId).ifPresent(existing -> {
            String label = "Xe biển số " + existing.getPlateNumber();
            throw new DuplicateResourceException(existing.isActive() ? label + " đã có trong danh mục"
                    : label + " đã có nhưng đang ngừng dùng. Hãy mở lại xe thay vì thêm mới");
        });
        vehicle.setPlateNumber(plate);
        vehicle.setName(request.name() == null || request.name().isBlank() ? null : request.name().trim());
        vehicle.setCapacityTons(request.capacityTons());
    }

    private Vehicle findOrThrow(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe id=" + id));
    }
}
