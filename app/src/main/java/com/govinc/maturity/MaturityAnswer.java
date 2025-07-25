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

    /**
     * Rating as a percentage (0 to 100).
     */
    @Column(nullable = false)
    private int rating = 0;

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

    public int getRating() {
        return rating;
    }

    /** For compatibility: consider 'rating' as the score (0-100) for this maturity answer */
    public int getScore() {
        return this.rating;
    }

    public void setRating(int rating) {
        if (rating < 0) rating = 0;
        if (rating > 100) rating = 100;
        this.rating = rating;
    }

    public Set<MaturityModel> getMaturityModels() {
        return maturityModels;
    }
    public void setMaturityModels(Set<MaturityModel> maturityModels) {
        this.maturityModels = maturityModels;
    }
}
