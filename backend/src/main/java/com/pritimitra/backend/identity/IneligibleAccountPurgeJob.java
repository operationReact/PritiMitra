package com.pritimitra.backend.identity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IneligibleAccountPurgeJob {

	private final IneligibleAccountPurgeService purgeService;

	IneligibleAccountPurgeJob(IneligibleAccountPurgeService purgeService) {
		this.purgeService = purgeService;
	}

	@Scheduled(cron = "${pritimitra.retention.purge-cron}")
	void run() {
		purgeService.purgeExpiredIneligibleAccounts();
	}
}
