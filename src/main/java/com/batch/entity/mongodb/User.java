package com.batch.entity.mongodb;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;


@Data
@Document(collection = "users")
// @JsonInclude use for exclude properties having null/empty
// Can learn more in https://www.youtube.com/watch?v=vl6DstgPoW8&ab_channel=DailyCodeBuffer
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    @Id
    private String id;

    // Before use unique just config in application.properties
    @Indexed(unique = true)
    private String name;

    private int age;

    @Indexed(unique = true)
    private String like;
}
