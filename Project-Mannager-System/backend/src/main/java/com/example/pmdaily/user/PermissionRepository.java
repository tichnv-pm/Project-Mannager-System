package com.example.pmdaily.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, java.util.UUID> {

    Optional<Permission> findByCode(String code);

    java.util.List<Permission> findAllByCodeIn(java.util.Collection<String> codes);
}
