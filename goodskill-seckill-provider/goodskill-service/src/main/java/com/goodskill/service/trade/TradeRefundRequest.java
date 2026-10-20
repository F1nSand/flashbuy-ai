package com.goodskill.service.trade;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TradeRefundRequest(@NotBlank @Size(max = 500) String reason) {
}
