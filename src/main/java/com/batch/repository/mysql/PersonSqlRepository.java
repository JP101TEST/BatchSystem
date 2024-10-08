package com.batch.repository.mysql;

import com.batch.entity.mysql.PersonSql;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonSqlRepository extends JpaRepository<PersonSql,Long> {
    Optional<PersonSql> findByUsername(String username);
}
