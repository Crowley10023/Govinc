package com.govinc.assessment;

public class ControlAnswerWithSource {
    private Long answerId;
    private String answerLabel;
    private String source; // "local" or "orgService: <name>"

    public ControlAnswerWithSource(Long answerId, String answerLabel, String source) {
        this.answerId = answerId;
        this.answerLabel = answerLabel;
        this.source = source;
    }

    public Long getAnswerId() {
        return answerId;
    }
    public void setAnswerId(Long answerId) {
        this.answerId = answerId;
    }
    public String getAnswerLabel() {
        return answerLabel;
    }
    public void setAnswerLabel(String answerLabel) {
        this.answerLabel = answerLabel;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
}
