package com.batch.repository.mongodb;

import com.batch.entity.mongodb.PersonNosql;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;


public interface PersonNosqlRepository extends MongoRepository<PersonNosql, String> {
    Optional<PersonNosql> findByName(String name);
}
