package com.tang.sky.ai.rag;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** 小型知识库用Spring AI内存Vector Store，保留文件来源；启动重建，不需要额外数据库。 */
@Component
public class KnowledgeStore {
    private final SimpleVectorStore store=SimpleVectorStore.builder(new LocalHashEmbedding()).build();
    public KnowledgeStore()throws IOException{
        var documents=new ArrayList<Document>();
        for(var resource:new PathMatchingResourcePatternResolver().getResources("classpath*:knowledge/*.md")){
            String text=resource.getContentAsString(StandardCharsets.UTF_8);
            // 400字符窗口、80字符重叠，让边界附近规则尽量不丢失语境。
            for(int start=0;start<text.length();start+=320){documents.add(new Document(text.substring(start,Math.min(start+400,text.length())),Map.of("source",resource.getFilename(),"offset",start)));if(start+400>=text.length())break;}
        }
        store.add(documents);
    }
    public List<Document> search(String query){return store.similaritySearch(SearchRequest.builder().query(query).topK(3).similarityThreshold(0.10).build());}
}
