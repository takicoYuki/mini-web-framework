package io.github.takicoyuki.miniweb.web;

import io.github.takicoyuki.miniweb.annotation.GetMapping;
import io.github.takicoyuki.miniweb.annotation.PathVariable;
import io.github.takicoyuki.miniweb.annotation.PostMapping;
import io.github.takicoyuki.miniweb.annotation.RequestBody;
import io.github.takicoyuki.miniweb.annotation.RequestMapping;
import io.github.takicoyuki.miniweb.annotation.RequestParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    record User(long id, String name) {
    }

    @RequestMapping("/users")
    static class UserController {

        @GetMapping("/{id}")
        public User get(@PathVariable("id") long id) {
            if (id == 404) {
                throw new HttpStatusException(404, "ユーザーがいません");
            }
            return new User(id, "user" + id);
        }

        @GetMapping("/search")
        public List<String> search(@RequestParam("q") String q,
                                   @RequestParam(value = "limit", defaultValue = "2") int limit) {
            return List.of(q + "1", q + "2", q + "3").subList(0, limit);
        }

        @GetMapping("/{id}/posts/{postId}")
        public Map<String, Object> post(@PathVariable("id") long id, @PathVariable("postId") String postId) {
            return Map.of("user", id, "post", postId);
        }

        @PostMapping("")
        public String create(@RequestBody String body) {
            return "created: " + body;
        }

        @PostMapping("/reset")
        public void reset() {
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("内部の詳細");
        }
    }

    static class Duplicate {
        @GetMapping("/users/{name}")
        public String dup(@PathVariable("name") String name) {
            return name;
        }
    }

    Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
        router.register(new UserController());
    }

    private Router.Response get(String pathAndQuery) {
        return router.dispatch(Router.Request.of("GET", pathAndQuery, ""));
    }

    @Test
    void パス変数を型変換して渡し_recordはJSONで返す() {
        Router.Response r = get("/users/42");

        assertEquals(200, r.status());
        assertTrue(r.contentType().startsWith("application/json"));
        assertEquals("{\"id\":42,\"name\":\"user42\"}", r.body());
    }

    @Test
    void 固定パスはパス変数より優先して振り分ける() {
        Router.Response r = get("/users/search?q=a");

        assertEquals(200, r.status());
        assertEquals("[\"a1\",\"a2\"]", r.body());
    }

    @Test
    void クエリパラメータとデフォルト値を扱える() {
        assertEquals("[\"a1\",\"a2\",\"a3\"]", get("/users/search?q=a&limit=3").body());
        assertEquals("[\"あ1\"]", get("/users/search?q=%E3%81%82&limit=1").body());
    }

    @Test
    void 複数のパス変数を受け取れる() {
        Router.Response r = get("/users/7/posts/abc");

        assertEquals(200, r.status());
        assertTrue(r.body().contains("\"user\":7") && r.body().contains("\"post\":\"abc\""), r.body());
    }

    @Test
    void 文字列を返すとtext_plainで返す() {
        Router.Response r = router.dispatch(Router.Request.of("POST", "/users", "taro"));

        assertEquals(200, r.status());
        assertTrue(r.contentType().startsWith("text/plain"));
        assertEquals("created: taro", r.body());
    }

    @Test
    void 戻り値がvoidなら204() {
        assertEquals(204, router.dispatch(Router.Request.of("POST", "/users/reset", "")).status());
    }

    @Test
    void ルートがなければ404_メソッドが違えば405() {
        assertEquals(404, get("/nothing").status());
        assertEquals(405, router.dispatch(Router.Request.of("DELETE", "/users/1", "")).status());
    }

    @Test
    void 型変換できない値や必須パラメータの不足は400() {
        assertEquals(400, get("/users/abc").status());
        assertEquals(400, get("/users/search").status());
        assertEquals(400, get("/users/search?q=a&limit=x").status());
    }

    @Test
    void コントローラーが投げたHttpStatusExceptionのステータスで返す() {
        Router.Response r = get("/users/404");

        assertEquals(404, r.status());
        assertTrue(r.body().contains("ユーザーがいません"), r.body());
    }

    @Test
    void 想定外の例外は500にして内部の詳細を返さない() {
        Router.Response r = get("/users/boom");

        assertEquals(500, r.status());
        assertTrue(!r.body().contains("内部の詳細"), r.body());
    }

    @Test
    void 末尾のスラッシュは無視する() {
        assertEquals(200, get("/users/1/").status());
    }

    @Test
    void 同じルートを二重に登録するとエラーにする() {
        assertThrows(IllegalStateException.class, () -> router.register(new Duplicate()));
    }
}
