package com.batch.repository.mysql;

import com.batch.entity.mysql.Mo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MoRepository extends JpaRepository<Mo,Long> {
}
