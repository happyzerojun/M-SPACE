package com.mspace.policy.repository;

import com.mspace.policy.domain.OperationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationPolicyRepository extends JpaRepository<OperationPolicy, Short> {
}
