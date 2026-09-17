package com.promptportal.repository;

import com.promptportal.domain.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    List<UserAccount> findAllByEmailIgnoreCase(String email);

    Optional<UserAccount> findByProviderAndProviderId(String provider, String providerId);
}
