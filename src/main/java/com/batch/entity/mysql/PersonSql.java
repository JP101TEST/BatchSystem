package com.batch.entity.mysql;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "PERSON")
public class PersonSql {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "person_id")
    private long id;

    @Column(nullable = false,unique = true)
    private String name;

    @Column(nullable = false)
    private String age;

    @Column(nullable = false)
    private String city;
}
