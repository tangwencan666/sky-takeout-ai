package com.tang.sky.ai.service;
import com.tang.sky.ai.model.MealPlanModels.*;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.ai.tool.DishTools;
import com.tang.sky.common.*;
import com.tang.sky.pojo.CatalogModels.Dish;
import com.tang.sky.service.DishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import com.tang.sky.ai.security.AiRequestAudit;
import com.tang.sky.ai.model.MealPlanRejectedException;
import com.tang.sky.ai.model.MealPlanRejectedException.Reason;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
/** Structured Output负责映射Java对象；真实性、数量和预算仍由后端验证，不信任模型金额。 */
@Service
public class MealPlanService {
    @Value("${AI_MODEL:deepseek-flash}") private String modelName="deepseek-flash";
    private final ChatClient client;private final DishTools tools;private final DishService dishes;private final PromptCatalog prompts;private final ObjectMapper json;private final String mode;
    public MealPlanService(ChatModel model,DishTools tools,DishService dishes,PromptCatalog prompts,ObjectMapper json,@Value("${AI_MODE:mock}")String mode){this.client=ChatClient.create(model);this.tools=tools;this.dishes=dishes;this.prompts=prompts;this.json=json;this.mode=mode;}
    public AiMealPlan create(Request request){
        return AiRequestAudit.run("meal-plan",null,"live".equals(mode)?modelName:"mock",()->createPlan(request));
    }
    private AiMealPlan createPlan(Request request){
        UserContext.require();BigDecimal budget=budget(request);var candidates=tools.getAvailableDishes();
        if(candidates.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"暂无可售菜品");Draft draft;
        if(!mode.equals("live")){
            // Mock仅用真实菜单演示选择和校验，不声称理解用户口味或人数。
            var ids=new ArrayList<Long>();BigDecimal sum=BigDecimal.ZERO;
            for(var dish:candidates){if(ids.size()<3&&sum.add(dish.price()).compareTo(budget)<=0){ids.add(dish.id());sum=sum.add(dish.price());}}
            draft=new Draft("【Mock演示】按真实菜单和预算生成候选，未使用模型理解口味。",ids);
        }else{
            // 菜单事实已经通过Service预加载，专用Prompt不再要求模型重复调用工具。
            var options=OpenAiChatOptions.builder().responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT,null))
                .internalToolExecutionEnabled(false).temperature(0.0).maxTokens(512).build();
            try{draft=client.prompt().system(prompts.mealPlan()).options(options)
                .user("用户需求："+request.message()+"\n后端确认预算："+budget+"\nCANDIDATE_MENU_JSON：\n"+json.writeValueAsString(candidates))
                .call().entity(Draft.class);}
            catch(Exception e){throw com.tang.sky.ai.security.AiFailure.classify(e);}
        }
        return validate(draft,budget,candidates.stream().map(Dish::id).collect(java.util.stream.Collectors.toSet()));
    }
    private BigDecimal budget(Request input){
        BigDecimal amount=input.budget();
        if(amount==null){var matcher=Pattern.compile("(?:预算|不超过|不超)\\s*(\\d+(?:\\.\\d{1,2})?)").matcher(input.message());if(matcher.find())amount=new BigDecimal(matcher.group(1));}
        if(amount==null||amount.signum()<=0||amount.compareTo(new BigDecimal("10000"))>0)throw new BusinessException(ErrorCode.BAD_REQUEST,"请明确提供预算数字或budget字段");return amount;
    }
    public AiMealPlan validate(Draft draft,BigDecimal budget,Set<Long> candidates){
        int count=draft==null||draft.dishIds()==null?0:draft.dishIds().size();
        if(draft==null)throw rejected(Reason.DRAFT_MISSING,count,budget,null);
        if(draft.reason()==null||draft.reason().isBlank()||draft.reason().length()>2000)throw rejected(Reason.REASON_INVALID,count,budget,null);
        if(draft.dishIds()==null||draft.dishIds().isEmpty())throw rejected(Reason.SELECTION_EMPTY,count,budget,null);
        if(count>10)throw rejected(Reason.SELECTION_TOO_LARGE,count,budget,null);
        Set<Long> ids=new HashSet<>();var selected=new ArrayList<Dish>();BigDecimal total=BigDecimal.ZERO;
        for(Long id:draft.dishIds()){
            if(id==null)throw rejected(Reason.DISH_ID_NULL,count,budget,total);
            if(!ids.add(id))throw rejected(Reason.DISH_ID_DUPLICATE,count,budget,total);
            if(!candidates.contains(id))throw rejected(Reason.DISH_ID_OUTSIDE_CANDIDATES,count,budget,total);
            Dish dish;
            try{dish=dishes.detail(id,true).dish();}
            catch(BusinessException e){if(e.error()==ErrorCode.NOT_FOUND)throw rejected(Reason.DISH_UNAVAILABLE,count,budget,total);throw e;}
            selected.add(dish);total=total.add(dish.price());
        }
        if(total.compareTo(budget)>0)throw rejected(Reason.OVER_BUDGET,count,budget,total);
        AiRequestAudit.mealPlanValidation("ACCEPTED",count,budget,total);
        return new AiMealPlan(draft.reason(),List.copyOf(draft.dishIds()),List.copyOf(selected),total,budget,mode);
    }
    private MealPlanRejectedException rejected(Reason reason,int count,BigDecimal budget,BigDecimal total){
        AiRequestAudit.mealPlanValidation(reason.name(),count,budget,total);return new MealPlanRejectedException(reason);
    }
}
