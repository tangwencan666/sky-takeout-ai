package com.tang.sky.mapper;
import com.tang.sky.pojo.OrderModels.*;
import com.tang.sky.pojo.CartModels.Line;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 普通订单查询固定带userId；状态更新附带旧状态作为并发检查，不覆盖已发生的转换。 */
@Mapper
public interface OrderMapper {
    String COLS="id,number,user_id AS userId,request_key AS requestKey,status,total_amount AS totalAmount,address,contact,created_at AS createdAt";
    @Select("SELECT "+COLS+" FROM orders WHERE id=#{id} AND user_id=#{userId}") Order owned(@Param("id")long id,@Param("userId")long userId);
    @Select("SELECT "+COLS+" FROM orders WHERE id=#{id}") Order adminFind(long id);
    @Select("SELECT "+COLS+" FROM orders WHERE user_id=#{userId} AND request_key=#{key}") Order byKey(@Param("userId")long userId,@Param("key")String key);
    @Select("SELECT "+COLS+" FROM orders WHERE user_id=#{userId} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}") List<Order> history(@Param("userId")long userId,@Param("offset")int offset,@Param("size")int size);
    @Select("SELECT COUNT(*) FROM orders WHERE user_id=#{userId}")long count(long userId);
    @Select("SELECT id,order_id AS orderId,item_type AS itemType,item_id AS itemId,name,unit_price AS unitPrice,quantity,flavor FROM order_detail WHERE order_id=#{id} ORDER BY id")List<Detail> details(long id);
    @Insert("INSERT INTO orders(number,user_id,request_key,status,total_amount,address,contact) VALUES(#{number},#{userId},#{input.requestKey},'PENDING_PAYMENT',#{amount},#{input.address},#{input.contact})")
    int insert(@Param("number")String number,@Param("userId")long userId,@Param("input")CreateInput input,@Param("amount")BigDecimal amount);
    @Select("SELECT LAST_INSERT_ID()")long lastId();
    @Insert("INSERT INTO order_detail(order_id,item_type,item_id,name,unit_price,quantity,flavor) VALUES(#{id},#{line.itemType},#{line.itemId},#{line.name},#{line.unitPrice},#{line.quantity},#{line.flavor})")int line(@Param("id")long id,@Param("line")Line line);
    @Update("UPDATE orders SET status=#{next} WHERE id=#{id} AND status=#{previous}")int transition(@Param("id")long id,@Param("previous")String previous,@Param("next")String next);
}
