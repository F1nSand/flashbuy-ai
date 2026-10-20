package com.goodskill.service.trade;

import java.time.Instant;
import java.util.List;

public record ActivityResponse(
        long activityId,
        String activityCode,
        String title,
        String state,
        Instant startsAt,
        Instant endsAt,
        long publishedVersion,
        long rowVersion,
        List<ActivitySkuResponse> skus) {
}
