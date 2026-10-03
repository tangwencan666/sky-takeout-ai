package com.tang.sky.ai.service;

import com.tang.sky.common.*;
import com.tang.sky.ai.tool.StatisticsTools;
import com.tang.sky.service.StatisticsService;
import com.tang.sky.pojo.StatisticsModels.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.tang.sky.ai.security.AiRequestAudit;

@Service
public class AdminAiService {
    public record Analysis(String answer, Revenue revenue, List<TopDish> topDishes, List<Trend> trend, String mode) {}
    private final ChatClient client;
    private final StatisticsService statistics;
    private final StatisticsTools tools;
    private final String mode;
    private final String prompt;
    @Value("${AI_MODEL:deepseek-flash}") private String modelName="deepseek-flash";
    public AdminAiService(ChatModel model, StatisticsService statistics, StatisticsTools tools,
            @Value("${AI_MODE:mock}") String mode) throws IOException {
        this.client = ChatClient.create(model); this.statistics = statistics; this.tools = tools; this.mode = mode;
        this.prompt = new ClassPathResource("prompts/admin-system.txt").getContentAsString(StandardCharsets.UTF_8);
    }
    public Analysis analyze(String question, int days) {
        return AiRequestAudit.run("admin-statistics",null,"live".equals(mode)?modelName:"mock",()->analyzeStatistics(question,days));
    }
    private Analysis analyzeStatistics(String question, int days) {
        // 结构化统计由Java固定查询，AI仅作解释，客户端仍可核对原始汇总。
        var revenue = statistics.revenue(days); var top = statistics.top(days); var trend = statistics.trend(days);
        String answer = "【Mock经营分析】近" + days + "天创建且已完成的订单有" + revenue.completedOrders()
            + "笔，订单金额" + revenue.totalAmount() + "元。仅教学订单统计，非实际收款；未调用真实模型。";
        if ("live".equals(mode)) {
            try {
                answer = client.prompt().system(prompt).user("统计区间：近" + days + "天。问题：" + question)
                    .toolCallbacks(AiRequestAudit.callbacks(tools)).toolContext(Map.of("identity", UserContext.require())).call().content();
            } catch (RuntimeException e) { throw new BusinessException(ErrorCode.AI_UNAVAILABLE); }
        }
        return new Analysis(com.tang.sky.ai.security.AiOutput.checked(answer), revenue, top, trend, mode);
    }
}
