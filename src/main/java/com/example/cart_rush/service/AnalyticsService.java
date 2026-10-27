package com.example.cart_rush.service;

import com.example.cart_rush.entity.Order;
import com.example.cart_rush.entity.OrderItem;
import com.example.cart_rush.entity.Review;
import com.example.cart_rush.repository.OrderItemRepository;
import com.example.cart_rush.repository.OrderRepository;
import com.example.cart_rush.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Builds all the numbers the admin dashboard charts need in one pass.
 * Cancelled orders never count towards revenue / orders / units.
 */
@Service
public class AnalyticsService {

    private static final int DAYS = 30;
    private static final int WEEKS = 12;
    private static final int MONTHS = 12;
    private static final int SPARK_DAYS = 14;
    private static final int TOP_PRODUCTS = 5;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;

    public AnalyticsService(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            ReviewRepository reviewRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.reviewRepository = reviewRepository;
    }

    /* ---------- response shapes ---------- */

    public record Series(List<String> labels, List<BigDecimal> revenue, List<Integer> orders) {}

    public record Kpi(String key, String label, BigDecimal value, Double changePct, List<BigDecimal> spark) {}

    public record TopProduct(Long id, String name, BigDecimal revenue, long units) {}

    public record Analytics(
            List<Kpi> kpis,
            Series daily,
            Series weekly,
            Series monthly,
            Map<String, Long> orderStatus,
            Map<String, BigDecimal> categoryRevenue,
            List<TopProduct> topProducts,
            Map<Integer, Long> ratingDistribution) {}

    /** Running totals for one day / week / month bucket. */
    private static class Agg {
        BigDecimal revenue = BigDecimal.ZERO;
        int orders = 0;
        long units = 0;

        void add(BigDecimal rev, long u) {
            revenue = revenue.add(rev);
            orders++;
            units += u;
        }

        void merge(Agg o) {
            revenue = revenue.add(o.revenue);
            orders += o.orders;
            units += o.units;
        }
    }

    /* ---------- main entry ---------- */

    @Transactional(readOnly = true)
    public Analytics getAnalytics() {
        LocalDate today = LocalDate.now();

        List<Order> orders = orderRepository.findAll();
        List<OrderItem> items = orderItemRepository.findAllForAnalytics();

        // revenue + units per order
        Map<Long, BigDecimal> revenueByOrder = new HashMap<>();
        Map<Long, Long> unitsByOrder = new HashMap<>();

        // all-time (non-cancelled) breakdowns
        Map<String, BigDecimal> categoryRevenue = new HashMap<>();
        Map<Long, BigDecimal> productRevenue = new HashMap<>();
        Map<Long, Long> productUnits = new HashMap<>();
        Map<Long, String> productName = new HashMap<>();

        for (OrderItem item : items) {
            if (item.getOrder() == null || item.getProduct() == null || item.getQuantity() == null) {
                continue;
            }
            // New order items retain their purchase-time price. Older rows created before
            // unit_price existed have no historical snapshot, so fall back to current price.
            BigDecimal unitPrice = item.getUnitPrice() != null
                    ? item.getUnitPrice() : item.getProduct().getPrice();
            if (unitPrice == null) {
                continue;
            }
            BigDecimal line = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            Long orderId = item.getOrder().getId();
            revenueByOrder.merge(orderId, line, BigDecimal::add);
            unitsByOrder.merge(orderId, (long) item.getQuantity(), Long::sum);

            if (isCancelled(item.getOrder())) {
                continue;
            }
            Long pid = item.getProduct().getId();
            productRevenue.merge(pid, line, BigDecimal::add);
            productUnits.merge(pid, (long) item.getQuantity(), Long::sum);
            productName.putIfAbsent(pid, item.getProduct().getName());

            String cat = item.getProduct().getCategory() != null
                    && item.getProduct().getCategory().getName() != null
                    ? item.getProduct().getCategory().getName()
                    : "Uncategorized";
            categoryRevenue.merge(cat, line, BigDecimal::add);
        }

        // order status counts (all orders, cancelled included)
        Map<String, Long> orderStatus = new LinkedHashMap<>();
        for (String s : List.of("PLACED", "SHIPPED", "DELIVERED", "CANCELLED")) {
            orderStatus.put(s, 0L);
        }

        // day buckets (non-cancelled only)
        TreeMap<LocalDate, Agg> byDay = new TreeMap<>();
        for (Order o : orders) {
            String status = o.getStatus() == null ? "UNKNOWN" : o.getStatus().toUpperCase();
            orderStatus.merge(status, 1L, Long::sum);

            if (isCancelled(o)) {
                continue;
            }
            // Do not assign an unknown timestamp to today: that would fabricate today's revenue.
            // Undated legacy orders remain in status/all-time product counts but are omitted
            // from time-series and date-range KPI calculations until their date is repaired.
            if (o.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = o.getCreatedAt().toLocalDate();
            byDay.computeIfAbsent(day, d -> new Agg())
                    .add(revenueByOrder.getOrDefault(o.getId(), BigDecimal.ZERO),
                            unitsByOrder.getOrDefault(o.getId(), 0L));
        }

        // roll days up into weeks (Monday start) and months
        Map<LocalDate, Agg> byWeek = new TreeMap<>();
        Map<YearMonth, Agg> byMonth = new TreeMap<>();
        for (Map.Entry<LocalDate, Agg> e : byDay.entrySet()) {
            LocalDate weekStart = e.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            byWeek.computeIfAbsent(weekStart, d -> new Agg()).merge(e.getValue());
            byMonth.computeIfAbsent(YearMonth.from(e.getKey()), d -> new Agg()).merge(e.getValue());
        }

        // series
        List<String> dLabels = new ArrayList<>();
        List<BigDecimal> dRev = new ArrayList<>();
        List<Integer> dOrd = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Agg a = byDay.get(d);
            dLabels.add(d.format(DAY_FMT));
            dRev.add(a == null ? BigDecimal.ZERO : a.revenue);
            dOrd.add(a == null ? 0 : a.orders);
        }

        List<String> wLabels = new ArrayList<>();
        List<BigDecimal> wRev = new ArrayList<>();
        List<Integer> wOrd = new ArrayList<>();
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = WEEKS - 1; i >= 0; i--) {
            LocalDate w = thisWeek.minusWeeks(i);
            Agg a = byWeek.get(w);
            LocalDate wEnd = w.plusDays(6);
            String weekLabel = w.getYear() == wEnd.getYear()
                    ? w.format(SHORT_DAY_FMT) + "–" + wEnd.format(SHORT_DAY_FMT) + " " + wEnd.getYear()
                    : w.format(SHORT_DAY_FMT) + " " + w.getYear() + "–" + wEnd.format(SHORT_DAY_FMT) + " " + wEnd.getYear();
            wLabels.add(weekLabel);
            wRev.add(a == null ? BigDecimal.ZERO : a.revenue);
            wOrd.add(a == null ? 0 : a.orders);
        }

        List<String> mLabels = new ArrayList<>();
        List<BigDecimal> mRev = new ArrayList<>();
        List<Integer> mOrd = new ArrayList<>();
        YearMonth thisMonth = YearMonth.from(today);
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth m = thisMonth.minusMonths(i);
            Agg a = byMonth.get(m);
            mLabels.add(m.format(MONTH_FMT));
            mRev.add(a == null ? BigDecimal.ZERO : a.revenue);
            mOrd.add(a == null ? 0 : a.orders);
        }

        // KPI cards: last 30 days vs the 30 days before
        Agg cur = range(byDay, today.minusDays(DAYS - 1), today);
        Agg prev = range(byDay, today.minusDays(2L * DAYS - 1), today.minusDays(DAYS));

        List<BigDecimal> sparkRev = new ArrayList<>();
        List<BigDecimal> sparkOrd = new ArrayList<>();
        List<BigDecimal> sparkUnits = new ArrayList<>();
        List<BigDecimal> sparkAov = new ArrayList<>();
        for (int i = SPARK_DAYS - 1; i >= 0; i--) {
            Agg a = byDay.get(today.minusDays(i));
            sparkRev.add(a == null ? BigDecimal.ZERO : a.revenue);
            sparkOrd.add(BigDecimal.valueOf(a == null ? 0 : a.orders));
            sparkUnits.add(BigDecimal.valueOf(a == null ? 0 : a.units));
            sparkAov.add(a == null ? BigDecimal.ZERO : average(a.revenue, a.orders));
        }

        BigDecimal curAov = average(cur.revenue, cur.orders);
        BigDecimal prevAov = average(prev.revenue, prev.orders);

        List<Kpi> kpis = List.of(
                new Kpi("revenue", "Revenue", cur.revenue, pct(cur.revenue, prev.revenue), sparkRev),
                new Kpi("orders", "Orders", BigDecimal.valueOf(cur.orders),
                        pct(BigDecimal.valueOf(cur.orders), BigDecimal.valueOf(prev.orders)), sparkOrd),
                new Kpi("units", "Units sold", BigDecimal.valueOf(cur.units),
                        pct(BigDecimal.valueOf(cur.units), BigDecimal.valueOf(prev.units)), sparkUnits),
                new Kpi("aov", "Avg. order value", curAov, pct(curAov, prevAov), sparkAov)
        );

        // top products by revenue
        List<TopProduct> topProducts = productRevenue.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_PRODUCTS)
                .map(e -> new TopProduct(e.getKey(), productName.get(e.getKey()),
                        e.getValue(), productUnits.getOrDefault(e.getKey(), 0L)))
                .toList();

        // categories, biggest first
        Map<String, BigDecimal> sortedCategories = new LinkedHashMap<>();
        categoryRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> sortedCategories.put(e.getKey(), e.getValue()));

        // rating distribution 1..5
        Map<Integer, Long> ratings = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratings.put(i, 0L);
        }
        for (Review r : reviewRepository.findAll()) {
            if (r.getRating() != null && r.getRating() >= 1 && r.getRating() <= 5) {
                ratings.merge(r.getRating(), 1L, Long::sum);
            }
        }

        return new Analytics(
                kpis,
                new Series(dLabels, dRev, dOrd),
                new Series(wLabels, wRev, wOrd),
                new Series(mLabels, mRev, mOrd),
                orderStatus,
                sortedCategories,
                topProducts,
                ratings);
    }

    /* ---------- helpers ---------- */

    private static boolean isCancelled(Order o) {
        return "CANCELLED".equalsIgnoreCase(o.getStatus());
    }

    private static Agg range(NavigableMap<LocalDate, Agg> byDay, LocalDate from, LocalDate to) {
        Agg total = new Agg();
        for (Agg a : byDay.subMap(from, true, to, true).values()) {
            total.merge(a);
        }
        return total;
    }

    private static BigDecimal average(BigDecimal total, int count) {
        return count == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /** % change vs previous period, or null when there is nothing to compare against. */
    private static Double pct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
