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
    @Tool(description="当用户询问菜名、单品预算或希望获得推荐时调用。keyword按菜名匹配（不是口味语义检索），maxPrice是人民币单价上限。查询真实可售菜品，前100个候选中过滤价格后最多返回20项；包含ID、名称、价格、描述，无匹配返回空列表，不表示完整菜单。")
    public List<Dish> searchDish(@ToolParam(description="可选菜名关键词；不知道菜名时留空，口味需结合返回描述及详情确认",required=false)String keyword,@ToolParam(description="可选人民币单价上限，非整单预算，必须大于等于0",required=false)BigDecimal maxPrice){
        if(maxPrice!=null&&maxPrice.signum()<0)throw new BusinessException(ErrorCode.BAD_REQUEST);
        var result=dishes.page(null,true,keyword,1,100).records().stream().filter(d->maxPrice==null||d.price().compareTo(maxPrice)<=0).limit(20).toList();
        log.info("Tool searchDish resultCount={}",result.size());return result;
    }
    @Tool(description="当用户询问某道菜的价格、描述或可选口味时调用。dishId必须来自菜单查询结果；返回真实可售菜品及口味选项，不存在或下架时报错。描述不是完整配料或过敏原清单。")
    public DishView getDishDetail(@ToolParam(description="菜单查询得到的真实菜品ID")long dishId){log.info("Tool getDishDetail dishId={}",dishId);return dishes.detail(dishId,true);}
    @Tool(description="用户没有指定菜名、询问现在吃什么或想了解当前菜单时调用。无需参数，返回数据库当前可售菜单前20项的ID、菜名、价格和描述；仅为候选窗口，不能声称列出了全部菜品。")
    public List<Dish> getAvailableDishes(){log.info("Tool getAvailableDishes");return dishes.page(null,true,null,1,20).records();}
}
