package dev.filipnikolov.vector.project.service;

import dev.filipnikolov.vector.project.model.Project;
import dev.filipnikolov.vector.project.model.ProjectService;
import dev.filipnikolov.vector.project.repository.ProjectEnvVarRepository;
import dev.filipnikolov.vector.project.repository.ProjectRepository;
import dev.filipnikolov.vector.project.repository.ProjectServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectCollapseServiceTest {

    private ProjectServiceRepository projectServiceRepository;
    private ProjectRepository projectRepository;
    private ProjectEnvVarRepository projectEnvVarRepository;
    private ProjectCollapseService service;

    @BeforeEach
    void setUp() {
        projectServiceRepository = mock(ProjectServiceRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectEnvVarRepository = mock(ProjectEnvVarRepository.class);
        service = new ProjectCollapseService(projectServiceRepository, projectRepository, projectEnvVarRepository);
    }

    private ProjectService svc(Long id, Long projectId, String appName) {
        ProjectService s = new ProjectService();
        s.setId(id);
        s.setProjectId(projectId);
        s.setAppName(appName);
        return s;
    }

    @Test
    void deletingOneOfThreeRemovesRowButKeepsProject() {
        ProjectService deleted = svc(1L, 10L, "shop-worker");
        ProjectService remainingA = svc(2L, 10L, "shop-web");
        ProjectService remainingB = svc(3L, 10L, "shop-api");
        when(projectServiceRepository.findByAppName("shop-worker")).thenReturn(Optional.of(deleted));
        when(projectServiceRepository.findByProjectId(10L)).thenReturn(List.of(remainingA, remainingB));

        service.collapse("shop-worker");

        verify(projectServiceRepository).delete(deleted);
        verify(projectRepository, never()).delete(any());
        verify(projectServiceRepository, never()).delete(remainingA);
        verify(projectServiceRepository, never()).delete(remainingB);
    }

    @Test
    void deletingOneOfTwoCollapsesProjectAndBothRows() {
        ProjectService deleted = svc(1L, 10L, "shop-worker");
        ProjectService survivor = svc(2L, 10L, "shop-web");
        when(projectServiceRepository.findByAppName("shop-worker")).thenReturn(Optional.of(deleted));
        when(projectServiceRepository.findByProjectId(10L)).thenReturn(List.of(survivor));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(10L)));
        when(projectEnvVarRepository.findByProjectId(10L)).thenReturn(List.of());

        service.collapse("shop-worker");

        verify(projectServiceRepository).delete(deleted);
        verify(projectServiceRepository).delete(survivor);
        verify(projectRepository).delete(any(Project.class));
    }

    @Test
    void appWithNoProjectServiceRowIsNoOp() {
        when(projectServiceRepository.findByAppName("standalone")).thenReturn(Optional.empty());

        service.collapse("standalone");

        verify(projectServiceRepository, never()).delete(any(ProjectService.class));
        verify(projectRepository, never()).delete(any());
    }

    private Project project(Long id) {
        Project p = new Project();
        p.setId(id);
        return p;
    }
}
