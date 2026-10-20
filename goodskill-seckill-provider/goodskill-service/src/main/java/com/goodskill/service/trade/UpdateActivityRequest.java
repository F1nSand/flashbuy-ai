package com.goodskill.service.trade;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateActivityRequest(
        @NotNull Long expectedVersion,
        @Size(max = 200) String title,
        Instant startsAt,
        Instant endsAt) {
}
