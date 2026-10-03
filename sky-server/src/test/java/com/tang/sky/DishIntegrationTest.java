package com.tang.sky;

import com.tang.sky.common.*;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 真实数据库验证业务规则与事务，测试创建的数据全部回滚。 */
@SpringBootTest @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class DishIntegrationTest {
    @Autowired DishService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    DishInput input(long category,String name) { return new DishInput(category,name,new BigDecimal("18.50"),"微辣鸡肉","",List.of(new FlavorInput("辣度","微辣,中辣"))); }
    @Test @Transactional void crudPagingStatusAndReferences() {
        long category=service.createCategory(new CategoryInput("测试菜品",1,0));
        long id=service.create(input(category,"测试鸡肉"));
        assertEquals(1,service.detail(id,false).flavors().size());
        assertThrows(BusinessException.class,()->service.detail(id,true));
        service.status(id,1);
        assertEquals(1,service.page(category,true,null,1,10).total());
        assertThrows(BusinessException.class,()->service.delete(id));
        service.update(id,input(category,"更新鸡肉"));
        assertEquals("更新鸡肉",service.detail(id,true).dish().name());
        service.status(id,0); service.delete(id); service.deleteCategory(category);
        assertThrows(BusinessException.class,()->service.detail(id,false));
        assertThrows(BusinessException.class,()->service.page(null,true,null,0,10));
    }
    @Test void transactionRollsBackDishAndFlavors() {
        long[] ids=new long[2];
        new TransactionTemplate(manager).executeWithoutResult(status->{
            ids[0]=service.createCategory(new CategoryInput("回滚分类",1,0));
            ids[1]=service.create(input(ids[0],"回滚菜品"));
            status.setRollbackOnly();
        });
        assertThrows(BusinessException.class,()->service.detail(ids[1],false));
        assertTrue(service.categories().stream().noneMatch(c->c.id()==ids[0]));
    }
    @Test @Transactional void categoryStatusPagingAndReferences() {
        String name="分页分类_"+UUID.randomUUID().toString().substring(0,8);
        long category=service.createCategory(new CategoryInput(name,1,3));
        long dish=service.create(input(category,"分类菜品"));service.status(dish,1);
        service.updateCategory(category,new CategoryInput(name+"更新",1,4));
        var page=service.categoryPage(1,name,1,1);
        assertEquals(1,page.total());assertEquals(category,page.records().get(0).id());
        service.categoryStatus(category,0);
        assertEquals(0,service.categoryPage(1,name,1,1).records().get(0).status());
        assertEquals(0,service.page(category,true,null,1,20).total());
        assertThrows(BusinessException.class,()->service.detail(dish,true));
        assertThrows(BusinessException.class,()->service.deleteCategory(category));
        service.categoryStatus(category,1);assertNotNull(service.detail(dish,true));
        assertThrows(BusinessException.class,()->service.categoryStatus(category,2));
        assertThrows(BusinessException.class,()->service.categoryPage(3,null,1,20));
        assertThrows(BusinessException.class,()->service.categoryPage(null,null,0,20));
    }
}
