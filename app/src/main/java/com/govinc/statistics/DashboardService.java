package com.govinc.statistics;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.assessment.AssessmentControlAnswerRepository;
import com.govinc.catalog.SecurityCatalogRepository;


@Service
public class DashboardService {
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    public DashboardResponse buildDashboard() {
        long assessmentsCount = assessmentRepository.count();

        // "Maturity answers" as the answers given in assessments (AssessmentControlAnswer with maturityAnswer)
        long maturityAnswers = assessmentControlAnswerRepository.findAll().stream()
            .filter(a -> !Boolean.TRUE.equals(a.getIsNotApplicable()) && a.getMaturityAnswer() != null)
            .count();

        // Distribution per catalog (uses countBySecurityCatalogId to avoid loading all assessments twice)
        List<DashboardResponse.CatalogDistribution> distribution = securityCatalogRepository.findAll().stream()
                .map(c -> new DashboardResponse.CatalogDistribution(c.getId(), c.getName(), assessmentRepository.countBySecurityCatalogId(c.getId())))
                .collect(Collectors.toList());

        // Recent assessments (latest 10)
        List<DashboardResponse.RecentAssessment> recent = assessmentRepository.findAll().stream()
                .sorted((a, b) -> b.getCreationDate().compareTo(a.getCreationDate()))
                .limit(10)
                .map(a -> new DashboardResponse.RecentAssessment(a.getId(), a.getName(), a.getCreationDate().format(DF), a.getSecurityCatalog() != null ? a.getSecurityCatalog().getName() : null, a.getStatus() != null ? a.getStatus().name() : null))
                .collect(Collectors.toList());

        // Monthly created assessments for last 12 months
        List<Assessment> allAssessments = assessmentRepository.findAll();
        YearMonth now = YearMonth.now();
        List<DashboardResponse.MonthStat> monthly = new ArrayList<>();

        // Build a map YearMonth -> count for efficiency
        Map<YearMonth, Long> grouped = allAssessments.stream()
                .filter(a -> a.getCreationDate() != null)
                .collect(Collectors.groupingBy(a -> YearMonth.from(a.getCreationDate()), Collectors.counting()));

        IntStream.iterate(11, i -> i - 1).limit(12).forEach(i -> {
            YearMonth ym = now.minusMonths(i);
            long cnt = grouped.getOrDefault(ym, 0L);
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + ym.getYear();
            monthly.add(new DashboardResponse.MonthStat(label, cnt));
        });

        return new DashboardResponse(assessmentsCount, maturityAnswers, distribution, recent, monthly);
    }
}
