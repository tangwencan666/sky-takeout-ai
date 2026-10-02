package com.tang.sky;
import com.tang.sky.ai.rag.*;
import com.tang.sky.ai.config.MockChatModel;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.common.UserContext;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
/** 本地真实Embedding与Vector Store检索测试；不将词法检索宣称为真实语义模型测试。 */
class RagTest {
    @AfterEach void clean(){UserContext.clear();}
    @Test void couponRetrievalAndSources()throws Exception{
        var store=new KnowledgeStore();var found=store.search("优惠券不能使用最低消费有效期");assertFalse(found.isEmpty());assertEquals("coupon-policy.md",found.get(0).getMetadata().get("source"));assertTrue(found.size()<=3);
        UserContext.set(new UserContext.Identity(1,"USER"));var answer=new RagService(store,new MockChatModel(),new PromptCatalog(),"mock").answer("优惠券不能使用最低消费有效期");assertTrue(answer.sources().contains("coupon-policy.md"));assertTrue(answer.answer().contains("Mock"));assertEquals("local-hash",answer.embeddingMode());
    }
    @Test void embeddingStable(){var model=new LocalHashEmbedding();assertArrayEquals(model.embed("配送规则"),model.embed("配送规则"));assertEquals(2048,model.dimensions());}
}
