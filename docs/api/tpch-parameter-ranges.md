# TPC-H Parameter Ranges (Non-Empty Responses)

This page documents, for every TPC-H endpoint, the parameter values and ranges that produce a **non-empty response** at database scale factors **0.1**, **0.5**, and **1.0**.

## Methodology

The ranges were measured empirically against TPC-H databases generated with the standard `dbgen` algorithm (via DuckDB's TPC-H extension) at SF 0.1, 0.5, and 1.0, using queries that mirror the exact semantics of the JPQL queries in `api-http` (including RestQ-specific modifications). TPC-H data is deterministic for a given scale factor, so the ranges apply to any spec-conforming data load. Exact date boundaries and the fine-grained SF 0.1 gaps noted for Q19 may shift by a few days/values if a non-standard generator or seed is used.

For endpoints that return a single aggregate object (Q6, Q14, Q17, Q19), "non-empty" means the aggregate value is non-null.

## Summary

Most parameters drawn from the TPC-H specification's substitution domains produce non-empty results at **all three scale factors**. The scale factor only matters in a few places:

| Query | Scale-factor sensitivity |
|-------|--------------------------|
| Q1, Q3, Q10, Q12 | Date boundaries shift by a few days between SF 0.1 and SF 1.0 |
| Q11 | The maximum usable `fraction` shrinks as SF grows |
| Q19 | A few `brand` × `quantity` branch combinations are empty at SF 0.1 (overall response still non-empty) |
| All others | Identical behavior at SF 0.1, 0.5, and 1.0 |

Common value domains referenced below:

- **Regions (5):** `AFRICA`, `AMERICA`, `ASIA`, `EUROPE`, `MIDDLE EAST`
- **Nations (25):** `ALGERIA`, `ARGENTINA`, `BRAZIL`, `CANADA`, `EGYPT`, `ETHIOPIA`, `FRANCE`, `GERMANY`, `INDIA`, `INDONESIA`, `IRAN`, `IRAQ`, `JAPAN`, `JORDAN`, `KENYA`, `MOROCCO`, `MOZAMBIQUE`, `PERU`, `CHINA`, `ROMANIA`, `SAUDI ARABIA`, `VIETNAM`, `RUSSIA`, `UNITED KINGDOM`, `UNITED STATES`
- **Market segments (5):** `AUTOMOBILE`, `BUILDING`, `FURNITURE`, `HOUSEHOLD`, `MACHINERY`
- **Ship modes (7):** `REG AIR`, `AIR`, `RAIL`, `SHIP`, `TRUCK`, `MAIL`, `FOB`
- **Brands (25):** `Brand#MN` with M, N ∈ 1–5
- **Type syllable 3 (5):** `BRASS`, `COPPER`, `NICKEL`, `STEEL`, `TIN`

Data date coverage (identical at all SFs unless noted): `o_orderdate` spans **1992-01-01 – 1998-08-02**, `l_shipdate` spans **1992-01-02 – 1998-12-01** (SF 0.1: starts 1992-01-03).

---

## Q1 — `/pricing-summary`

Non-empty whenever `shipDate + delta` ≥ the earliest ship date in the data:

| SF | Earliest ship date | Non-empty condition |
|----|--------------------|---------------------|
| 0.1 | 1992-01-03 | `shipDate + delta` ≥ 1992-01-03 |
| 0.5 | 1992-01-02 | `shipDate + delta` ≥ 1992-01-02 |
| 1.0 | 1992-01-02 | `shipDate + delta` ≥ 1992-01-02 |

The default `shipDate` (1998-12-01) and the auto-generated `delta` (60–120) always produce a non-empty (and in fact complete) result. Any `shipDate` ≥ 1992-01-03 works at every SF regardless of `delta`.

## Q2 — `/supplier-part-info`

All **1250** combinations of `size` ∈ 1–50, `type` ∈ {`BRASS`, `COPPER`, `NICKEL`, `STEEL`, `TIN`} (the query matches `type` as a suffix of `p_type`), and the 5 regions are non-empty at **all three scale factors**.

## Q3 — `/order-revenue-info`

Any of the 5 market segments works. The `date` must lie strictly between the segment's earliest order date and latest ship date:

| SF | First valid `date` | Last valid `date` |
|----|--------------------|-------------------|
| 0.1 | 1992-01-02 | 1998-11-27 (`FURNITURE`) – 1998-11-30, varies by segment¹ |
| 0.5 | 1992-01-02 | 1998-11-30 (all segments) |
| 1.0 | 1992-01-02 | 1998-11-30 (all segments) |

¹ SF 0.1 last valid date per segment: `AUTOMOBILE` 1998-11-28, `BUILDING` 1998-11-29, `FURNITURE` 1998-11-27, `HOUSEHOLD` 1998-11-30, `MACHINERY` 1998-11-30.

**Safe range for every segment and SF: 1992-01-02 – 1998-11-27.** The TPC-H spec range (1995-03-01 – 1995-03-31) always works.

## Q4 — `/order-priority-count`

The query counts orders in `[date, date + 3 months)` that have a late line item. Identical at all SFs:

- **Valid `date` range: 1991-10-04 – 1998-08-02** (window must overlap order dates 1992-01-01 – 1998-08-02).
- The spec range (first day of any month from 1993-01 to 1997-10) always works.

## Q5 — `/local-supplier-volume`

Any of the 5 regions works. The one-year window `[startDate, startDate + 1 year)` must overlap the qualifying order dates (1992-01-01 – 1998-08-02 for every region and SF; SF 0.1 `ASIA` starts 1992-01-02):

- **Valid `startDate` range: 1991-01-02 – 1998-08-02** for every region at every SF.
- The spec values (January 1 of 1993–1997) always work.

## Q6 — `/revenue-increase`

Verified empirically at all SFs: every combination of `discount` ∈ 0.00–0.10 (step 0.01), `quantity` ≥ 2, and `startDate` = January 1 of 1992–1998 returns a non-null revenue value.

- `discount` — the query matches `l_discount BETWEEN discount − 0.01 AND discount + 0.01`; data contains discounts 0.00–0.10, so any `discount` in **0.00–0.10** works (spec: 0.02–0.09).
- `quantity` — must be **≥ 2** (the predicate is `l_quantity < quantity` and the minimum quantity in the data is 1). `quantity = 1` returns null at every SF. Spec values 24 and 25 always work.
- `startDate` — the one-year window must overlap ship dates: **1991-01-03 – 1998-12-01** (spec: January 1 of 1993–1997).

## Q7 — `/nations-volume-shipping`

With the default date range (1995-01-01 – 1995-12-31), **all 300 unordered nation pairs** are non-empty at all three scale factors. Custom `startDate`/`endDate` must overlap the ship-date range (1992-01-02 – 1998-12-01); the spec window 1995-01-01 – 1996-12-31 always works.

## Q8 — `/market-share`

The order-date window is fixed to 1995-01-01 – 1996-12-31. **All 750 combinations** of the 5 regions × 150 part types (`{STANDARD, SMALL, MEDIUM, LARGE, ECONOMY, PROMO} × {ANODIZED, BURNISHED, PLATED, POLISHED, BRUSHED} × {BRASS, COPPER, NICKEL, STEEL, TIN}`, e.g. `ECONOMY ANODIZED STEEL`) are non-empty at all three scale factors.

`nation` may be any of the 25 nations — it only affects the numerator of the market share, so the response stays non-empty either way; choose a nation belonging to the chosen region to get a non-zero share.

## Q9 — `/product-type-profit`

**All 92 color words** from the TPC-H part-name generator (e.g. `green`, `almond`, `antique`, …, `yellow`) match parts with line items and return non-empty results at all three scale factors.

## Q10 — `/returned-items`

Returned items (`l_returnflag = 'R'`) only exist for orders placed up to mid-1995, because the return flag is only set for line items received on or before 1995-06-17:

| SF | Valid `date` range |
|----|--------------------|
| 0.1 | 1991-10-04 – 1995-06-12 |
| 0.5 | 1991-10-04 – 1995-06-12 |
| 1.0 | 1991-10-04 – 1995-06-14 |

**Safe range for every SF: 1991-10-04 – 1995-06-12.** The spec range (first day of any month from 1993-02 to 1995-01) always works.

## Q11 — `/important-stock`

All 25 nations work. The `fraction` must be smaller than the largest single part group's share of the nation's total stock value — and this threshold **shrinks as the scale factor grows** (more parts per nation means each part is a smaller share):

| SF | Threshold range across nations | `fraction` guaranteed non-empty for every nation |
|----|-------------------------------|--------------------------------------------------|
| 0.1 | 0.00147 (PERU) – 0.00306 (JORDAN) | `fraction` ≤ **0.0014** |
| 0.5 | 0.00038 (GERMANY) – 0.00052 (ROMANIA) | `fraction` ≤ **0.00037** |
| 1.0 | 0.00020 (ALGERIA) – 0.00024 (UNITED STATES) | `fraction` ≤ **0.0002** |

The default `fraction` (0.0001) is non-empty for every nation at all three scale factors.

!!! note
    The controller is meant to divide `fraction` by the configured scale factor (per the spec's `0.0001 / SF`), but the scale factor is currently hard-coded to `1.0`, so the value you pass is used as-is.

## Q12 — `/shipping-modes`

Any pair of the 7 ship modes works. The one-year window `[date, date + 1 year)` on the receipt date must overlap the qualifying receipt dates (roughly 1992-02 – 1998-11 for every mode; the tightest bounds occur at SF 0.1, e.g. `MAIL` 1992-02-13 – 1998-11-15, `REG AIR` 1992-02-12 – 1998-11-11):

- **Valid `date` range for every mode pair at every SF: 1991-02-14 – 1998-11-11.**
- The spec values (January 1 of 1993–1997) always work.

## Q13 — `/customer-distribution`

**All 16 combinations** of `word1` ∈ {`special`, `pending`, `unusual`, `express`} × `word2` ∈ {`packages`, `requests`, `accounts`, `deposits`} are non-empty at all scale factors — the query left-joins from `customer`, so it always returns one row per customer (15,000 / 75,000 / 150,000 rows at SF 0.1 / 0.5 / 1.0).

## Q14 — `/promotion-revenue`

Every date the controller accepts (years **1993–1997**) produces a non-null result at all three scale factors, since the one-month window always falls inside the ship-date coverage (1992-01 – 1998-12).

## Q16 — `/part-supplier-relationships`

Every input the controller accepts — any brand `Brand#MN` (excluded from results), any type prefix (e.g. `MEDIUM POLISHED`), and any 8 distinct sizes in 1–50 — is non-empty at all three scale factors. The filter is exclusionary, so thousands of part groups always remain (e.g. the spec example returns 2,762 / 11,527 / 18,314 rows at SF 0.1 / 0.5 / 1.0).

## Q17 — `/small-quantity-revenue`

**All 400 combinations** of the 25 brands × the 16 containers accepted by the controller (`{SM, MED, LG, JUMBO} × {BOX, CASE, PACK, PKG}`) return a non-null result at all three scale factors.

## Q19 — `/discounted-revenue`

The three branches (SM / MED / LG containers, tied to `brand1`/`quantity1`, `brand2`/`quantity2`, `brand3`/`quantity3`) are OR-ed together, so the response is non-empty as long as **at least one** branch matches.

- **SF 0.5 and 1.0:** every branch matches for every brand and every quantity in the accepted ranges (`quantity1` 1–10, `quantity2` 10–20, `quantity3` 20–30). Any valid request is non-empty.
- **SF 0.1:** the LG branch (`brand3`/`quantity3`) matches for every brand and quantity, so **every valid request still returns a non-empty response**. However, some individual SM/MED branch combinations contribute nothing:
    - SM branch empty for: `Brand#11` (q1 ≥ 4), `Brand#13` (q1 3–9), `Brand#15` (q1 = 10), `Brand#21` (all q1), `Brand#23` (q1 4–7), `Brand#24` (q1 4–7), `Brand#51` (q1 = 8)
    - MED branch empty for: `Brand#11` (q2 ≥ 14), `Brand#13` (q2 = 10), `Brand#15` (q2 12–13), `Brand#54` (q2 15–17)

## Q21 — `/suppliers-kept-waiting`

**All 25 nations** return non-empty results at all three scale factors.

## Q22 — `/global-sales-opportunities`

Customer phone country codes span **10–34** (nation key + 10). Every code in 10–34 has qualifying customers (positive above-average balance, no orders) at all three scale factors, so **any 7 distinct codes chosen from 10–34** produce a non-empty response. Codes outside 10–34 match no customers; a request where all 7 codes fall outside this range returns an empty response.
