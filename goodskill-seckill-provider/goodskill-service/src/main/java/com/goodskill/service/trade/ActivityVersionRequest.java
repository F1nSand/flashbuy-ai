package com.goodskill.service.trade;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ActivityVersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {
}
