package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.History.History;
import com.restq.core.Models.tpcc.History.HistoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoryRepository extends JpaRepository<History, HistoryId> {

    // History is primarily insert-only for payment transactions
    // All necessary functionality is provided by JpaRepository
} 
