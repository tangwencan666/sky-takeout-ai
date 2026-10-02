package com.tang.sky.pojo;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class StatisticsModels {
    private StatisticsModels() {}
    public record TopDish(long dishId, String name, long quantity, BigDecimal amount) {}
    public record Trend(LocalDate day, long orderCount) {}
    public record Revenue(long completedOrders, BigDecimal totalAmount) {}
}
