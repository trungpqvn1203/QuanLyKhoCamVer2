package com.grainwarehouse.store.service;

import com.grainwarehouse.store.dto.StoreInfoRequest;
import com.grainwarehouse.store.dto.StoreInfoResponse;
import com.grainwarehouse.store.entity.StoreInfo;
import com.grainwarehouse.store.repository.StoreInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thông tin cửa hàng in trên phiếu (BR-PRT-01). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreInfoService {

    private final StoreInfoRepository storeInfoRepository;

    public StoreInfoResponse get() {
        return StoreInfoResponse.from(storeInfoRepository.findById(StoreInfo.ID).orElse(null));
    }

    /**
     * Tạo ở lần lưu đầu, sau đó ghi đè. Hai người lưu lần đầu cùng lúc: người sau vi phạm khóa chính
     * → GlobalExceptionHandler trả 409, lưu lại là xong.
     */
    @Transactional
    public StoreInfoResponse save(StoreInfoRequest request) {
        StoreInfo info = storeInfoRepository.findById(StoreInfo.ID).orElseGet(StoreInfo::create);
        info.update(request.name().trim(), blankToNull(request.address()), blankToNull(request.phone()),
                blankToNull(request.receiptFooter()));
        // saveAndFlush: ép ghi ngay để @UpdateTimestamp có giá trị trong response
        return StoreInfoResponse.from(storeInfoRepository.saveAndFlush(info));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
