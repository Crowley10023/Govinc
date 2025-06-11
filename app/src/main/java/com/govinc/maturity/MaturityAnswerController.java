package com.govinc.maturity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/maturityanswer")
public class MaturityAnswerController {
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;

    @GetMapping("/list")
    public String listAnswers(Model model) {
        model.addAttribute("answers", maturityAnswerRepository.findAll());
        return "maturityanswer-list";
    }

    @GetMapping("/edit")
    public String editAnswer(@RequestParam(required = false) Long id, Model model) {
        MaturityAnswer answer = (id != null) ? maturityAnswerRepository.findById(id).orElse(new MaturityAnswer()) : new MaturityAnswer();
        model.addAttribute("maturityAnswer", answer);
        return "edit-maturityanswer";
    }

    @PostMapping("/edit")
    public String saveAnswer(@ModelAttribute MaturityAnswer maturityAnswer) {
        maturityAnswerRepository.save(maturityAnswer);
        return "redirect:/maturityanswer/list";
    }

    @PostMapping("/delete")
    public String deleteAnswer(@RequestParam Long id) {
        maturityAnswerRepository.deleteById(id);
        return "redirect:/maturityanswer/list";
    }
    
    @GetMapping("/create")
    public String createAnswer(Model model) {
        model.addAttribute("maturityAnswer", new MaturityAnswer());
        return "edit-maturityanswer";
    }
}
