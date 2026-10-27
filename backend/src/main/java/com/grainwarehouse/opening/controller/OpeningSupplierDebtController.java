package com.grainwarehouse.opening.controller;

import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.service.OpeningDebtService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nợ đầu kỳ phải trả NCC: xem, trả như công nợ NCC (BR-ODB-08); ghi nhận, đảo chỉ quản lý hệ thống. */
@RestController
@RequestMapping("/api/opening-supplier-debts")
public class OpeningSupplierDebtController extends OpeningDebtController {

    public OpeningSupplierDebtController(OpeningDebtService openingDebtService) {
        super(openingDebtService, OpeningDebtParty.SUPPLIER);
    }
}
