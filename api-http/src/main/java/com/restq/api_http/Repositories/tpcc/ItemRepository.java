package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.Item.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer> {

    // For New Order transaction - get item price, name, and data
    @Query("SELECT i.price, i.name, i.data FROM Item i WHERE i.itemId = :itemId")
    Object[] getItemPriceNameData(@Param("itemId") Integer itemId);

    // For New Order transaction - get item info
    @Query("SELECT i FROM Item i WHERE i.itemId = :itemId")
    Item findByItemId(@Param("itemId") Integer itemId);

    // For New Order transaction - batch get multiple items
    @Query("SELECT i FROM Item i WHERE i.itemId IN :itemIds ORDER BY i.itemId")
    List<Item> findByItemIdIn(@Param("itemIds") List<Integer> itemIds);
} 
