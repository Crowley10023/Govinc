package com.govinc.user;

import jakarta.persistence.*;

@Entity
@Table(name = "external_interviewees")
public class ExternalInterviewee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    public ExternalInterviewee() {}

    public ExternalInterviewee(String email) {
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
}