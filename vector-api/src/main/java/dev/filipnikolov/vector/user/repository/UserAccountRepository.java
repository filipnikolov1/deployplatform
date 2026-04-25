package dev.filipnikolov.vector.user.repository;

import dev.filipnikolov.vector.user.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findFirstByOrderByIdAsc();

    Optional<UserAccount> findByEmail(String email);
}
