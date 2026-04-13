package com.filipnikolov.launchpad.user.repository;

import com.filipnikolov.launchpad.user.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findFirstByOrderByIdAsc();

    Optional<UserAccount> findByEmail(String email);
}
