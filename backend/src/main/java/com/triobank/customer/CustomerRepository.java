package com.triobank.customer;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    boolean existsByPesel(String pesel);

    /**
     * Loads the customer with {@code SELECT ... FOR UPDATE}, so the row stays locked until the transaction ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Takes the next 8-digit customer number from the database sequence.
     */
    @Query(value = "SELECT CAST(nextval('customer_number_seq') AS VARCHAR)", nativeQuery = true)
    String nextCustomerNumber();

}
