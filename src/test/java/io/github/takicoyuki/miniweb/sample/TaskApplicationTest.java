package io.github.takicoyuki.miniweb.sample;

import io.github.takicoyuki.miniweb.MiniWebApplication;
import io.github.takicoyuki.sample.TaskApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 実際にサーバーを起動して HTTP で呼び出す結合テスト */
class TaskApplicationTest {

    MiniWebApplication app;
    HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        app = MiniWebApplication.run(TaskApplication.class, 0);
    }

    @AfterEach
    void stop() {
        app.close();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void 起動時にルートが登録される() {
        assertEquals(List.of("GET /tasks", "GET /tasks/{id}", "POST /tasks", "POST /tasks/{id}/done"), app.routes());
    }

    @Test
    void 登録_取得_完了_絞り込みができる() throws Exception {
        HttpResponse<String> created = send("POST", "/tasks", "牛乳を買う");
        assertEquals(200, created.statusCode());
        assertEquals("{\"id\":1,\"title\":\"牛乳を買う\",\"done\":false}", created.body());

        send("POST", "/tasks", "本を返す");
        assertEquals(200, send("POST", "/tasks/1/done", null).statusCode());

        assertEquals("{\"id\":1,\"title\":\"牛乳を買う\",\"done\":true}", send("GET", "/tasks/1", null).body());
        assertEquals("[{\"id\":2,\"title\":\"本を返す\",\"done\":false}]", send("GET", "/tasks?done=false", null).body());
        assertTrue(send("GET", "/tasks", null).body().startsWith("[{\"id\":1"));
    }

    @Test
    void 入力エラーや存在しないIDはJSONのエラーで返す() throws Exception {
        HttpResponse<String> blank = send("POST", "/tasks", "  ");
        assertEquals(400, blank.statusCode());
        assertTrue(blank.body().contains("タイトルを入力してください"));

        HttpResponse<String> missing = send("GET", "/tasks/999", null);
        assertEquals(404, missing.statusCode());
        assertTrue(missing.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void 同時に登録してもIDが重複しない() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<HttpResponse<String>>> calls = IntStream.range(0, 50)
                    .<Callable<HttpResponse<String>>>mapToObj(i -> () -> send("POST", "/tasks", "task" + i))
                    .toList();
            for (var f : pool.invokeAll(calls)) {
                assertEquals(200, f.get().statusCode());
            }
        }
        String all = send("GET", "/tasks", null).body();
        assertEquals(50, all.split("\"id\":").length - 1);
    }
}
