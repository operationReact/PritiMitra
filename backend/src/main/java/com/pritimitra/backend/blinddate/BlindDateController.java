package com.pritimitra.backend.blinddate;
import com.pritimitra.backend.blinddate.BlindDateDtos.OptInList;
import com.pritimitra.backend.blinddate.BlindDateDtos.OptInReceipt;
import com.pritimitra.backend.blinddate.BlindDateDtos.OptInRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blind-dates")
class BlindDateController {
    private final BlindDateService blindDates;
    BlindDateController(BlindDateService blindDates) { this.blindDates = blindDates; }
    @GetMapping("/my") OptInList mine(@AuthenticationPrincipal Jwt jwt) { return blindDates.mine(jwt.getSubject()); }
    @PostMapping("/opt-ins") @ResponseStatus(HttpStatus.CREATED)
    OptInReceipt optIn(@AuthenticationPrincipal Jwt jwt, @RequestBody OptInRequest request) {
        return blindDates.optIn(jwt.getSubject(), request);
    }
    @DeleteMapping("/opt-ins/{optInPublicId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID optInPublicId) {
        blindDates.withdraw(jwt.getSubject(), optInPublicId);
    }
    @DeleteMapping("/sessions/{sessionPublicId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionPublicId) {
        blindDates.cancelSession(jwt.getSubject(), sessionPublicId);
    }
}
