package com.triobank.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIban(String iban);

    Optional<Account> findBySystemCode(SystemAccountCode systemCode);

    /**
     * Takes the next account number from the database sequence. Used to build the IBAN of a new account.
     */
    @Query(value = "SELECT CAST(nextval('account_number_seq') AS VARCHAR)", nativeQuery = true)
    String nextAccountNumber();

}
