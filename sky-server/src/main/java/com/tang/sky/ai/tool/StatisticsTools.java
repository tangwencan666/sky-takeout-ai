package com.tang.sky.ai.tool;

import com.tang.sky.common.*;
import com.tang.sky.service.StatisticsService;
import com.tang.sky.pojo.StatisticsModels.*;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.*;

/** 管理员工具独立注册，不出现在用户聊天的可调用工具集合中。 */
@Component
public class StatisticsTools {
    private static final Logger log = LoggerFactory.getLogger(StatisticsTools.class);
    private final StatisticsService service;
    public StatisticsTools(StatisticsService service) { this.service = service; }
    private <T> T admin(ToolContext context, String tool, Supplier<T> action) {
        var previous = UserContext.currentOrNull();
        Object candidate = context == null ? previous : context.getContext().get("identity");
        if (!(candidate instanceof UserContext.Identity identity) || !"ADMIN".equals(identity.role()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        try {
            UserContext.set(identity);
            log.info("Statistics tool={} adminId={}", tool, identity.id());
            return action.get();
        } finally {
            if (previous == null) UserContext.clear(); else UserContext.set(previous);
        }
    }
    @Tool(description = "查询近days天已完成订单的单品销量前十，不包含套餐拆分，days为1至90")
    public List<TopDish> getTopSellingDishes(int days, ToolContext context) {
        return admin(context, "getTopSellingDishes", () -> service.top(days));
    }
    @Tool(description = "查询近days天每天创建的所有状态订单数量，days为1至90")
    public List<Trend> getOrderTrend(int days, ToolContext context) {
        return admin(context, "getOrderTrend", () -> service.trend(days));
    }
    @Tool(description = "查询近days天创建且当前已完成订单的数量和金额，非实际支付收入，days为1至90")
    public Revenue getRevenueStatistics(int days, ToolContext context) {
        return admin(context, "getRevenueStatistics", () -> service.revenue(days));
    }
}
