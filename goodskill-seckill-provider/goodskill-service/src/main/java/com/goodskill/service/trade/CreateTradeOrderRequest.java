package com.goodskill.service.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTradeOrderRequest(
        @NotNull Long skuId,
        @NotNull @Min(1) @Max(100) Integer quantity,
        @NotNull @Valid ShippingAddress shippingAddress) {

    public record ShippingAddress(
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,24}$") String recipientPhone,
            @NotBlank @Size(max = 100) String province,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 300) String addressLine) {
    }
}
