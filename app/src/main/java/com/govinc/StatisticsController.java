package com.govinc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswerRepository;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.catalog.SecurityControlRepository;

@Controller
public class StatisticsController {
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private SecurityControlRepository securityControlRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;

    @GetMapping("/statistics")
    public String statisticsPage(Model model) {
        // Assessments per year
        List<Assessment> allAssessments = assessmentRepository.findAll();
        Map<Integer, Long> yearToCount = allAssessments.stream()
                .collect(Collectors.groupingBy(a -> a.getDate().getYear() + 1900, Collectors.counting()));
        List<AssessmentYearStat> assessmentsPerYear = yearToCount.entrySet().stream()
                .map(e -> new AssessmentYearStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(AssessmentYearStat::getYear))
                .collect(Collectors.toList());
        
        // Total security controls
        long totalControls = securityControlRepository.count();
        // Total controls answered
        long totalAnswered = assessmentControlAnswerRepository.count();

        model.addAttribute("assessmentsPerYear", assessmentsPerYear);
        model.addAttribute("totalControls", totalControls);
        model.addAttribute("totalAnswered", totalAnswered);
        return "statistics";
    }

    public static class AssessmentYearStat {
        private int year;
        private long count;

        public AssessmentYearStat(int year, long count) {
            this.year = year;
            this.count = count;
        }
        public int getYear() { return year; }
        public long getCount() { return count; }
    }
}
