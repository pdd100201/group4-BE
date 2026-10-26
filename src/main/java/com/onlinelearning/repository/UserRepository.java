package com.onlinelearning.repository;

import com.onlinelearning.entity.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where u.enabled = true and u.blocked = false and u.id <> :me and lower(u.fullName) like lower(concat('%', :query, '%')) escape '!'")
    Page<User> chatDirectory(@Param("me") Long me, @Param("query") String query, Pageable pageable);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);

    @Query("""
            select distinct u from User u left join u.roles role
            where (:query is null or lower(u.email) like lower(concat('%', :query, '%'))
                or lower(u.fullName) like lower(concat('%', :query, '%'))
                or lower(coalesce(u.phone, '')) like lower(concat('%', :query, '%')))
              and (:roleCode is null or role.code = :roleCode)
              and (:enabled is null or u.enabled = :enabled)
              and (:blocked is null or u.blocked = :blocked)
            """)
    Page<User> search(@Param("query") String query, @Param("roleCode") String roleCode,
            @Param("enabled") Boolean enabled, @Param("blocked") Boolean blocked, Pageable pageable);

    @Query("select count(distinct u.id) from User u join u.roles role where role.code = 'STUDENT' and u.enabled = true and u.blocked = false")
    long countActiveStudents();
}
