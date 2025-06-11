package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/assessment-urls")
public class AssessmentUrlsController {
    @Autowired
    private AssessmentUrlsRepository repository;

    @GetMapping
    public List<AssessmentUrls> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssessmentUrls> getById(@PathVariable Long id) {
        Optional<AssessmentUrls> url = repository.findById(id);
        return url.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public AssessmentUrls create(@RequestBody AssessmentUrls urls) {
        return repository.save(urls);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssessmentUrls> update(@PathVariable Long id, @RequestBody AssessmentUrls updated) {
        Optional<AssessmentUrls> found = repository.findById(id);
        if (!found.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        AssessmentUrls existing = found.get();
        existing.setUrl(updated.getUrl());
        existing.setResponsiblePerson(updated.getResponsiblePerson());
        existing.setLifetime(updated.getLifetime());
        return ResponseEntity.ok(repository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<AssessmentUrls> found = repository.findById(id);
        if (!found.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
