package com.goodskill.service.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TradeShipmentRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String carrierCode,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,100}") String trackingNo) {
}
