package com.tang.sky.mapper;
import com.tang.sky.pojo.SetmealModels.*;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** N:N中间表保存套餐组成，外键防止引用被静默破坏；查询参数均绑定。 */
@Mapper
public interface SetmealMapper {
    String FILTER=" FROM setmeal s JOIN category c ON c.id=s.category_id WHERE 1=1 <if test='available'> AND s.status=1 AND c.status=1 AND EXISTS(SELECT 1 FROM setmeal_dish x WHERE x.setmeal_id=s.id) AND NOT EXISTS(SELECT 1 FROM setmeal_dish x JOIN dish d ON x.dish_id=d.id JOIN category dc ON dc.id=d.category_id WHERE x.setmeal_id=s.id AND (d.status=0 OR dc.status=0))</if><if test='categoryId != null'> AND s.category_id=#{categoryId}</if>";
    @Select("SELECT id,category_id AS categoryId,name,price,description,status FROM setmeal WHERE id=#{id}") Setmeal find(long id);
    @Select("SELECT x.dish_id AS dishId,d.name,x.quantity FROM setmeal_dish x JOIN dish d ON d.id=x.dish_id WHERE x.setmeal_id=#{id} ORDER BY x.id") List<Line> lines(long id);
    @Select("<script>SELECT s.id,s.category_id AS categoryId,s.name,s.price,s.description,s.status"+FILTER+" ORDER BY s.id LIMIT #{size} OFFSET #{offset}</script>") List<Setmeal> page(@Param("categoryId") Long categoryId,@Param("available") boolean available,@Param("offset") int offset,@Param("size") int size);
    @Select("<script>SELECT COUNT(*)"+FILTER+"</script>") long count(@Param("categoryId") Long categoryId,@Param("available") boolean available);
    @Insert("INSERT INTO setmeal(category_id,name,price,description) VALUES(#{categoryId},#{name},#{price},#{description})") int insert(Input input);
    @Select("SELECT LAST_INSERT_ID()") long lastId();
    @Update("UPDATE setmeal SET category_id=#{input.categoryId},name=#{input.name},price=#{input.price},description=#{input.description} WHERE id=#{id}") int update(@Param("id") long id,@Param("input") Input input);
    @Insert("INSERT INTO setmeal_dish(setmeal_id,dish_id,quantity) VALUES(#{id},#{line.dishId},#{line.quantity})") int addLine(@Param("id") long id,@Param("line") LineInput line);
    @Delete("DELETE FROM setmeal_dish WHERE setmeal_id=#{id}") int clearLines(long id);
    @Delete("DELETE FROM setmeal WHERE id=#{id}") int delete(long id);
    @Update("UPDATE setmeal SET status=#{status} WHERE id=#{id}") int status(@Param("id") long id,@Param("status") int status);
}
