import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** 运行镜像无需额外安装curl，仅验证HTTP应用已启动。数据库另有独立健康检查。 */
public class HealthProbe {
    public static void main(String[] args) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health"))
                .timeout(Duration.ofSeconds(3)).GET().build();
            var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
            System.exit(response.statusCode() == 200 && response.body().contains("SkyTakeout AI is running") ? 0 : 1);
        } catch (Exception e) { System.exit(1); }
    }
}
