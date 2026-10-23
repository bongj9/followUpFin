package com.bongj9.briefing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DisclosureRepository extends JpaRepository<Disclosure, String> {

    List<Disclosure> findByStockCodeInAndRceptDtGreaterThanEqual(Collection<String> stockCodes, LocalDate since);
}
