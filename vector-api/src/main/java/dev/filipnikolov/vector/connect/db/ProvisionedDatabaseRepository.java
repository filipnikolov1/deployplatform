package dev.filipnikolov.vector.connect.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProvisionedDatabaseRepository extends JpaRepository<ProvisionedDatabase, Long> {

    Optional<ProvisionedDatabase> findByAppName(String appName);

    Optional<ProvisionedDatabase> findByDbName(String dbName);

    List<ProvisionedDatabase> findByDbNameStartingWith(String prefix);
}
