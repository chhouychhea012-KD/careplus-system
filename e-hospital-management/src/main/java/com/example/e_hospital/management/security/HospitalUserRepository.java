package com.example.e_hospital.management.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface HospitalUserRepository extends JpaRepository<HospitalUser, Long> {
    Optional<HospitalUser> findByUsername(String username);
    long countByActiveTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from HospitalUser u where u.username = :username")
    Optional<HospitalUser> lockByUsername(@Param("username") String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from HospitalUser u where u.id = :id")
    Optional<HospitalUser> lockById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from HospitalUser u where u.role = com.example.e_hospital.management.security.Role.ADMIN and u.active = true order by u.id")
    List<HospitalUser> lockActiveAdministrators();
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    @Query("select u from HospitalUser u where lower(u.username) like lower(concat('%', :q, '%')) or lower(u.displayName) like lower(concat('%', :q, '%'))")
    Page<HospitalUser> search(@Param("q") String query, Pageable pageable);
}
