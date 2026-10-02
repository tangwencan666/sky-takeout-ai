package com.tang.sky.service;

import com.tang.sky.common.*;
import com.tang.sky.mapper.StatisticsMapper;
import com.tang.sky.pojo.StatisticsModels.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
    private final StatisticsMapper mapper;
    public StatisticsService(StatisticsMapper mapper) { this.mapper = mapper; }
    private record Window(LocalDate start, LocalDate end) {}
    private Window window(int days) {
        // Service再次检查角色，避免新增工具入口时只依赖URL拦截器。
        if (!"ADMIN".equals(UserContext.require().role())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (days < 1 || days > 90) throw new BusinessException(ErrorCode.BAD_REQUEST);
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        return new Window(end.minusDays(days), end);
    }
    public List<TopDish> top(int days) {
        var w = window(days);
        return mapper.top(w.start().atStartOfDay(), w.end().atStartOfDay());
    }
    public List<Trend> trend(int days) {
        var w = window(days);
        Map<LocalDate, Long> counts = new HashMap<>();
        mapper.trend(w.start().atStartOfDay(), w.end().atStartOfDay()).forEach(t -> counts.put(t.day(), t.orderCount()));
        return w.start().datesUntil(w.end()).map(d -> new Trend(d, counts.getOrDefault(d, 0L))).toList();
    }
    public Revenue revenue(int days) {
        var w = window(days);
        return mapper.revenue(w.start().atStartOfDay(), w.end().atStartOfDay());
    }
}
