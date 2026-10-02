package com.tang.sky.mapper;

import com.tang.sky.pojo.StatisticsModels.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 仅提供预先审查的聚合SQL，模型无法改变列、表、排序或拼接SQL。 */
@Mapper
public interface StatisticsMapper {
    @Select("SELECT d.item_id AS dishId, MAX(d.name) AS name, SUM(d.quantity) AS quantity, "
        + "SUM(d.unit_price*d.quantity) AS amount FROM order_detail d JOIN orders o ON o.id=d.order_id "
        + "WHERE o.status='COMPLETED' AND d.item_type='DISH' AND o.created_at >= #{start} AND o.created_at < #{end} "
        + "GROUP BY d.item_id ORDER BY quantity DESC, dishId ASC LIMIT 10")
    List<TopDish> top(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS orderCount FROM orders "
        + "WHERE created_at >= #{start} AND created_at < #{end} GROUP BY DATE(created_at) ORDER BY day")
    List<Trend> trend(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) AS completedOrders, COALESCE(SUM(total_amount),0) AS totalAmount "
        + "FROM orders WHERE status='COMPLETED' AND created_at >= #{start} AND created_at < #{end}")
    Revenue revenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
