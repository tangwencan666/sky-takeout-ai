package com.tang.sky.ai.tool;

import com.tang.sky.common.*;
import com.tang.sky.service.StatisticsService;
import com.tang.sky.pojo.StatisticsModels.*;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
    @Tool(description = "管理员询问最近最畅销菜品时调用。days指定最近1至90天；返回该期间创建且已完成订单的单品销量前十和数量，不含套餐拆分。由服务端验证管理员身份，不允许用户调用。")
    public List<TopDish> getTopSellingDishes(@ToolParam(description="最近统计天数，整数1至90")int days, ToolContext context) {
        return admin(context, "getTopSellingDishes", () -> service.top(days));
    }
    @Tool(description = "管理员询问订单趋势或每天单量时调用。days为最近1至90天，返回有订单日期及当天创建的所有状态订单数（含取消订单），无订单日期可能缺省。不能将单量解释为已完成销量或收入。")
    public List<Trend> getOrderTrend(@ToolParam(description="最近统计天数，整数1至90")int days, ToolContext context) {
        return admin(context, "getOrderTrend", () -> service.trend(days));
    }
    @Tool(description = "管理员询问最近营业额时调用。days为最近1至90天，返回区间内创建且目前已完成的订单数量及金额汇总。项目无真实支付，该结果仅为教学订单金额，不得解释为实际支付收入、利润或现金流。")
    public Revenue getRevenueStatistics(@ToolParam(description="最近统计天数，整数1至90")int days, ToolContext context) {
        return admin(context, "getRevenueStatistics", () -> service.revenue(days));
    }
}
