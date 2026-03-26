package com.govinc.governance;

import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GovernanceProjectService {

    @Autowired
    private GovernanceProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    public List<GovernanceProject> findAll() {
        return projectRepository.findAll();
    }

    public Optional<GovernanceProject> findById(Long id) {
        return projectRepository.findById(id);
    }

    public GovernanceProject createProject(String name, String description, Long ownerId, User createdBy) {
        GovernanceProject project = new GovernanceProject();
        project.setName(name);
        project.setDescription(description);
        project.setCreatedBy(createdBy);

        if (ownerId != null) {
            userRepository.findById(ownerId).ifPresent(project::setOwner);
        }

        return projectRepository.save(project);
    }

    public GovernanceProject save(GovernanceProject project) {
        return projectRepository.save(project);
    }

    public void delete(Long id) {
        projectRepository.deleteById(id);
    }
}
