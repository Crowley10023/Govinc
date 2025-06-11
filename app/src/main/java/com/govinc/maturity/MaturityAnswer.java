package com.govinc.maturity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

@Entity
@Table(name = "maturity_answers")
public class MaturityAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String answer;
    private String description;

    @ManyToMany(mappedBy = "maturityAnswers")
    private Set<MaturityModel> maturityModels = new HashSet<>();

    // Constructors
    public MaturityAnswer() {}

    public MaturityAnswer(String answer, String description) {
        this.answer = answer;
        this.description = description;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<MaturityModel> getMaturityModels() {
        return maturityModels;
    }
    public void setMaturityModels(Set<MaturityModel> maturityModels) {
        this.maturityModels = maturityModels;
    }
}
