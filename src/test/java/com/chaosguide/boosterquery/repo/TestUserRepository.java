package com.chaosguide.boosterquery.repo;

import com.chaosguide.boosterquery.entity.TestUser;
import com.chaosguide.boosterquery.repository.BoosterNativeRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestUserRepository extends BoosterNativeRepository<TestUser, Long> {
}
