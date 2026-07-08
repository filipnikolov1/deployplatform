package dev.filipnikolov.vector.connect.workflow;

import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;

import java.util.List;

public interface WorkflowWiringService {

    WiringResult wire(GitHubRepo repo, ConnectRequest req, List<ModuleJob> jobs);
}
