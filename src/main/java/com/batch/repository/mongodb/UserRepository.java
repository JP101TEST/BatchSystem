package com.batch.repository.mongodb;

import com.batch.entity.mongodb.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;


public interface UserRepository  extends MongoRepository<User,String> {
    List<User> findByName(String name);
}
