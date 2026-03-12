package com.chaosguide.jpa.booster.integration.mysql;

import com.chaosguide.jpa.booster.BoosterQueryRepositoryFactoryBean;
import com.chaosguide.jpa.booster.dto.UserDTO;
import com.chaosguide.jpa.booster.entity.TestUser;
import com.chaosguide.jpa.booster.repo.TestSmartUserRepository;
import com.chaosguide.jpa.booster.repo.TestUserRepository;
import com.chaosguide.jpa.booster.repository.BoosterNativeJpaRepository;
import com.chaosguide.jpa.booster.repository.BoosterQueryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MysqlIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public class MysqlIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> "true");
    }

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestSmartUserRepository smartUserRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        LocalDateTime now = LocalDateTime.now();
        userRepository.save(new TestUser("Alice", 25, "alice@example.com", "alice_un", now));
        userRepository.save(new TestUser("Bob", 30, "bob@example.com", "bob_un", now.minusDays(1)));
        userRepository.save(new TestUser("Charlie", 35, "charlie@example.com", "charlie_un", now.minusDays(2)));
    }

    @Test
    void testNativeQueryList() {
        String sql = "select * from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<TestUser> users = userRepository.nativeQueryList(sql, params);
        assertEquals(2, users.size());
        assertTrue(users.stream().anyMatch(u -> u.getName().equals("Bob")));
        assertTrue(users.stream().anyMatch(u -> u.getName().equals("Charlie")));
    }

    @Test
    void testNativeQueryListWithInheritedParamObject() {
        String sql = "select * from t_test_user where age > :age";
        ChildParam param = new ChildParam();
        param.age = 28;
        param.name = "ignored";

        List<TestUser> users = userRepository.nativeQueryList(sql, param);
        assertEquals(2, users.size());
    }

    @Test
    void testNativeQueryPage() {
        String sql = "select * from t_test_user order by age desc";
        Pageable pageable = PageRequest.of(0, 2);

        Page<TestUser> page = userRepository.nativeQuery(sql, pageable);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Charlie", page.getContent().getFirst().getName());
        assertEquals("Bob", page.getContent().get(1).getName());
    }

    @Test
    void testNativeQueryPageWithSortOverridesSqlOrderBy() {
        String sql = "select * from t_test_user order by age desc";
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.asc("age")));

        Page<TestUser> page = userRepository.nativeQuery(sql, pageable);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Alice", page.getContent().getFirst().getName());
        assertEquals("Bob", page.getContent().get(1).getName());
    }

    @Test
    void testNativeQueryOne() {
        String sql = "select * from t_test_user where name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");

        TestUser user = userRepository.nativeQueryOne(sql, params);
        assertNotNull(user);
        assertEquals("alice@example.com", user.getEmail());
    }

    @Test
    void testNativeQueryOneWithObjectParam() {
        String sql = "select * from t_test_user where name = :name";
        TestUser param = new TestUser();
        param.setName("Bob");

        TestUser user = userRepository.nativeQueryOne(sql, param);
        assertNotNull(user);
        assertEquals("bob@example.com", user.getEmail());
    }

    @Test
    void testNativeQueryOneNoParam() {
        String sql = "select * from t_test_user where name = 'Charlie'";
        TestUser user = userRepository.nativeQueryOne(sql);
        assertNotNull(user);
        assertEquals("charlie@example.com", user.getEmail());
    }

    @Test
    void testNativeCount() {
        String sql = "select * from t_test_user where email like :email";
        Map<String, Object> params = new HashMap<>();
        params.put("email", "%@example.com");

        long count = userRepository.nativeCount(sql, params);
        assertEquals(3, count);
    }

    @Test
    void testNativeCountWithObjectParam() {
        String sql = "select * from t_test_user where age > :age";
        TestUser param = new TestUser();
        param.setAge(28);

        long count = userRepository.nativeCount(sql, param);
        assertEquals(2, count);
    }

    @Test
    void testNativeCountNoParam() {
        String sql = "select * from t_test_user";
        long count = userRepository.nativeCount(sql);
        assertEquals(3, count);
    }

    @Test
    void testNativeExecute() {
        String sql = "update t_test_user set age = age + 1 where name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");

        int rows = userRepository.nativeExecute(sql, params);
        assertEquals(1, rows);

        TestUser alice = userRepository.findAll().stream()
                .filter(u -> u.getName().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertEquals(26, alice.getAge());
    }

    @Test
    void testNativeExecuteWithObjectParam() {
        String sql = "update t_test_user set age = age + 2 where name = :name";
        TestUser param = new TestUser();
        param.setName("Bob");

        int rows = userRepository.nativeExecute(sql, param);
        assertEquals(1, rows);

        TestUser bob = userRepository.findAll().stream()
                .filter(u -> u.getName().equals("Bob"))
                .findFirst()
                .orElseThrow();
        assertEquals(32, bob.getAge());
    }

    @Test
    void testNativeExecuteNoParam() {
        String sql = "update t_test_user set age = age + 1";
        int rows = userRepository.nativeExecute(sql);
        assertEquals(3, rows);
    }

    @Test
    void testNativeQueryListWithDto() {
        String sql = "select name, email from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<UserDto> dtos = userRepository.nativeQueryList(sql, params, UserDto.class);
        assertEquals(2, dtos.size());
        assertTrue(dtos.stream().anyMatch(u -> "Bob".equals(u.getName()) && "bob@example.com".equals(u.getEmail())));
        assertTrue(dtos.stream().anyMatch(u -> "Charlie".equals(u.getName()) && "charlie@example.com".equals(u.getEmail())));
    }

    @Test
    void testNativeQueryOneWithDto() {
        String sql = "select name, email from t_test_user where name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");

        UserDto dto = userRepository.nativeQueryOne(sql, params, UserDto.class);
        assertNotNull(dto);
        assertEquals("Alice", dto.getName());
        assertEquals("alice@example.com", dto.getEmail());
    }

    @Test
    void testBoosterQueryListWithNullParamRewrite() {
        String sql = "select * from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", null);

        List<TestUser> users = smartUserRepository.boosterQueryList(sql, params);
        assertEquals(3, users.size());
    }

    @Test
    void testBoosterQueryListWithDto() {
        String sql = "select name, email from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<UserDto> dtos = smartUserRepository.boosterQueryList(sql, params, UserDto.class);
        assertEquals(2, dtos.size());
        assertTrue(dtos.stream().anyMatch(u -> "Bob".equals(u.getName())));
    }

    @Test
    void testBoosterQueryListWithRecord() {
        String sql = "select name, email from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<UserRecord> records = smartUserRepository.boosterQueryList(sql, params, UserRecord.class);
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> "Bob".equals(r.name())));
    }

    @Test
    void testBoosterQueryListWithMap() {
        String sql = "select name, email from t_test_user where name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");

        List<Map> maps = smartUserRepository.boosterQueryList(sql, params, Map.class);
        assertEquals(1, maps.size());
        Map first = maps.getFirst();
        assertEquals("Alice", first.get("name"));
        assertEquals("alice@example.com", first.get("email"));
    }

    @Test
    void testBoosterQueryOneWithInteger() {
        String sql = "select count(*) from t_test_user";
        Long count = smartUserRepository.boosterQueryOne(sql, null, Long.class);
        assertEquals(3L, count);
    }

    @Test
    void testBoosterPage() {
        String sql = "select * from t_test_user order by age";
        Pageable pageable = PageRequest.of(0, 2);

        Page<TestUser> page = smartUserRepository.boosterQuery(sql, pageable);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Alice", page.getContent().getFirst().getName());
    }

    @Test
    void testBoosterPageWithSortOverridesSqlOrderBy() {
        String sql = "select * from t_test_user order by age desc";
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.asc("age")));

        Page<TestUser> page = smartUserRepository.boosterQuery(sql, pageable);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Alice", page.getContent().getFirst().getName());
        assertEquals("Bob", page.getContent().get(1).getName());
    }

    @Test
    void testBoosterPageWithDto() {
        String sql = "select name, email from t_test_user order by age";
        Pageable pageable = PageRequest.of(0, 2);

        Page<UserDto> page = smartUserRepository.boosterQuery(sql, null, pageable, UserDto.class);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("Alice", page.getContent().getFirst().getName());
    }

    @Test
    void testBoosterCount() {
        String sql = "select * from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 20);
        long count = smartUserRepository.boosterCount(sql, params);
        assertEquals(3, count);
    }

    @Test
    void testBoosterExecute() {
        String sql = "update t_test_user set email = :email where name = :name";
        Map<String, Object> params = new HashMap<>();
        params.put("email", "new_alice@example.com");
        params.put("name", "Alice");

        int updated = smartUserRepository.boosterExecute(sql, params);
        assertEquals(1, updated);

        TestUser alice = userRepository.findAll().stream()
                .filter(u -> u.getName().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertEquals("new_alice@example.com", alice.getEmail());
    }

    @Test
    void testBoosterQueryAnnotationPageDto() {
        Page<UserDTO> page = smartUserRepository.findUserDTOByAgeAnno(30, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Bob", page.getContent().getFirst().getName());
        assertEquals("bob@example.com", page.getContent().getFirst().getEmail());
    }

    @Test
    void testBoosterQueryAnnotationPageDtoNoAs() {
        Page<UserDTO> page = smartUserRepository.findUserDTOByAgeAnnoNoAs(30, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Bob", page.getContent().getFirst().getName());
        assertEquals("bob@example.com", page.getContent().getFirst().getEmail());
    }

    @Test
    void testBoosterQueryAnnotationListRewrite() {
        List<TestUser> users = smartUserRepository.findUsersByMinAgeAndNameAnno(20, null);
        assertEquals(3, users.size());
    }

    @Test
    void testBoosterQueryAnnotationCount() {
        long count = smartUserRepository.countByMinAgeAnno(30);
        assertEquals(2, count);
    }

    @Test
    void testBoosterQueryUnderscoreFieldsMapping() {
        Page<UserDTO> page = smartUserRepository.findUserDTOWithUnderscoreFields(25, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        UserDTO dto = page.getContent().getFirst();
        assertEquals("Alice", dto.getName());
        assertEquals("alice_un", dto.getUserName());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void testStandardQueryCompatibility() {
        Page<TestUser> page = smartUserRepository.findByAgeStandardQuery(30, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertEquals("Bob", page.getContent().getFirst().getName());

        Page<UserDTO> dtoPage = smartUserRepository.findUserDTOByAgeStandardQuery(30, PageRequest.of(0, 10));
        assertEquals(1, dtoPage.getTotalElements());
        assertEquals("bob_un", dtoPage.getContent().getFirst().getUserName());
    }

    @Test
    void testBoosterQueryAnnotationModify() {
        int updated = smartUserRepository.updateEmailByNameAnno("Alice", "updated@example.com");
        assertEquals(1, updated);
        TestUser alice = userRepository.findAll().stream()
                .filter(u -> u.getName().equals("Alice"))
                .findFirst()
                .orElseThrow();
        assertEquals("updated@example.com", alice.getEmail());
    }

    public static class UserDto {
        private String name;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public record UserRecord(String name, String email) {
    }

    static class BaseParam {
        Integer age;
    }

    static class ChildParam extends BaseParam {
        String name;
    }

    @SpringBootApplication(scanBasePackages = "com.chaosguide.jpa.booster")
    @EntityScan(basePackages = "com.chaosguide.jpa.booster.entity")
    @Import({NormalRepoConfig.class, SmartRepoConfig.class})
    static class TestConfig {
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.chaosguide.jpa.booster.repo",
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.jpa\\.booster\\.repo\\.TestUserRepository"
            ),
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.jpa\\.booster\\.repo\\.TestSmartUserRepository"
            ),
            repositoryBaseClass = BoosterNativeJpaRepository.class
    )
    static class NormalRepoConfig {
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.chaosguide.jpa.booster.repo",
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.jpa\\.booster\\.repo\\.TestSmartUserRepository"
            ),
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.jpa\\.booster\\.repo\\.TestUserRepository"
            ),
            repositoryFactoryBeanClass = BoosterQueryRepositoryFactoryBean.class,
            repositoryBaseClass = BoosterQueryJpaRepository.class
    )
    static class SmartRepoConfig {
    }
}
