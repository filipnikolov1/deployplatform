package dev.filipnikolov.vector.project.service;

import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectCollapseService {

    private final ProjectServiceRepository projectServiceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEnvVarRepository projectEnvVarRepository;

    @Transactional
    public void collapse(String appName) {
        ProjectService deleted = projectServiceRepository.findByAppName(appName).orElse(null);
        if (deleted == null) {
            return;
        }

        Long projectId = deleted.getProjectId();
        projectServiceRepository.delete(deleted);

        List<ProjectService> remaining = projectServiceRepository.findByProjectId(projectId);
        if (remaining.size() == 1) {
            projectServiceRepository.delete(remaining.get(0));
            deleteProject(projectId);
        } else if (remaining.isEmpty()) {
            deleteProject(projectId);
        }
    }

    private void deleteProject(Long projectId) {
        projectEnvVarRepository.findByProjectId(projectId).forEach(projectEnvVarRepository::delete);
        projectRepository.findById(projectId).ifPresent(projectRepository::delete);
    }
}
