package com.batch.repository;

import com.batch.entity.Mo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MoRepository extends JpaRepository<Mo,Long> {
}
