package com.tang.sky.mapper;

import com.tang.sky.pojo.CatalogModels.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 所有过滤值绑定参数；排序固定，分页在Service限制范围，禁止拼接任意SQL。 */
@Mapper
public interface CatalogMapper {
    @Select("SELECT * FROM category ORDER BY sort,id") List<Category> categories();
    @Select("SELECT * FROM category WHERE id=#{id}") Category category(long id);
    @Insert("INSERT INTO category(name,type,sort) VALUES(#{name},#{type},#{sort})") int insertCategory(CategoryInput input);
    @Update("UPDATE category SET name=#{input.name},type=#{input.type},sort=#{input.sort} WHERE id=#{id}") int updateCategory(@Param("id") long id,@Param("input") CategoryInput input);
    @Delete("DELETE FROM category WHERE id=#{id}") int deleteCategory(long id);
    @Select("SELECT LAST_INSERT_ID()") long lastId();
    @Select("SELECT id,category_id AS categoryId,name,price,description,image,status FROM dish WHERE id=#{id}") Dish dish(long id);
    @Select("SELECT id,dish_id AS dishId,name,options FROM dish_flavor WHERE dish_id=#{id} ORDER BY id") List<Flavor> flavors(long id);
    @Select("<script>SELECT d.id,d.category_id AS categoryId,d.name,d.price,d.description,d.image,d.status FROM dish d JOIN category c ON c.id=d.category_id WHERE 1=1 <if test='categoryId != null'>AND d.category_id=#{categoryId}</if><if test='available'> AND d.status=1 AND c.status=1</if><if test='keyword != null'> AND LOCATE(#{keyword},d.name)>0</if> ORDER BY d.id LIMIT #{limit} OFFSET #{offset}</script>")
    List<Dish> page(@Param("categoryId") Long categoryId,@Param("available") boolean available,@Param("keyword") String keyword,@Param("offset") int offset,@Param("limit") int limit);
    @Select("<script>SELECT COUNT(*) FROM dish d JOIN category c ON c.id=d.category_id WHERE 1=1 <if test='categoryId != null'>AND d.category_id=#{categoryId}</if><if test='available'> AND d.status=1 AND c.status=1</if><if test='keyword != null'> AND LOCATE(#{keyword},d.name)>0</if></script>")
    long count(@Param("categoryId") Long categoryId,@Param("available") boolean available,@Param("keyword") String keyword);
    @Insert("INSERT INTO dish(category_id,name,price,description,image) VALUES(#{categoryId},#{name},#{price},#{description},#{image})") int insertDish(DishInput input);
    @Update("UPDATE dish SET category_id=#{input.categoryId},name=#{input.name},price=#{input.price},description=#{input.description},image=#{input.image} WHERE id=#{id}") int updateDish(@Param("id") long id,@Param("input") DishInput input);
    @Insert("INSERT INTO dish_flavor(dish_id,name,options) VALUES(#{id},#{flavor.name},#{flavor.options})") int insertFlavor(@Param("id") long id,@Param("flavor") FlavorInput flavor);
    @Delete("DELETE FROM dish_flavor WHERE dish_id=#{id}") int deleteFlavors(long id);
    @Delete("DELETE FROM dish WHERE id=#{id}") int deleteDish(long id);
    @Update("UPDATE dish SET status=#{status} WHERE id=#{id}") int status(@Param("id") long id,@Param("status") int status);
}
