package dev.filipnikolov.vector.connect.workflow.impl;

import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.workflow.ModuleJob;
import dev.filipnikolov.vector.connect.workflow.WiringResult;
import dev.filipnikolov.vector.connect.workflow.WorkflowWiringService;
import dev.filipnikolov.vector.githubapp.model.GitHubRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Placeholder until Task 14 implements the real workflow-wiring logic (marker detection,
 * never-overwrite lane, putFile/dispatch). Returns a snippet-only result so Task 12's
 * connect flow can complete end-to-end ahead of Task 14.
 */
@Service
public class WorkflowWiringServiceStub implements WorkflowWiringService {

    @Override
    public WiringResult wire(GitHubRepo repo, ConnectRequest req, List<ModuleJob> jobs) {
        return new WiringResult(req.workflowMode(), null, null);
    }
}
