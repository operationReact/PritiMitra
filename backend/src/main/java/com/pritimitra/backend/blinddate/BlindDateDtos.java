package com.pritimitra.backend.blinddate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public final class BlindDateDtos {
    private BlindDateDtos() {}
    public record OptInRequest(OffsetDateTime startsAt, String timeZone) {}
    public record OptInReceipt(UUID optInId, OffsetDateTime startsAt, String timeZone, String status,
            UUID sessionId, UUID partnerProfilePublicId, String sessionStatus, OffsetDateTime createdAt) {}
    public record OptInList(List<OptInReceipt> items) {}
}
