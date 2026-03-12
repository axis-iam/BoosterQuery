package com.chaosguide.jpa.booster.repo;

import com.chaosguide.jpa.booster.entity.TestUser;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSmartUserRepository extends ITestSmartUserRepository {
    TestUser findByAge(Integer age);
}
