package com.tang.sky;
import com.tang.sky.ai.rag.*;
import com.tang.sky.ai.config.MockChatModel;
import com.tang.sky.ai.prompt.PromptCatalog;
import com.tang.sky.common.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
/** 本地真实Embedding与Vector Store检索测试；不将词法检索宣称为真实语义模型测试。 */
class RagTest {
    @AfterEach void clean(){UserContext.clear();}
    @Test void couponRetrievalAndSources()throws Exception{
        var store=new KnowledgeStore();var found=store.search("优惠券不能使用最低消费有效期");assertFalse(found.isEmpty());assertEquals("coupon-policy.md",found.get(0).getMetadata().get("source"));assertTrue(found.size()<=3);
        UserContext.set(new UserContext.Identity(1,"USER"));var answer=new RagService(store,new MockChatModel(),new PromptCatalog(),"mock").answer("优惠券不能使用最低消费有效期");assertTrue(answer.sources().contains("coupon-policy.md"));assertTrue(answer.answer().contains("Mock"));assertEquals("local-hash",answer.embeddingMode());
    }
    @Test void embeddingStable(){var model=new LocalHashEmbedding();assertArrayEquals(model.embed("配送规则"),model.embed("配送规则"));assertEquals(2048,model.dimensions());}
    @ParameterizedTest
    @CsvSource({"配送超时怎么办？,delivery-policy.md,delivery,配送规则", "优惠券为什么不能用？,coupon-policy.md,coupon,优惠券规则", "退款多久？,refund-policy.md,refund,退款规则", "食品有问题怎么办？,food-safety.md,food-safety,食品安全说明"})
    void requiredQuestionsRetrieveRelevantDocument(String question,String source,String category,String title)throws Exception{
        var store=new KnowledgeStore();var found=store.search(question);
        assertFalse(found.isEmpty());assertEquals(source,found.get(0).getMetadata().get("source"));
        assertEquals(category,found.get(0).getMetadata().get("category"));assertTrue(found.get(0).getMetadata().get("title").toString().startsWith(title));
        assertTrue(found.size()<=3);
        UserContext.set(new UserContext.Identity(1,"USER"));
        var reply=new RagService(store,new MockChatModel(),new PromptCatalog(),"mock").answer(question);
        assertTrue(reply.sources().contains(source));assertTrue(reply.answer().contains("依据《"+title));
    }
}
