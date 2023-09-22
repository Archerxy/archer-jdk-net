package test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.archer.jdknet.ServerChannel;
import com.archer.jdknet.http.ContentType;
import com.archer.jdknet.http.HttpRequest;
import com.archer.jdknet.http.HttpResponse;
import com.archer.jdknet.http.HttpStatus;
import com.archer.jdknet.http.HttpWrappedHandler;
import com.archer.jdknet.http.client.NioRequest;
import com.archer.jdknet.http.client.NioResponse;
import com.archer.jdknet.http.multipart.Multipart;
import com.archer.jdknet.http.multipart.MultipartParser;

public class JdkNetTest {
	
	public static void httpTest() {
		ServerChannel server = new ServerChannel();
		server.add(new HttpWrappedHandler() {
			@Override
			public void handle(HttpRequest req, HttpResponse res) throws Exception {
				String uri = req.getUri();
				System.out.println("uri = " + uri);
				Map<String, String> query = req.getQueryParams();
				System.out.println("id = " + query.getOrDefault("id", null));
				System.out.println("t = " + query.getOrDefault("t", null));
				if(uri.equals("/nihao")) {
					res.setStatus(HttpStatus.OK);
					res.setContent("{\"nihao\":\"ni\"}".getBytes());
				} else {
					res.setStatus(HttpStatus.NOT_FOUND);
					res.setContent("{\"nihao\":\"ni\"}".getBytes());
				}
			}

			@Override
			public void handleException(HttpRequest req, HttpResponse res, Throwable t) {
				t.printStackTrace();
				String body = "{" +
						"\"server\": \"Java/"+System.getProperty("java.version")+"\"," +
						"\"time\": \"" + LocalDateTime.now().toString() + "\"," +
						"\"status\": \"" + HttpStatus.SERVICE_UNAVAILABLE.getStatus() + "\"" +
					"}";

				res.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
				res.setContentType(ContentType.APPLICATION_JSON);
				res.setContent(body.getBytes());
			}
		});
		try {
			server.bind(8888).start();
			System.out.println("server listened on 8888");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
//		httpTest();
		try {
//			Thread.sleep(1000);
			for(int i = 0; i < 50; i++) {
				NioResponse res = NioRequest.get("https://api.weixin.qq.com/sns/jscode2session?appid=wx7044ac0149b3f437&secret=1fde4b8778750f7a3267bb8dfa23fe9e&js_code=0d3off1w3nyLo13j411w3L9cJQ1off1R&grant_type=authorization_code");
				System.out.println(res.getStatus());
				System.out.println(new String(res.getBody()));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
