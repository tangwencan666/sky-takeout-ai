package com.tang.sky.ai.rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkTest {
    @Test void longDocumentIsWindowedWithOverlapAndMetadata() {
        String text="# 配送规则\n\n"+"规则内容".repeat(260);
        var chunks=KnowledgeStore.chunk("delivery-policy.md",text);
        assertTrue(chunks.size()>2);
        for(int i=0;i<chunks.size();i++){
            var chunk=chunks.get(i);
            assertTrue(chunk.getText().length()<=400);
            assertEquals(i*320,chunk.getMetadata().get("offset"));
            assertEquals(i,chunk.getMetadata().get("chunkIndex"));
            assertEquals("delivery",chunk.getMetadata().get("category"));
            assertEquals("配送规则",chunk.getMetadata().get("title"));
            if(i>0)assertEquals(chunks.get(i-1).getText().substring(320),chunk.getText().substring(0,80));
        }
    }
}
