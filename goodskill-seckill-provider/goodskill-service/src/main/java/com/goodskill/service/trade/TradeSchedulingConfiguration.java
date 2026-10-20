package com.goodskill.service.trade;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("production")
@EnableScheduling
public class TradeSchedulingConfiguration {
}
