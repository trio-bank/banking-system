package com.triobank.account;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIban(String iban);

    Optional<Account> findBySystemCode(SystemAccountCode systemCode);

    /**
     * All accounts of a customer with their products loaded in the same query: personal ones first,
     * then by opening time. The id at the end keeps the order stable when two accounts share a timestamp.
     */
    @Query("""
            select a from Account a join fetch a.product p
            where a.customer.id = :customerId
            order by case when p.type = com.triobank.product.AccountType.PERSONAL then 0 else 1 end,
                     a.openedAt, a.id""")
    List<Account> findAllOfCustomer(@Param("customerId") UUID customerId);

    /**
     * One account of a customer with its product. Empty both when the account does not exist
     * and when it belongs to someone else.
     */
    @Query("""
            select a from Account a join fetch a.product
            where a.id = :accountId and a.customer.id = :customerId""")
    Optional<Account> findOfCustomer(@Param("accountId") UUID accountId, @Param("customerId") UUID customerId);

    /**
     * Takes the next account number from the database sequence. Used to build the IBAN of a new account.
     */
    @Query(value = "SELECT CAST(nextval('account_number_seq') AS VARCHAR)", nativeQuery = true)
    String nextAccountNumber();

    /**
     * Loads the accounts with {@code SELECT ... FOR UPDATE}. Rows are locked in id order,
     * so two postings over the same pair of accounts never wait for each other in a circle.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> findAllForUpdate(@Param("ids") Collection<UUID> ids);

}
