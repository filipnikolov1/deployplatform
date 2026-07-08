package dev.filipnikolov.vector.connect.deploy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingBuildRepository extends JpaRepository<PendingBuild, String> {
}
