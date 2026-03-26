package com.govinc.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SecurityControlChangeTrackingRepository extends JpaRepository<SecurityControlChangeTracking, Long> {
    List<SecurityControlChangeTracking> findByGovernanceProjectIdOrderByChangedAtDesc(Long projectId);
    List<SecurityControlChangeTracking> findBySecurityControlIdOrderByChangedAtDesc(Long controlId);
}
