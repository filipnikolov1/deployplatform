package dev.filipnikolov.vector.connect.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppsPostgresConfigRepository extends JpaRepository<AppsPostgresConfig, Long> {
}
