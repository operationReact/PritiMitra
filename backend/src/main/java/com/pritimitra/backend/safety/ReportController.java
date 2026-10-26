package com.pritimitra.backend.safety;

import com.pritimitra.backend.safety.ReportDtos.ReportReceipt;
import com.pritimitra.backend.safety.ReportDtos.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated report submission. Reports are private and returned only as a receipt to the reporter. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController {

	private final ReportService reports;

	ReportController(ReportService reports) {
		this.reports = reports;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ReportReceipt report(@AuthenticationPrincipal Jwt jwt, @RequestBody ReportRequest request) {
		return reports.report(jwt.getSubject(), request);
	}
}
