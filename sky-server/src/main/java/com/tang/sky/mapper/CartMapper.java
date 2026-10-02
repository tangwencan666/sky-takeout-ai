package com.tang.sky.mapper;
import com.tang.sky.pojo.CartModels.*;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 每条读写SQL均带当前user_id，防止仅按购物车主键操作导致越权。 */
@Mapper
public interface CartMapper {
    @Select("SELECT id FROM `user` WHERE id=#{userId} FOR UPDATE") Long lockUser(long userId);
    @Select("SELECT id,user_id AS userId,item_type AS itemType,item_id AS itemId,flavor,quantity FROM shopping_cart WHERE user_id=#{userId} ORDER BY id") List<Item> list(long userId);
    @Select("SELECT id,user_id AS userId,item_type AS itemType,item_id AS itemId,flavor,quantity FROM shopping_cart WHERE id=#{id} AND user_id=#{userId}") Item find(@Param("id")long id,@Param("userId")long userId);
    @Select("SELECT id,user_id AS userId,item_type AS itemType,item_id AS itemId,flavor,quantity FROM shopping_cart WHERE user_id=#{userId} AND item_type=#{input.itemType} AND item_id=#{input.itemId} AND flavor=#{input.flavor}") Item same(@Param("userId")long userId,@Param("input")AddInput input);
    @Insert("INSERT INTO shopping_cart(user_id,item_type,item_id,flavor,quantity) VALUES(#{userId},#{input.itemType},#{input.itemId},#{input.flavor},#{input.quantity})") int insert(@Param("userId")long userId,@Param("input")AddInput input);
    @Update("UPDATE shopping_cart SET quantity=#{quantity} WHERE id=#{id} AND user_id=#{userId}") int quantity(@Param("id")long id,@Param("userId")long userId,@Param("quantity")int quantity);
    @Delete("DELETE FROM shopping_cart WHERE id=#{id} AND user_id=#{userId}") int delete(@Param("id")long id,@Param("userId")long userId);
    @Delete("DELETE FROM shopping_cart WHERE user_id=#{userId}") int clear(long userId);
}
