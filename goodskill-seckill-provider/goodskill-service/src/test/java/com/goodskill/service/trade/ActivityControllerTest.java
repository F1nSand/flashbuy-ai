package com.goodskill.service.trade;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityControllerTest {

    @Test
    void activityCanBeReadWithoutAuthentication() {
        ActivityService service = mock(ActivityService.class);
        ActivityResponse activity = new ActivityResponse(12L, "SALE-12", "Sale", "PUBLISHED",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"),
                1, 1, List.of());
        when(service.publicActivity(12L)).thenReturn(activity);

        var response = new ActivityController(service).getPublic(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(activity, response.getBody().getData());
    }

    @Test
    void createsDraftAndReturnsCreated() {
        ActivityService service = mock(ActivityService.class);
        CreateActivityRequest.ActivitySku sku = new CreateActivityRequest.ActivitySku(
                "SKU-12", "Item", "Blue", 1999L, 2, 100L);
        CreateActivityRequest request = new CreateActivityRequest("SALE-12", "Sale",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"), List.of(sku));
        ActivityResponse activity = new ActivityResponse(12L, "SALE-12", "Sale", "DRAFT",
                request.startsAt(), request.endsAt(), 0, 0,
                List.of(new ActivitySkuResponse(15L, "SKU-12", "Item", "Blue", 1999L, 2, true, 100L, 100L)));
        when(service.create("manager-1", request)).thenReturn(activity);

        var response = new ActivityController(service).create("manager-1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(activity, response.getBody().getData());
        verify(service).create("manager-1", request);
    }

    @Test
    void managementListReturnsActivitiesIncludingDraftsFromService() {
        ActivityService service = mock(ActivityService.class);
        List<ActivityResponse> activities = List.of(new ActivityResponse(12L, "SALE-12", "Sale", "DRAFT",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"),
                0, 0, List.of()));
        when(service.managementActivities(100)).thenReturn(activities);

        var response = new ActivityController(service).listForManagement(100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(activities, response.getBody().getData());
        verify(service).managementActivities(100);
    }

    @Test
    void returnsStableActivityConflict() {
        ActivityService service = mock(ActivityService.class);
        var response = new ActivityController(service)
                .activityError(new ActivityApiException(409, "VERSION_CONFLICT"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("VERSION_CONFLICT", response.getBody().getMsg());
    }
}
