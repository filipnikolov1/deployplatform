package dev.filipnikolov.vector.envvar.repository;

import dev.filipnikolov.vector.envvar.model.EnvVar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvVarRepository extends JpaRepository<EnvVar, Long> {

    List<EnvVar> findByAppName(String appName);

    Optional<EnvVar> findByAppNameAndVarKey(String appName, String varKey);

    void deleteByAppNameAndVarKey(String appName, String varKey);

    void deleteByAppName(String appName);
}
