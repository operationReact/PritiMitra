package com.pritimitra.backend.identity;

import java.util.UUID;

public record AccountDeletionResponse(UUID requestId, String status) {
}
