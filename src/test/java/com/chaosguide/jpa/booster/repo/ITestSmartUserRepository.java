package com.chaosguide.jpa.booster.repo;

import com.chaosguide.jpa.booster.dto.UserDTO;
import com.chaosguide.jpa.booster.entity.TestUser;
import com.chaosguide.jpa.booster.annotation.BoosterQuery;
import com.chaosguide.jpa.booster.repository.BoosterQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ITestSmartUserRepository extends BoosterQueryRepository<TestUser, Long> {

    default Page<UserDTO> findUserDTOByAge(Integer age,
                                           Pageable pageable) {
        String sql = "select name, email from t_test_user where age = :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", age);
        return boosterQuery(sql, params, pageable, UserDTO.class);
    }

    @BoosterQuery("select name as name, email as email from t_test_user where age = :age")
    Page<UserDTO> findUserDTOByAgeAnno(@Param("age") Integer age, Pageable pageable);

    @BoosterQuery("select name, email from t_test_user where age = :age")
    Page<UserDTO> findUserDTOByAgeAnnoNoAs(@Param("age") Integer age, Pageable pageable);

    @BoosterQuery("select * from t_test_user where age >= :minAge and name = :name")
    List<TestUser> findUsersByMinAgeAndNameAnno(@Param("minAge") Integer minAge, @Param("name") String name);

    @BoosterQuery("select count(*) from t_test_user where age >= :minAge")
    long countByMinAgeAnno(@Param("minAge") Integer minAge);

    @Modifying
    @Transactional
    @BoosterQuery("update t_test_user set email = :email where name = :name")
    int updateEmailByNameAnno(@Param("name") String name, @Param("email") String email);

    @BoosterQuery("select user_name, created_at, name, email from t_test_user where age = :age")
    Page<UserDTO> findUserDTOWithUnderscoreFields(@Param("age") Integer age, Pageable pageable);

    @Query("select u from TestUser u where u.age = :age")
    Page<TestUser> findByAgeStandardQuery(@Param("age") Integer age, Pageable pageable);

    @Query("select new com.chaosguide.jpa.booster.dto.UserDTO(u.name, u.email, u.userName, u.createdAt) from TestUser u where u.age = :age")
    Page<UserDTO> findUserDTOByAgeStandardQuery(@Param("age") Integer age, Pageable pageable);
}
