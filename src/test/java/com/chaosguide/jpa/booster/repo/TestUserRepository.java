package com.chaosguide.jpa.booster.repo;

import com.chaosguide.jpa.booster.entity.TestUser;
import com.chaosguide.jpa.booster.repository.BoosterNativeRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestUserRepository extends BoosterNativeRepository<TestUser, Long> {
}
