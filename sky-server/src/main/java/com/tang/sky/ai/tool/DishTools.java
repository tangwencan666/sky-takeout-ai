package com.tang.sky.ai.tool;
import com.tang.sky.pojo.CatalogModels.*;
import com.tang.sky.service.DishService;
import com.tang.sky.common.*;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.*;
import org.springframework.ai.tool.annotation.*;
import org.springframework.stereotype.Component;
/** 模型只选工具和参数，实际查询经过DishService，禁止把任意SQL作为工具参数。 */
@Component
public class DishTools {
    private static final Logger log=LoggerFactory.getLogger(DishTools.class);
    private final DishService dishes;public DishTools(DishService dishes){this.dishes=dishes;}
    @Tool(description="按菜名关键词查询真实可售菜品，可传单价上限；最多返回20项，不存在则空列表")
    public List<Dish> searchDish(@ToolParam(required=false)String keyword,@ToolParam(required=false)BigDecimal maxPrice){
        if(maxPrice!=null&&maxPrice.signum()<0)throw new BusinessException(ErrorCode.BAD_REQUEST);
        var result=dishes.page(null,true,keyword,1,100).records().stream().filter(d->maxPrice==null||d.price().compareTo(maxPrice)<=0).limit(20).toList();
        log.info("Tool searchDish resultCount={}",result.size());return result;
    }
    @Tool(description="根据真实菜品ID查询可售菜品详情及口味，不存在或下架时报错")
    public DishView getDishDetail(long dishId){log.info("Tool getDishDetail dishId={}",dishId);return dishes.detail(dishId,true);}
    @Tool(description="查询当前可售真实菜单的前20项，用于确认菜名和价格")
    public List<Dish> getAvailableDishes(){log.info("Tool getAvailableDishes");return dishes.page(null,true,null,1,20).records();}
}
