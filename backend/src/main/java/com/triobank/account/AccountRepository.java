package com.triobank.account;

import com.triobank.product.AccountType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Whether the account exists and belongs to the customer.
     */
    @Query("""
            select count(a) > 0 from Account a
            where a.id = :accountId and a.customer.id = :customerId""")
    boolean existsOfCustomer(@Param("accountId") UUID accountId, @Param("customerId") UUID customerId);

    /**
     * Same as {@link #findOfCustomer}, with the owner loaded too.
     */
    @Query("""
            select a from Account a join fetch a.product join fetch a.customer
            where a.id = :accountId and a.customer.id = :customerId""")
    Optional<Account> findOfCustomerWithOwner(@Param("accountId") UUID accountId,
                                              @Param("customerId") UUID customerId);

    /**
     * How many accounts of the product the customer has that are not closed. Blocked accounts count too.
     */
    @Query("""
            select count(a) from Account a
            where a.customer.id = :customerId and a.product.id = :productId
              and a.status <> com.triobank.account.AccountStatus.CLOSED""")
    long countNotClosed(@Param("customerId") UUID customerId, @Param("productId") UUID productId);

    /**
     * Whether the customer has at least one active account of the given type.
     */
    @Query("""
            select count(a) > 0 from Account a
            where a.customer.id = :customerId and a.product.type = :type
              and a.status = com.triobank.account.AccountStatus.ACTIVE""")
    boolean existsActiveOfType(@Param("customerId") UUID customerId, @Param("type") AccountType type);

    /**
     * Takes the next account number from the database sequence. Used to build the IBAN of a new account.
     */
    @Query(value = "SELECT CAST(nextval('account_number_seq') AS VARCHAR)", nativeQuery = true)
    String nextAccountNumber();

    /**
     * Locks the account rows with {@code SELECT ... FOR UPDATE} until the end of the transaction.
     * Rows are locked in id order, so two postings over the same pair of accounts never wait for each
     * other in a circle. Only ids come back: if an account was already read in this transaction,
     * Hibernate would refuse a locked row with a newer version, so the accounts are loaded afterwards.
     *
     * @return ids of the accounts that exist
     */
    @Query(value = "SELECT id FROM accounts WHERE id IN (:ids) ORDER BY id FOR UPDATE", nativeQuery = true)
    List<UUID> lockForUpdate(@Param("ids") Collection<UUID> ids);

}
