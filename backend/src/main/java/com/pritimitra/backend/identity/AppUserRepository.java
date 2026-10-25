package com.pritimitra.backend.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByCognitoSub(String cognitoSub);

	List<AppUser> findByStatusAndIneligibleAtBefore(AccountStatus status, Instant cutoff);
}
