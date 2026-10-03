package com.tang.sky.ai.security;
import com.tang.sky.common.*;

/** 限制模型回复体积；这是资源保护，不能证明内容真实或阻止所有提示词注入。 */
public final class AiOutput {
    private AiOutput() {}
    public static String checked(String answer) {
        if (answer == null || answer.isBlank() || answer.length() > 16000)
            throw AiFailure.of(AiFailure.Category.EMPTY_RESPONSE);
        return answer;
    }
}
