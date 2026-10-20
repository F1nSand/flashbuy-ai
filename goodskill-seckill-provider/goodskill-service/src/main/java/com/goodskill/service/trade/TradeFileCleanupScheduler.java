package com.goodskill.service.trade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
@ConditionalOnProperty(prefix = "goodskill.files", name = "enabled", havingValue = "true")
public class TradeFileCleanupScheduler {

    private final TradeFileAssetService fileAssetService;

    public TradeFileCleanupScheduler(TradeFileAssetService fileAssetService) {
        this.fileAssetService = fileAssetService;
    }

    @Scheduled(fixedDelayString = "${goodskill.files.cleanup-delay-ms:3600000}",
            initialDelayString = "${goodskill.files.cleanup-initial-delay-ms:60000}")
    public void cleanOrphans() {
        fileAssetService.cleanupOrphans();
    }
}
