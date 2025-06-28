package com.restq.api_http.utils;

import java.util.Random;

public class TpccUtil {

    // TPC-C Constants
    public static final int INVALID_ITEM_ID = 100001;
    public static final int MIN_ITEM_ID = 1;
    public static final int MAX_ITEM_ID = 100000;
    public static final int MIN_CUSTOMER_ID = 1;
    public static final int MAX_CUSTOMER_ID = 3000;

    // Generate random number between min and max (inclusive)
    public static int randomNumber(int min, int max, Random random) {
        return random.nextInt(max - min + 1) + min;
    }

    // Generate customer ID (1-3000)
    public static int getCustomerID(Random random) {
        return randomNumber(MIN_CUSTOMER_ID, MAX_CUSTOMER_ID, random);
    }

    // Generate item ID (1-100000)
    public static int getItemID(Random random) {
        return randomNumber(MIN_ITEM_ID, MAX_ITEM_ID, random);
    }

    // Generate random last name using TPC-C specification
    public static String randomLastName(Random random) {
        String[] syllables = {
            "BAR", "OUGHT", "ABLE", "PRI", "PRES", "ESE", "ANTI", "CALLY", "ATION", "EING"
        };
        
        int num = randomNumber(0, 999, random);
        return syllables[num / 100] + syllables[(num / 10) % 10] + syllables[num % 10];
    }

    // Generate non-uniform random number for customer selection by last name
    public static int getNonUniformRandom(int type, int min, int max, Random random) {
        // Simplified non-uniform random for demo purposes
        return randomNumber(min, max, random);
    }

    // Generate payment amount (1.00 to 5000.00)
    public static float getPaymentAmount(Random random) {
        return randomNumber(100, 500000, random) / 100.0f;
    }

    // Get district info string based on district ID
    public static String getDistrictInfo(int districtId, String[] distInfos) {
        if (districtId >= 1 && districtId <= 10) {
            return distInfos[districtId - 1];
        }
        return distInfos[0];
    }

    // Calculate order line amount
    public static float calculateOrderLineAmount(int quantity, float itemPrice, float discount) {
        return quantity * itemPrice * (1 - discount);
    }
} 
