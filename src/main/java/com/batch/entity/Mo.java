package com.batch.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "MO")
public class Mo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mo_id")
    private long moId;

    @Column(nullable = false,unique = true)
    private String username;
}
