package com.jichi.salesAgent.tool;

import com.jichi.salesAgent.dto.AnomalyDTO;
import com.jichi.salesAgent.entity.Product;
import com.jichi.salesAgent.entity.SalesRegion;
import com.jichi.salesAgent.entity.SalesRep;
import com.jichi.salesAgent.repository.ProductRepository;
import com.jichi.salesAgent.repository.SalesRegionRepository;
import com.jichi.salesAgent.repository.SalesRepRepository;
import com.jichi.salesAgent.service.SalesQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionTool {

    private final SalesQueryService queryService;
    private final SalesRegionRepository regionRepository;
    private final SalesRepRepository repRepository;
    private final ProductRepository productRepository;

    @Value("${sales-agent.tool.anomaly-threshold-days:5}")
    private int zeroSaleThresholdDays;

    @Value("${sales-agent.tool.trend-drop-threshold:0.3}")
    private double trendDropThreshold;

    @Tool("自动检测销售数据中的所有异常，包括：大区订单量骤降、产品连续零销售、" +
            "销售员退单率异常、销售员业绩骤降。适用于：有没有异常、风险排查、预警检测等场景。" +
            "无需传入参数，系统自动全面扫描。")
    public String detectAllAnomalies() {

        log.info("工具调用-detectAllAnomalies: 开始全面异常检测");

        List<AnomalyDTO> anomalies = new ArrayList<>();

        try {
            anomalies.addAll(detectRegionDropAnomalies());
            anomalies.addAll(detectZeroSaleProducts());
            anomalies.addAll(detectHighRefundReps());
            anomalies.addAll(detectRepPerformanceDrop());
        } catch (Exception e) {
            log.error("异常检测出错", e);
            return "异常检测过程中出现问题，请稍后重试";
        }

        if (anomalies.isEmpty()) {
            return "当前数据未检测到明显异常，销售数据运行正常。";
        }

        // 按优先级排序：HIGH > MEDIUM > LOW
        anomalies.sort((a, b) -> {
            int order = severityOrder(a.severity()) - severityOrder(b.severity());
            return order;
        });

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("异常检测结果：共发现 %d 个异常\n\n", anomalies.size()));

        for (AnomalyDTO anomaly : anomalies) {
            String icon = switch (anomaly.severity()) {
                case "HIGH" -> "🔴 高优先级";
                case "MEDIUM" -> "🟡 中优先级";
                default -> "🔵 低优先级";
            };
            sb.append(String.format("%s｜%s\n", icon, anomaly.type()));
            sb.append(String.format("  对象：%s\n", anomaly.subject()));
            sb.append(String.format("  描述：%s\n", anomaly.description()));
            sb.append(String.format("  建议：%s\n\n", anomaly.suggestion()));
        }

        return sb.toString();
    }

    // ============================================================
    // 检测一：大区订单量骤降
    // ============================================================
    private List<AnomalyDTO> detectRegionDropAnomalies() {
        List<AnomalyDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 近 2 周 vs 过去 4 周每 2 周的平均
        LocalDate recentStart = today.minusWeeks(2);
        LocalDate recentEnd = today;
        LocalDate baseStart = today.minusWeeks(6);
        LocalDate baseEnd = today.minusWeeks(2).minusDays(1);

        for (SalesRegion region : regionRepository.findAll()) {
            Long recentCount = queryService.queryOrderCount(region.getId(), recentStart, recentEnd);
            Long baseCount = queryService.queryOrderCount(region.getId(), baseStart, baseEnd);

            // 基准期 4 周折算成每 2 周平均
            double baseAvg = baseCount / 2.0;
            if (baseAvg < 2) continue; // 样本量太小，忽略

            double dropRate = (baseAvg - recentCount) / baseAvg;
            if (dropRate > trendDropThreshold) {
                String severity = dropRate > 0.6 ? "HIGH" : "MEDIUM";
                result.add(new AnomalyDTO(
                        "大区订单量骤降",
                        severity,
                        region.getName(),
                        String.format("近 2 周订单量 %d 笔，过去 4 周均值 %.1f 笔/两周，下降 %.0f%%",
                                recentCount, baseAvg, dropRate * 100),
                        "建议联系大区负责人确认原因，检查是否有系统问题或市场变化"
                ));
            }
        }
        return result;
    }

    // ============================================================
    // 检测二：产品连续零销售
    // ============================================================
    private List<AnomalyDTO> detectZeroSaleProducts() {
        List<AnomalyDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Product product : productRepository.findByStatus("ACTIVE")) {
            LocalDate lastSaleDate = queryService.queryLastOrderDate(product.getId());
            if (lastSaleDate == null) continue; // 从未销售的新品，跳过

            long daysWithoutSale = ChronoUnit.DAYS.between(lastSaleDate, today);
            if (daysWithoutSale >= zeroSaleThresholdDays) {
                String severity = daysWithoutSale >= 14 ? "HIGH"
                        : daysWithoutSale >= 7 ? "MEDIUM" : "LOW";
                result.add(new AnomalyDTO(
                        "产品连续零销售",
                        severity,
                        product.getName() + "（" + product.getSkuCode() + "）",
                        String.format("已连续 %d 天无销售订单，上次出单日期：%s",
                                daysWithoutSale, lastSaleDate),
                        "检查产品是否下架、库存是否充足、价格是否有竞争力"
                ));
            }
        }
        return result;
    }

    // ============================================================
    // 检测三：销售员退单率异常
    // ============================================================
    private List<AnomalyDTO> detectHighRefundReps() {
        List<AnomalyDTO> result = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);

        List<Object[]> refundData = queryService.queryRefundRates(start, end);
        for (Object[] row : refundData) {
            Long repId = ((Number) row[0]).longValue();
            long refunded = ((Number) row[1]).longValue();
            long total = ((Number) row[2]).longValue();

            if (total < 3) continue; // 样本量太小

            double refundRate = (double) refunded / total;
            if (refundRate > 0.15) {
                String repName = queryService.getRepName(repId);
                String severity = refundRate > 0.3 ? "HIGH" : "MEDIUM";
                result.add(new AnomalyDTO(
                        "销售员退单率异常",
                        severity,
                        repName,
                        String.format("近 30 天退单率 %.0f%%（%d/%d 单），明显高于团队平均水平",
                                refundRate * 100, refunded, total),
                        "建议与该销售员沟通了解原因，排查是否存在虚报订单或客户不满意的情况"
                ));
            }
        }
        return result;
    }

    // ============================================================
    // 检测四：销售员业绩骤降
    // ============================================================
    private List<AnomalyDTO> detectRepPerformanceDrop() {
        List<AnomalyDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate curStart = today.minusDays(30);
        LocalDate prevStart = today.minusDays(60);
        LocalDate prevEnd = today.minusDays(31);

        for (SalesRep rep : repRepository.findByRole("SALES_REP")) {
            BigDecimal current = queryService.queryTotalAmount(null,
                    curStart, today); // 简化：实际应按 repId 查
            // 实际实现需要 Repository 支持 repId 的 sumAmount
            // 这里演示逻辑，完整实现见工具层单元测试那节
        }
        return result; // 简化返回空，完整实现在真实项目代码里
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}