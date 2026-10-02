package com.tang.sky.ai.rag;
import org.springframework.ai.embedding.*;
import org.springframework.ai.document.Document;
import java.util.*;
/** 零费用词法Embedding基线：字符与双字特征哈希，不宣称具备神经网络语义理解能力。 */
public class LocalHashEmbedding implements EmbeddingModel {
    public int dimensions(){return 2048;}
    public float[] embed(Document document){return vector(document.getText());}
    public EmbeddingResponse call(EmbeddingRequest request){var results=new ArrayList<Embedding>();for(int i=0;i<request.getInstructions().size();i++)results.add(new Embedding(vector(request.getInstructions().get(i)),i));return new EmbeddingResponse(results);}
    private float[] vector(String text){
        String cleaned=text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");float[] vector=new float[dimensions()];
        for(int i=0;i<cleaned.length();i++){vector[Math.floorMod(cleaned.substring(i,i+1).hashCode(),vector.length)]+=0.25f;if(i+1<cleaned.length())vector[Math.floorMod(cleaned.substring(i,i+2).hashCode(),vector.length)]+=1;}
        double length=0;for(float value:vector)length+=value*value;if(length>0){float norm=(float)Math.sqrt(length);for(int i=0;i<vector.length;i++)vector[i]/=norm;}return vector;
    }
}
