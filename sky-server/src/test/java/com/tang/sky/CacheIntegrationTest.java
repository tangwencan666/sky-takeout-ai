package com.tang.sky;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.mapper.CatalogMapper;
import com.tang.sky.service.DishService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 用真实Redis证明第二次读取不查Mapper，更新提交后重新读取最新状态；只清理本项目缓存。 */
@SpringBootTest(properties="sky.cache.enabled=true") @EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CacheIntegrationTest {
    @Autowired DishService service;@Autowired CacheManager caches;@MockitoSpyBean CatalogMapper mapper;
    @Test void missHitAndInvalidation(){
        long category=service.createCategory(new CategoryInput("缓存验证",1,0));long dish=0;
        try{
            caches.getCache("categories").clear();clearInvocations(mapper);
            assertFalse(service.categories().isEmpty());service.categories();verify(mapper,times(1)).categories();
            dish=service.create(new DishInput(category,"缓存菜",new BigDecimal("12.00"),"","",List.of()));service.status(dish,1);
            assertEquals(1,service.page(category,true,null,1,20).total());
            assertEquals(1,service.page(category,true,null,1,20).total());
            service.update(dish,new DishInput(category,"缓存菜已更新",new BigDecimal("13.50"),"","",List.of()));
            var updated=service.page(category,true,null,1,20).records().get(0);
            assertEquals("缓存菜已更新",updated.name());assertEquals(new BigDecimal("13.50"),updated.price());
            service.categoryStatus(category,0);assertEquals(0,service.page(category,true,null,1,20).total());
            assertEquals(0,service.categories().stream().filter(c->c.id()==category).findFirst().orElseThrow().status());
            service.categoryStatus(category,1);assertEquals(1,service.page(category,true,null,1,20).total());
            service.status(dish,0);assertEquals(0,service.page(category,true,null,1,20).total());
        }finally{if(dish!=0){service.status(dish,0);service.delete(dish);}service.deleteCategory(category);}
    }
}
