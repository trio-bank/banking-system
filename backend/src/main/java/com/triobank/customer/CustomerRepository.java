package com.triobank.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    boolean existsByPesel(String pesel);

    /**
     * Takes the next 8-digit customer number from the database sequence.
     */
    @Query(value = "SELECT CAST(nextval('customer_number_seq') AS VARCHAR)", nativeQuery = true)
    String nextCustomerNumber();

}
