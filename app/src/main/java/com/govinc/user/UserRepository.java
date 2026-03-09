package com.govinc.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.leadsOrgUnits WHERE u.name = :name")
    Optional<User> findByName(@Param("name") String name);
    
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.leadsOrgUnits WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);
    
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.leadsOrgUnits")
    List<User> findAll();
}