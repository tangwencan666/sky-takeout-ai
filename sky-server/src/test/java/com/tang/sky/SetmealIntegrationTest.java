package com.tang.sky;
import com.tang.sky.common.BusinessException;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.pojo.SetmealModels.*;
import com.tang.sky.service.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
/** 在真实MySQL中验证N:N关系与关联菜品状态，完成后回滚测试数据。 */
@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class SetmealIntegrationTest {
    @Autowired DishService dishes; @Autowired SetmealService meals;
    @Test void lifecycleAndAssociation() {
        long dc=dishes.createCategory(new CategoryInput("菜",1,0));long sc=dishes.createCategory(new CategoryInput("套餐",2,0));
        long dish=dishes.create(new DishInput(dc,"鸡肉",new BigDecimal("20.00"),"","",List.of()));dishes.status(dish,1);
        var input=new Input(sc,"双人餐",new BigDecimal("38.00"),"",List.of(new LineInput(dish,2)));
        long meal=meals.create(input);assertEquals(2,meals.detail(meal,false).dishes().get(0).quantity());
        meals.status(meal,1);assertEquals(1,meals.page(sc,true,1,10).total());
        assertThrows(BusinessException.class,()->meals.delete(meal));
        meals.update(meal,new Input(sc,"更新套餐",new BigDecimal("39.00"),"",input.dishes()));
        assertEquals("更新套餐",meals.detail(meal,true).setmeal().name());
        dishes.status(dish,0);assertEquals(0,meals.page(sc,true,1,10).total());
        assertThrows(BusinessException.class,()->meals.detail(meal,true));
        assertThrows(BusinessException.class,()->dishes.delete(dish));
        meals.status(meal,0);meals.delete(meal);dishes.delete(dish);
        assertThrows(BusinessException.class,()->meals.detail(meal,false));
    }
}
