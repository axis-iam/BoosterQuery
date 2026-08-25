package com.chaosguide.boosterquery.integration.postgres;

import com.chaosguide.boosterquery.BoosterQueryRepositoryFactoryBean;
import com.chaosguide.boosterquery.dto.UserDTO;
import com.chaosguide.boosterquery.entity.TestUser;
import com.chaosguide.boosterquery.repo.TestSmartUserRepository;
import com.chaosguide.boosterquery.repo.TestUserRepository;
import com.chaosguide.boosterquery.repository.BoosterNativeJpaRepository;
import com.chaosguide.boosterquery.repository.BoosterQueryJpaRepository;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PgsqlIntegrationTest.TestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public class PgsqlIntegrationTest {

    private static final UUID APP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_APP_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private TestUserRepository userRepository;

    @Autowired
    private TestSmartUserRepository smartUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        LocalDateTime now = LocalDateTime.now();
        userRepository.save(new TestUser("Alice", 25, "alice@example.com", "alice_un", now));
        userRepository.save(new TestUser("Bob", 30, "bob@example.com", "bob_un", now.minusDays(1)));
        userRepository.save(new TestUser("Charlie", 35, "charlie@example.com", "charlie_un", now.minusDays(2)));
        resetGlobalIdentityFixture(now);
    }

    @Test
    void testNativeQueryList() {
        String sql = "select * from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<TestUser> users = userRepository.nativeQueryList(sql, params);
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
    }

    @Test
    void testNativeCountWithComplexSql() {
        String sql = "select * from t_test_user where email like :email order by id desc";
        Map<String, Object> params = new HashMap<>();
        params.put("email", "%@example.com");

        long count = userRepository.nativeCount(sql, params);
        assertEquals(3, count);
    }

    @Test
    void testNativeQueryOneWithObjectParam() {
        String sql = "select * from t_test_user where name = :name";
        TestUser param = new TestUser();
        param.setName("Alice");

        TestUser user = userRepository.nativeQueryOne(sql, param);
        assertNotNull(user);
        assertEquals("alice@example.com", user.getEmail());
    }

    @Test
    void testNativeExecuteWithObjectParam() {
        String sql = "update t_test_user set age = age + 1 where name = :name";
        TestUser param = new TestUser();
        param.setName("Charlie");

        int rows = userRepository.nativeExecute(sql, param);
        assertEquals(1, rows);

        TestUser charlie = userRepository.findAll().stream()
                .filter(u -> u.getName().equals("Charlie"))
                .findFirst()
                .orElseThrow();
        assertEquals(36, charlie.getAge());
    }

    @Test
    void testNativeQueryListNoParam() {
        String sql = "select * from t_test_user order by id";
        List<TestUser> list = userRepository.nativeQueryList(sql);
        assertEquals(3, list.size());
    }

    @Test
    void testNativeQueryListWithDto() {
        String sql = "select name, email from t_test_user where age > :age";
        Map<String, Object> params = new HashMap<>();
        params.put("age", 28);

        List<UserDto> dtos = userRepository.nativeQueryList(sql, params, UserDto.class);
        assertEquals(2, dtos.size());
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
    void testBoosterQueryAnnotationPageLimitKeepsTotalCount() {
        Page<TestUser> page = smartUserRepository.findUsersWithPageLimit(PageRequest.of(0, 2));

        assertEquals(2, page.getContent().size());
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void testBoosterQueryAnnotationRejectsPageSizeAboveLimit() {
        InvalidDataAccessApiUsageException exception = assertThrows(
                InvalidDataAccessApiUsageException.class,
                () -> smartUserRepository.findUsersWithPageLimit(PageRequest.of(0, 3)));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("Page size 3"));
        assertTrue(cause.getMessage().contains("maximum of 2"));
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
    void testBoosterQueryAnnotationNumericScalarResults() {
        Integer age = smartUserRepository.findAgeByNameAnno("Alice");
        Long id = smartUserRepository.findIdByNameAnno("Alice");

        assertEquals(25, age);
        assertNotNull(id);
        assertTrue(id > 0);
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
    void testBoosterQueryAnnotationListWithLimitParameter() {
        List<UserDTO> rows = smartUserRepository.findRecentUserDTOs(2);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.getFirst().getName());
        assertEquals("Bob", rows.get(1).getName());
    }

    @Test
    void testPostgresEnumFilterWithCastNamedParam() {
        List<Map<String, Object>> rows = smartUserRepository.findIdentityEmailsByCastStatus(APP_ID, "ACTIVE");

        assertIdentityEmails(rows, "active@example.com");
    }

    @Test
    void testPostgresEnumFilterWithShorthandParamCast() {
        List<Map<String, Object>> rows = smartUserRepository.findIdentityEmailsByShorthandCastStatus(APP_ID, "ACTIVE");

        assertIdentityEmails(rows, "active@example.com");
    }

    @Test
    void testNullConditionRewriteWhenConditionContainsCast() {
        List<Map<String, Object>> rows = smartUserRepository.findIdentityEmailsByOptionalCastStatus(APP_ID, null);

        assertIdentityEmails(rows, "active@example.com", "disabled@example.com");
    }

    @Test
    void testNullConditionRewriteRemovesGroupedOptionalSearchWithoutDroppingScope() {
        List<Map<String, Object>> rows = smartUserRepository.findIdentityEmailsByOptionalGroupedSearch(APP_ID, null);

        assertIdentityEmails(rows, "active@example.com", "disabled@example.com");
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

    private void resetGlobalIdentityFixture(LocalDateTime now) {
        jdbcTemplate.execute("drop table if exists global_identities");
        jdbcTemplate.execute("drop type if exists global_identity_status");
        jdbcTemplate.execute("create type global_identity_status as enum ('ACTIVE', 'DISABLED')");
        jdbcTemplate.execute("""
                create table global_identities (
                    id bigserial primary key,
                    primary_email text not null,
                    status global_identity_status not null,
                    app_id uuid not null,
                    created_at timestamp not null
                )
                """);
        jdbcTemplate.update("""
                insert into global_identities(primary_email, status, app_id, created_at)
                values (?, CAST(? AS global_identity_status), ?, ?)
                """, "active@example.com", "ACTIVE", APP_ID, now);
        jdbcTemplate.update("""
                insert into global_identities(primary_email, status, app_id, created_at)
                values (?, CAST(? AS global_identity_status), ?, ?)
                """, "disabled@example.com", "DISABLED", APP_ID, now.minusHours(1));
        jdbcTemplate.update("""
                insert into global_identities(primary_email, status, app_id, created_at)
                values (?, CAST(? AS global_identity_status), ?, ?)
                """, "other-app@example.com", "ACTIVE", OTHER_APP_ID, now.minusHours(2));
    }

    private static void assertIdentityEmails(List<Map<String, Object>> rows, String... emails) {
        assertEquals(emails.length, rows.size());
        for (int i = 0; i < emails.length; i++) {
            assertEquals(emails[i], rows.get(i).get("primary_email"));
        }
    }

    @SpringBootApplication(scanBasePackages = "com.chaosguide.boosterquery")
    @EntityScan(basePackages = "com.chaosguide.boosterquery.entity")
    @Import({NormalRepoConfig.class, SmartRepoConfig.class})
    static class TestConfig {
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.chaosguide.boosterquery.repo",
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.boosterquery\\.repo\\.TestUserRepository"
            ),
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.boosterquery\\.repo\\.TestSmartUserRepository"
            ),
            repositoryBaseClass = BoosterNativeJpaRepository.class
    )
    static class NormalRepoConfig {
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.chaosguide.boosterquery.repo",
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.boosterquery\\.repo\\.TestSmartUserRepository"
            ),
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.chaosguide\\.boosterquery\\.repo\\.TestUserRepository"
            ),
            repositoryFactoryBeanClass = BoosterQueryRepositoryFactoryBean.class,
            repositoryBaseClass = BoosterQueryJpaRepository.class
    )
    static class SmartRepoConfig {
    }
}
