package com.chaosguide.boosterquery.repo;

import com.chaosguide.boosterquery.entity.TestUser;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSmartUserRepository extends ITestSmartUserRepository {
    TestUser findByAge(Integer age);
}
