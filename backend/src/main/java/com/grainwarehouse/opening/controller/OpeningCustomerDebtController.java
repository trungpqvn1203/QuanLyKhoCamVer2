package com.grainwarehouse.opening.controller;

import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.service.OpeningDebtService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nợ đầu kỳ của khách: xem, thu như công nợ khách (BR-ODB-08); ghi nhận, đảo chỉ quản lý hệ thống. */
@RestController
@RequestMapping("/api/opening-customer-debts")
public class OpeningCustomerDebtController extends OpeningDebtController {

    public OpeningCustomerDebtController(OpeningDebtService openingDebtService) {
        super(openingDebtService, OpeningDebtParty.CUSTOMER);
    }
}
