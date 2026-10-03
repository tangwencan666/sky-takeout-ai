package com.tang.sky.ai.prompt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
/** System Prompt规定角色与边界，User Prompt是用户本次需求；二者不拼成同一条高权限消息。 */
@Component
public class PromptCatalog {
    private final String ordering;
    private final String mealPlan;
    public PromptCatalog() throws IOException {
        ordering=new ClassPathResource("prompts/ordering-system.txt").getContentAsString(StandardCharsets.UTF_8);
        mealPlan=new ClassPathResource("prompts/meal-plan-system.txt").getContentAsString(StandardCharsets.UTF_8);
    }
    public String ordering(){return ordering;}
    public String mealPlan(){return mealPlan;}
}
