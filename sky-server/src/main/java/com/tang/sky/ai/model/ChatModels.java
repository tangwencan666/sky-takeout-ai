package com.tang.sky.ai.model;
import jakarta.validation.constraints.*;
/** conversationId只是会话标识，不能替代登录身份；message限制长度避免无界模型输入。 */
public final class ChatModels {
    private ChatModels(){}
    public record Request(@NotBlank @Size(max=2000)String message,@Pattern(regexp="[a-zA-Z0-9_-]{1,64}")String conversationId){}
    public record Reply(String conversationId,String message,String mode){}
}
