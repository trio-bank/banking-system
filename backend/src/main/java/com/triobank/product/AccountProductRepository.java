package com.triobank.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountProductRepository extends JpaRepository<AccountProduct, UUID> {

    Optional<AccountProduct> findByCode(String code);

    List<AccountProduct> findByActiveTrueOrderByCode();

}
