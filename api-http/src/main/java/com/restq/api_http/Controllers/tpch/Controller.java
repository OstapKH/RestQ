package com.restq.api_http.Controllers.tpch;

import com.restq.api_http.DTO.tpch.*;
import com.restq.api_http.Repositories.tpch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/reports")
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCH", matchIfMissing = false)
public class Controller {

    @Autowired
    private LineItemRepository lineItemRepository;
    @Autowired
    private PartRepository supplierPartRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private SupplierRepository supplierRepository;
    @Autowired
    private NationRepository nationRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PartSuppRepository partSuppRepository;

//    @Value("${app.database.scale-factor}")
    private Double scaleFactor = 1.0;

    // Q1
    // This endpoint accepts DELTA and endDate as query parameters
    @GetMapping("/pricing-summary")
    public List<PricingSummaryReport> getPricingSummary(
            @RequestParam(value = "delta", required = false) Integer delta,
            @RequestParam(value = "shipDate", defaultValue = "1998-12-01") LocalDate shipDate
    ) {
        // If DELTA is not passed, generate a random DELTA between 60 and 120
        if (delta == null || delta == 0 || delta > 120) {
            Random random = new Random();
            delta = 60 + random.nextInt(61);
        }

        // Calculate the endDate by adding DELTA days to the shipDate
        LocalDate endDate = shipDate.plusDays(delta);

        return lineItemRepository.getPricingSummaryReport(endDate);
    }

    // Q2
    @GetMapping("/supplier-part-info")
    public List<SupplierPartInfo> getSupplierPartInfo(
            @RequestParam(value = "size") Integer size,
            @RequestParam(value = "type") String type,
            @RequestParam(value = "region") String region) {
        return supplierPartRepository.findSupplierPartInfo(size, type, region);
    }

    // Q3
    @GetMapping("/order-revenue-info")
    public List<OrderRevenueInfo> getOrderRevenueInfo(
            @RequestParam(value = "segment") String segment,
            @RequestParam(value = "date") LocalDate date) {
        return orderRepository.findTopUnshippedOrders(segment, date);
    }

    // Q4
    @GetMapping("/order-priority-count")
    public List<OrderPriorityCountInfo> getOrderPriorityCount(
            @RequestParam(value = "date") LocalDate date) {
        LocalDate datePlus = date.plusMonths(3);
        return orderRepository.findOrderPriorityCount(date, datePlus);
    }

    // Q5
    @GetMapping("/local-supplier-volume")
    public List<LocalSupplierVolume> getLocalSupplierVolume(
            @RequestParam(value = "region") String region,
            @RequestParam(value = "startDate") LocalDate startDate) {
        LocalDate endDate = startDate.plusYears(1);
        return supplierRepository.findLocalSupplierVolume(region, startDate, endDate);
    }

    // Q6
    @GetMapping("/revenue-increase")
    public BigDecimal getRevenueIncrease(
            @RequestParam(value = "discount") Double discount,
            @RequestParam(value = "quantity") Integer quantity,
            @RequestParam(value = "startDate") LocalDate startDate
    ) {
        LocalDate endDate = startDate.plusYears(1);
        return lineItemRepository.calculateRevenueIncrease(startDate, endDate, discount, quantity);
    }

    // Q7 (Modified)
    @GetMapping("/nations-volume-shipping")
    public List<VolumeShippingOfNations> getNationsVolumeShipping(
            @RequestParam(value = "nation1") String nation1,
            @RequestParam(value = "nation2") String nation2,
            @RequestParam(value = "startDate", defaultValue = "1995-01-01") LocalDate startDate,
            @RequestParam(value = "endDate", defaultValue = "1995-12-31") LocalDate endDate) {
        return nationRepository.getNationsVolumeShipping(nation1, nation2, startDate, endDate);
    }

    // Q8
    @GetMapping("/market-share")
    public List<MarketShareReport> getMarketShare(
            @RequestParam(value = "nation") String nation,
            @RequestParam(value = "region") String region,
            @RequestParam(value = "type") String type) {
        // Fixed date range for 1995-1996 as per specification
        LocalDate startDate = LocalDate.of(1995, 1, 1);
        LocalDate endDate = LocalDate.of(1996, 12, 31);

        return nationRepository.getMarketShare(nation, region, type, startDate, endDate);
    }

    // Q9
    @GetMapping("/product-type-profit")
    public List<ProductProfitReport> getProductTypeProfit(
            @RequestParam(value = "color") String color) {
        // Pass the color parameter directly, wildcards are handled in the query
        return nationRepository.getProductTypeProfit(color);
    }

    // Q10
    @GetMapping("/returned-items")
    public List<ReturnedItemReport> getReturnedItems(
            @RequestParam(value = "date") LocalDate startDate) {
        // Calculate end date (3 months after start date)
        LocalDate endDate = startDate.plusMonths(3);

        return customerRepository.findTopReturnedItems(startDate, endDate);
    }

   // Q11
   @GetMapping("/important-stock")
   public List<ImportantStockReport> getImportantStock(
           @RequestParam(value = "nation") String nation,
           @RequestParam(value = "fraction", defaultValue = "0.0001") Double fraction) {
            
       // Calculate fraction as 0.0001 / SF as per specification
       BigDecimal fractionPerScaleFactor = BigDecimal.valueOf(fraction)
           .divide(BigDecimal.valueOf(scaleFactor), java.math.RoundingMode.HALF_UP);

       return partSuppRepository.findImportantStock(nation, fractionPerScaleFactor);
   }

    // Q12
    @GetMapping("/shipping-modes")
    public List<ShippingModeReport> getShippingModes(
            @RequestParam(value = "shipMode1") String shipMode1,
            @RequestParam(value = "shipMode2") String shipMode2,
            @RequestParam(value = "date") LocalDate startDate) {
        // Calculate end date (1 year after start date)
        LocalDate endDate = startDate.plusYears(1);
        
        // Create list of ship modes
        List<String> shipModes = Arrays.asList(shipMode1, shipMode2);
        
        return lineItemRepository.findShippingModeStats(shipModes, startDate, endDate);
    }

    // Q13
    @GetMapping("/customer-distribution")
    public List<CustomerDistributionReport> getCustomerDistribution(
            @RequestParam(value = "word1", defaultValue = "special") String word1,
            @RequestParam(value = "word2", defaultValue = "requests") String word2) {
        // Validate word1 is one of: special, pending, unusual, express
        List<String> validWord1 = Arrays.asList("special", "pending", "unusual", "express");
        if (!validWord1.contains(word1.toLowerCase())) {
            throw new IllegalArgumentException("word1 must be one of: special, pending, unusual, express");
        }
        
        // Validate word2 is one of: packages, requests, accounts, deposits
        List<String> validWord2 = Arrays.asList("packages", "requests", "accounts", "deposits");
        if (!validWord2.contains(word2.toLowerCase())) {
            throw new IllegalArgumentException("word2 must be one of: packages, requests, accounts, deposits");
        }
        
        return customerRepository.findCustomerDistribution(word1, word2);
    }

    // Q14
    @GetMapping("/promotion-revenue")
    public PromotionRevenueReport getPromotionRevenue(
            @RequestParam(value = "date") LocalDate startDate) {
        // Validate date is between 1993 and 1997
        if (startDate.getYear() < 1993 || startDate.getYear() > 1997) {
            throw new IllegalArgumentException("Date must be between 1993 and 1997");
        }
        
        // Calculate end date (1 month after start date)
        LocalDate endDate = startDate.plusMonths(1);
        
        return lineItemRepository.calculatePromotionRevenue(startDate, endDate);
    }

//    // Q15
//    @GetMapping("/top-suppliers")
//    public List<TopSupplierReport> getTopSuppliers(
//            @RequestParam(value = "date") LocalDate startDate) {
//        // Validate date is between 1993-01 and 1997-10
//        if (startDate.getYear() < 1993 ||
//            (startDate.getYear() == 1997 && startDate.getMonthValue() > 10) ||
//            startDate.getYear() > 1997) {
//            throw new IllegalArgumentException("Date must be between 1993-01 and 1997-10");
//        }
//
//        // Calculate end date (3 months after start date)
//        LocalDate endDate = startDate.plusMonths(3);
//
//        return supplierRepository.findTopSuppliers(startDate, endDate);
//    }

    // Q16
    @GetMapping("/part-supplier-relationships")
    public List<PartSupplierReport> getPartSupplierRelationships(
            @RequestParam(value = "brand") String brand,
            @RequestParam(value = "type") String type,
            @RequestParam(value = "sizes") List<Integer> sizes) {
        // Validate brand format (Brand#MN where M,N are 1-5)
        if (!brand.matches("Brand#[1-5][1-5]")) {
            throw new IllegalArgumentException("Brand must be in format Brand#MN where M,N are digits 1-5");
        }
        
        // Validate type (should be 2 syllables)
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException("Type cannot be empty");
        }
        
        // Validate sizes (must be 8 different values between 1 and 50)
        if (sizes.size() != 8 || 
            sizes.stream().anyMatch(s -> s < 1 || s > 50) ||
            sizes.stream().distinct().count() != 8) {
            throw new IllegalArgumentException(
                "Must provide 8 different sizes, each between 1 and 50");
        }
        
        return partSuppRepository.findPartSupplierRelationships(brand, type, sizes);
    }

    // Q17
    @GetMapping("/small-quantity-revenue")
    public SmallQuantityRevenueReport getSmallQuantityRevenue(
            @RequestParam(value = "brand") String brand,
            @RequestParam(value = "container") String container) {
        // Validate brand format (Brand#MN where M,N are 1-5)
        if (!brand.matches("Brand#[1-5][1-5]")) {
            throw new IllegalArgumentException("Brand must be in format Brand#MN where M,N are digits 1-5");
        }
        
        // Validate container (should be 2 syllables)
        List<String> validContainers = Arrays.asList(
            "SM BOX", "SM CASE", "SM PACK", "SM PKG",
            "MED BOX", "MED CASE", "MED PACK", "MED PKG",
            "LG BOX", "LG CASE", "LG PACK", "LG PKG",
            "JUMBO BOX", "JUMBO CASE", "JUMBO PACK", "JUMBO PKG"
        );
        if (!validContainers.contains(container.toUpperCase())) {
            throw new IllegalArgumentException("Invalid container type");
        }
        
        return lineItemRepository.calculateSmallQuantityRevenue(brand, container);
    }

//    // Q18
//    @GetMapping("/large-volume-customers")
//    public List<LargeVolumeCustomerReport> getLargeVolumeCustomers(
//            @RequestParam(value = "quantity", defaultValue = "313") Integer quantity) {
//        // Validate quantity is between 312 and 315
//        if (quantity < 312 || quantity > 315) {
//            throw new IllegalArgumentException("Quantity must be between 312 and 315");
//        }
//
//        // Create Pageable for top 100 results
//        Pageable topHundred = PageRequest.of(0, 100);
//
//        return customerRepository.findLargeVolumeCustomers(
//            BigDecimal.valueOf(quantity),
//            topHundred
//        );
//    }

    // Q19
    @GetMapping("/discounted-revenue")
    public DiscountedRevenueReport getDiscountedRevenue(
            @RequestParam(value = "brand1") String brand1,
            @RequestParam(value = "brand2") String brand2,
            @RequestParam(value = "brand3") String brand3,
            @RequestParam(value = "quantity1") Integer quantity1,
            @RequestParam(value = "quantity2") Integer quantity2,
            @RequestParam(value = "quantity3") Integer quantity3) {
        // Validate brand formats (Brand#MN where M,N are 1-5)
        List<String> brands = Arrays.asList(brand1, brand2, brand3);
        for (String brand : brands) {
            if (!brand.matches("Brand#[1-5][1-5]")) {
                throw new IllegalArgumentException(
                    "Brands must be in format Brand#MN where M,N are digits 1-5");
            }
        }
        
        // Validate quantities are in correct ranges
        if (quantity1 < 1 || quantity1 > 10) {
            throw new IllegalArgumentException("Quantity1 must be between 1 and 10");
        }
        if (quantity2 < 10 || quantity2 > 20) {
            throw new IllegalArgumentException("Quantity2 must be between 10 and 20");
        }
        if (quantity3 < 20 || quantity3 > 30) {
            throw new IllegalArgumentException("Quantity3 must be between 20 and 30");
        }
        
        return lineItemRepository.calculateDiscountedRevenue(
            brand1, brand2, brand3,
            quantity1, quantity2, quantity3
        );
    }

//    // Q20
//    @GetMapping("/potential-promotion-suppliers")
//    public List<PotentialPromotionReport> getPotentialPromotionSuppliers(
//            @RequestParam(value = "color") String color,
//            @RequestParam(value = "date") LocalDate startDate,
//            @RequestParam(value = "nation") String nation) {
//        // Validate date is between 1993 and 1997
//        if (startDate.getYear() < 1993 || startDate.getYear() > 1997 ||
//            startDate.getDayOfMonth() != 1 || startDate.getMonthValue() != 1) {
//            throw new IllegalArgumentException(
//                "Date must be January 1st of a year between 1993 and 1997");
//        }
//
//        // Calculate end date (1 year after start date)
//        LocalDate endDate = startDate.plusYears(1);
//
//        // Validate nation is in the list of valid nations
//        List<String> validNations = Arrays.asList(
//            "ALGERIA", "ARGENTINA", "BRAZIL", "CANADA", "EGYPT", "ETHIOPIA",
//            "FRANCE", "GERMANY", "INDIA", "INDONESIA", "IRAN", "IRAQ", "JAPAN",
//            "JORDAN", "KENYA", "MOROCCO", "MOZAMBIQUE", "PERU", "CHINA",
//            "ROMANIA", "SAUDI ARABIA", "VIETNAM", "RUSSIA", "UNITED KINGDOM",
//            "UNITED STATES"
//        );
//        if (!validNations.contains(nation.toUpperCase())) {
//            throw new IllegalArgumentException("Invalid nation");
//        }
//
//        return supplierRepository.findPotentialPromotionSuppliers(
//            color, startDate, endDate, nation);
//    }

    // Q21
    @GetMapping("/suppliers-kept-waiting")
    public List<SupplierWaitingReport> getSuppliersWhoKeptWaiting(
            @RequestParam(value = "nation") String nation) {
        // Validate nation is in the list of valid nations
        List<String> validNations = Arrays.asList(
            "ALGERIA", "ARGENTINA", "BRAZIL", "CANADA", "EGYPT", "ETHIOPIA",
            "FRANCE", "GERMANY", "INDIA", "INDONESIA", "IRAN", "IRAQ", "JAPAN",
            "JORDAN", "KENYA", "MOROCCO", "MOZAMBIQUE", "PERU", "CHINA",
            "ROMANIA", "SAUDI ARABIA", "VIETNAM", "RUSSIA", "UNITED KINGDOM",
            "UNITED STATES"
        );
        if (!validNations.contains(nation.toUpperCase())) {
            throw new IllegalArgumentException("Invalid nation");
        }
        
        // Create Pageable for top 100 results
        Pageable topHundred = PageRequest.of(0, 100);
        
        return supplierRepository.findSuppliersWhoKeptWaiting(nation, topHundred);
    }

    // Q22
    @GetMapping("/global-sales-opportunities")
    public List<GlobalSalesOpportunityReport> getGlobalSalesOpportunities(
            @RequestParam(value = "countryCodes") List<String> countryCodes) {
        // Validate we have exactly 7 country codes
        if (countryCodes.size() != 7) {
            throw new IllegalArgumentException("Must provide exactly 7 country codes");
        }
        
        // Validate country codes are 2 digits
        for (String code : countryCodes) {
            if (!code.matches("\\d{2}")) {
                throw new IllegalArgumentException(
                    "Country codes must be exactly 2 digits");
            }
        }
        
        // Validate country codes are unique
        if (countryCodes.stream().distinct().count() != 7) {
            throw new IllegalArgumentException(
                "Country codes must be unique");
        }
        
        return customerRepository.findGlobalSalesOpportunities(countryCodes);
    }
}
