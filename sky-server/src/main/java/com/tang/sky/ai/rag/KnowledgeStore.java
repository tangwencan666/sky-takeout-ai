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
            documents.addAll(chunk(resource.getFilename(),text));
        }
        store.add(documents);
    }
    /** 400字符窗口、80字符重叠；每个片段携带标题和分类，便于回答展示及检索验收。 */
    static List<Document> chunk(String source,String text){
        String title=text.lines().filter(line->line.startsWith("# ")).map(line->line.substring(2).trim()).findFirst().orElse(source);
        String category=switch(source){case "delivery-policy.md"->"delivery";case "refund-policy.md"->"refund";case "coupon-policy.md"->"coupon";case "membership-policy.md"->"membership";case "food-safety.md"->"food-safety";default->"general";};
        var chunks=new ArrayList<Document>();
        for(int start=0;start<text.length();start+=320){
            chunks.add(new Document(text.substring(start,Math.min(start+400,text.length())),Map.of("source",source,"category",category,"title",title,"offset",start,"chunkIndex",chunks.size())));
            if(start+400>=text.length())break;
        }
        return List.copyOf(chunks);
    }
    public List<Document> search(String query){return store.similaritySearch(SearchRequest.builder().query(query).topK(3).similarityThreshold(0.10).build());}
}
