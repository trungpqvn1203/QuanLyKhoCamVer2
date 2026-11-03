package com.grainwarehouse.delivery.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.delivery.dto.DeliveryOrderLineResponse;
import com.grainwarehouse.delivery.dto.DeliveryOrderResponse;
import com.grainwarehouse.delivery.dto.DeliveryTripRequest;
import com.grainwarehouse.delivery.dto.DeliveryTripResponse;
import com.grainwarehouse.delivery.dto.DeliveryTripSearchCriteria;
import com.grainwarehouse.delivery.dto.DeliveryTripSummaryResponse;
import com.grainwarehouse.delivery.dto.DriverResponse;
import com.grainwarehouse.delivery.entity.DeliveryTrip;
import com.grainwarehouse.delivery.entity.DeliveryTripStatus;
import com.grainwarehouse.delivery.entity.DeliveryTripStop;
import com.grainwarehouse.delivery.entity.Vehicle;
import com.grainwarehouse.delivery.repository.DeliveryTripRepository;
import com.grainwarehouse.delivery.repository.DeliveryTripSpecifications;
import com.grainwarehouse.delivery.repository.DeliveryTripStopRepository;
import com.grainwarehouse.delivery.repository.VehicleRepository;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.sales.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chuyến giao hàng (BR-DLV-01..10). Chuyến chỉ gom phiếu bán Nháp theo thứ tự giao; tồn kho chỉ đổi lúc Xuất chuyến,
 * qua đúng logic Hoàn tất của phiếu bán (SalesOrderService.completeForDelivery) — không có đường trừ tồn thứ hai.
 *
 * <p>Thứ tự khóa của mọi thao tác: chuyến (nếu có) → phiếu bán theo id tăng dần → dòng tồn (khi xuất). Không thao
 * tác nào khóa phiếu trước rồi mới khóa chuyến, nên hai thao tác chạy cùng lúc không chờ vòng tròn (deadlock).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryTripService {

    private static final String CODE_FORMAT = "CG%06d";

    private final DeliveryTripRepository tripRepository;
    private final DeliveryTripStopRepository stopRepository;
    private final VehicleRepository vehicleRepository;
    private final EmployeeRepository employeeRepository;
    private final StockDocumentRepository stockDocumentRepository;
    private final SalesOrderService salesOrderService;

    public PageResponse<DeliveryTripSummaryResponse> list(DeliveryTripSearchCriteria criteria, Pageable pageable) {
        Page<DeliveryTrip> page = tripRepository.findAll(DeliveryTripSpecifications.from(criteria), pageable);
        List<Long> tripIds = page.getContent().stream().map(DeliveryTrip::getId).toList();
        List<DeliveryTripStop> stops = tripIds.isEmpty() ? List.of() : stopRepository.findByTripIds(tripIds);
        Map<Long, BigDecimal> tonsByDocument = tonsByDocument(
                stops.stream().map(stop -> stop.getDocument().getId()).toList());
        Map<Long, List<DeliveryTripStop>> stopsByTrip = stops.stream()
                .collect(Collectors.groupingBy(stop -> stop.getTrip().getId()));

        return PageResponse.from(page, trip -> {
            List<DeliveryTripStop> tripStops = stopsByTrip.getOrDefault(trip.getId(), List.of());
            BigDecimal tons = tripStops.stream()
                    .map(stop -> tonsByDocument.getOrDefault(stop.getDocument().getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return DeliveryTripSummaryResponse.from(trip, tripStops.size(), tons);
        });
    }

    public DeliveryTripResponse get(Long id) {
        return toResponse(tripRepository.findWithDetailsById(id).orElseThrow(() -> notFound(id)));
    }

    /** BR-DLV-03: phiếu bán Nháp chưa nằm trên chuyến nào — danh sách để chọn khi xếp chuyến. */
    public List<DeliveryOrderResponse> assignableOrders() {
        List<StockDocument> documents = stopRepository.findAssignableOrders();
        Map<Long, List<DeliveryOrderLineResponse>> lines = linesByDocument(
                documents.stream().map(StockDocument::getId).toList());
        return documents.stream()
                .map(document -> DeliveryOrderResponse.from(null, document, lines.getOrDefault(document.getId(), List.of())))
                .toList();
    }

    /** Lái xe chọn được: nhân viên đang làm, theo tên. */
    public List<DriverResponse> drivers() {
        return employeeRepository.findByActiveTrue(Sort.by("name", "id")).stream().map(DriverResponse::from).toList();
    }

    @Transactional
    public DeliveryTripResponse create(DeliveryTripRequest request, String username) {
        DeliveryTrip trip = new DeliveryTrip(CODE_FORMAT.formatted(tripRepository.nextCodeNumber()), username);
        apply(trip, request);
        return toResponse(tripRepository.saveAndFlush(trip));
    }

    /** BR-DLV-05: sửa ngày, xe, lái xe, ghi chú và danh sách / thứ tự điểm giao — chỉ khi Đang xếp. */
    @Transactional
    public DeliveryTripResponse update(Long id, DeliveryTripRequest request) {
        DeliveryTrip trip = lockForChange(id, "sửa");
        apply(trip, request);
        return toResponse(tripRepository.saveAndFlush(trip));
    }

    /** BR-DLV-09: chuyến Đang xếp chưa làm gì tới tồn / tiền → xóa hẳn; phiếu bán trở về "chưa xếp chuyến". */
    @Transactional
    public void delete(Long id) {
        tripRepository.delete(lockForChange(id, "xóa"));
    }

    /**
     * BR-DLV-06: xuất chuyến = hoàn tất mọi phiếu (trừ tồn, ghi StockTransaction) + chuyến sang Đã xuất, trong MỘT
     * transaction. Thiếu hàng ở bất kỳ phiếu nào → rollback toàn bộ: không phiếu nào bị trừ, chuyến vẫn Đang xếp.
     */
    @Transactional
    public DeliveryTripResponse dispatch(Long id, String username) {
        DeliveryTrip trip = lockForChange(id, "xuất");
        if (trip.getStops().isEmpty()) {
            throw new BusinessException("Chuyến " + trip.getCode() + " chưa có phiếu nào để xuất");
        }
        requireActive(trip.getVehicle());
        requireActive(trip.getDriver());

        salesOrderService.completeForDelivery(
                trip.getStops().stream().map(stop -> stop.getDocument().getId()).toList(), trip.getCode());
        trip.markDispatched(Instant.now(), username);
        return toResponse(tripRepository.saveAndFlush(trip));
    }

    /** Khóa chuyến trước, kiểm trạng thái sau (như BR-IMP-14) — phải là lần đọc chuyến đầu tiên trong transaction. */
    private DeliveryTrip lockForChange(Long id, String action) {
        DeliveryTrip trip = tripRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        if (trip.getStatus() != DeliveryTripStatus.PLANNED) {
            throw new BusinessException("Chuyến " + trip.getCode() + " đang ở trạng thái " + trip.getStatus().label()
                    + ". Chỉ chuyến Đang xếp mới được " + action);
        }
        return trip;
    }

    /** Kiểm và ghi mọi thứ request tham chiếu tới. Thứ tự: 400 → 404 → 409, như phiếu bán. */
    private void apply(DeliveryTrip trip, DeliveryTripRequest request) {
        List<Long> documentIds = distinctDocumentIds(request.documentIds());

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe id=" + request.vehicleId()));
        Employee driver = employeeRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên id=" + request.driverId()));
        List<StockDocument> documents = lockDocuments(documentIds);

        // BR-VEH-04: xe / lái xe ngừng hoạt động không chọn MỚI được; chuyến cũ đang dùng thì vẫn sửa được phần khác
        if (!vehicle.getId().equals(idOf(trip.getVehicle()))) {
            requireActive(vehicle);
        }
        if (!driver.getId().equals(idOf(trip.getDriver()))) {
            requireActive(driver);
        }
        requireAssignable(trip, documents);

        trip.setTripDate(request.tripDate());
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        trip.replaceStops(documents);
    }

    // BR-DLV-03: mỗi phiếu một điểm giao
    private static List<Long> distinctDocumentIds(List<Long> documentIds) {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < documentIds.size(); i++) {
            if (!seen.add(documentIds.get(i))) {
                throw new InvalidRequestException("documentIds[" + i + "]", "Phiếu này đã có ở một điểm giao khác của chuyến");
            }
        }
        return documentIds;
    }

    /**
     * Khóa phiếu bán FOR UPDATE theo id tăng dần rồi mới kiểm: hai người cùng xếp PX000123 vào hai chuyến thì người sau
     * chờ người trước commit, rồi thấy phiếu đã có chuyến. Cũng chặn được phiếu bị hủy / hoàn tất đúng lúc đang xếp.
     * Trả về theo thứ tự của request (= thứ tự giao).
     */
    private List<StockDocument> lockDocuments(List<Long> documentIds) {
        Map<Long, StockDocument> locked = documentIds.stream().sorted()
                .map(id -> stockDocumentRepository.findForUpdate(id, StockDocumentType.OUTBOUND)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu bán id=" + id)))
                .collect(Collectors.toMap(StockDocument::getId, Function.identity()));
        return documentIds.stream().map(locked::get).toList();
    }

    /**
     * BR-DLV-03: phiếu MỚI thêm vào chuyến phải là Nháp và chưa ở chuyến khác. Phiếu đã ở chuyến này mà sau đó bị hủy /
     * hoàn tất riêng thì vẫn giữ được khi sửa (để đổi thứ tự các điểm khác) — Xuất chuyến mới chặn (BR-DLV-07).
     */
    private void requireAssignable(DeliveryTrip trip, List<StockDocument> documents) {
        Set<Long> alreadyHere = trip.getStops().stream().map(stop -> stop.getDocument().getId())
                .collect(Collectors.toSet());
        List<StockDocument> added = documents.stream().filter(document -> !alreadyHere.contains(document.getId())).toList();

        List<String> notDraft = added.stream().filter(document -> document.getStatus() != StockDocumentStatus.DRAFT)
                .map(document -> document.getCode() + " (" + document.getStatus().label() + ")").toList();
        if (!notDraft.isEmpty()) {
            throw new BusinessException("Chỉ xếp được phiếu bán Nháp vào chuyến: " + String.join(", ", notDraft));
        }
        if (added.isEmpty()) {
            return;
        }
        List<String> elsewhere = stopRepository.findByDocumentIds(added.stream().map(StockDocument::getId).toList())
                .stream()
                .filter(stop -> !Objects.equals(stop.getTrip().getId(), trip.getId()))
                .map(stop -> stop.getDocument().getCode() + " đã ở chuyến " + stop.getTrip().getCode())
                .toList();
        if (!elsewhere.isEmpty()) {
            throw new BusinessException("Mỗi phiếu chỉ nằm trên một chuyến: " + String.join(", ", elsewhere));
        }
    }

    private static void requireActive(Vehicle vehicle) {
        if (!vehicle.isActive()) {
            throw new BusinessException("Xe " + vehicle.getPlateNumber() + " đang ngừng dùng, không chọn cho chuyến được");
        }
    }

    private static void requireActive(Employee driver) {
        if (!driver.isActive()) {
            throw new BusinessException("Nhân viên '" + driver.getName() + "' (" + driver.getCode()
                    + ") đang ngừng hoạt động, không chọn làm lái xe được");
        }
    }

    private DeliveryTripResponse toResponse(DeliveryTrip trip) {
        List<DeliveryTripStop> stops = trip.getStops();
        Map<Long, List<DeliveryOrderLineResponse>> lines = linesByDocument(
                stops.stream().map(stop -> stop.getDocument().getId()).toList());
        return DeliveryTripResponse.from(trip, stops.stream()
                .map(stop -> DeliveryOrderResponse.from(stop.getStopOrder(), stop.getDocument(),
                        lines.getOrDefault(stop.getDocument().getId(), List.of())))
                .toList());
    }

    private Map<Long, List<DeliveryOrderLineResponse>> linesByDocument(Collection<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return stopRepository.findItemsOfDocuments(documentIds).stream()
                .collect(Collectors.groupingBy(item -> item.getDocument().getId(),
                        Collectors.mapping(DeliveryOrderLineResponse::from, Collectors.toList())));
    }

    // Tấn mỗi phiếu = tổng tấn từng dòng (mỗi dòng làm tròn riêng, như totalTons của phiếu bán)
    private Map<Long, BigDecimal> tonsByDocument(Collection<Long> documentIds) {
        return linesByDocument(documentIds).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(DeliveryOrderLineResponse::tons)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Không tìm thấy chuyến giao id=" + id);
    }

    // So theo id: trip.getVehicle() có thể là proxy lazy — getId() của proxy không cần tải bản ghi
    private static Long idOf(Vehicle vehicle) {
        return vehicle == null ? null : vehicle.getId();
    }

    private static Long idOf(Employee employee) {
        return employee == null ? null : employee.getId();
    }
}
