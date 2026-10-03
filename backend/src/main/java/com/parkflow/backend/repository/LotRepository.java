package com.parkflow.backend.repository;

import com.parkflow.backend.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, String> {
}
