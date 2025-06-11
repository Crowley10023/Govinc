package com.govinc.maturity;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/maturitymodel")
public class MaturityModelController {
    @Autowired
    private MaturityModelRepository maturityModelRepository;

    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;

    @GetMapping("/list")
    public String listMaturityModels(Model model) {
        List<MaturityModel> models = maturityModelRepository.findAll();
        model.addAttribute("maturityModels", models);
        return "maturitymodel-list";
    }

    // CREATE: Show empty form for new MaturityModel
    @GetMapping("/edit")
    public String createMaturityModel(Model model) {
        MaturityModel maturityModel = new MaturityModel();
        List<MaturityAnswer> allAnswers = maturityAnswerRepository.findAll();
        model.addAttribute("maturityModel", maturityModel);
        model.addAttribute("allAnswers", allAnswers);
        return "maturitymodel-edit";
    }

    @GetMapping("/edit/{id}")
    public String editMaturityModel(@PathVariable Long id, Model model) {
        MaturityModel maturityModel = maturityModelRepository.findById(id).orElse(new MaturityModel());
        List<MaturityAnswer> allAnswers = maturityAnswerRepository.findAll();
        model.addAttribute("maturityModel", maturityModel);
        model.addAttribute("allAnswers", allAnswers);
        return "maturitymodel-edit";
    }

    @PostMapping("/save")
    public String saveMaturityModel(@ModelAttribute MaturityModel maturityModel, @RequestParam(value = "maturityAnswers", required = false) Set<Long> answerIds) {
        // Attach selected answers (if any)
        if (answerIds != null) {
            List<MaturityAnswer> selectedAnswers = maturityAnswerRepository.findAllById(answerIds);
            maturityModel.setMaturityAnswers(Set.copyOf(selectedAnswers));
        } else {
            maturityModel.setMaturityAnswers(Set.of());
        }
        maturityModelRepository.save(maturityModel);
        return "redirect:/maturitymodel/list";
    }

    @GetMapping("/delete/{id}")
    public String deleteMaturityModel(@PathVariable Long id) {
        maturityModelRepository.deleteById(id);
        return "redirect:/maturitymodel/list";
    }
}
