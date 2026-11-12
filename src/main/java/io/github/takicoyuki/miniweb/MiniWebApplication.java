package io.github.takicoyuki.miniweb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.takicoyuki.miniweb.annotation.Controller;
import io.github.takicoyuki.miniweb.core.ClassScanner;
import io.github.takicoyuki.miniweb.core.Container;
import io.github.takicoyuki.miniweb.web.Router;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * フレームワークの入口。Spring Boot の SpringApplication.run に相当する。
 *
 * <pre>
 * 1. 起動クラスと同じパッケージ配下をスキャン
 * 2. @Component / @Controller を生成して依存関係を注入
 * 3. @Controller のメソッドをルートとして登録
 * 4. JDK 標準の HttpServer で待ち受け
 * </pre>
 */
public final class MiniWebApplication implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MiniWebApplication.class.getName());

    private final Container container;
    private final Router router;
    private final HttpServer server;
    private final ExecutorService executor;

    private MiniWebApplication(Container container, Router router, HttpServer server, ExecutorService executor) {
        this.container = container;
        this.router = router;
        this.server = server;
        this.executor = executor;
    }

    /**
     * @param mainClass 起動クラス（このクラスのパッケージ配下がスキャン対象）
     * @param port      待ち受けるポート。0 なら空いているポートを使う
     */
    public static MiniWebApplication run(Class<?> mainClass, int port) throws IOException {
        long started = System.nanoTime();

        List<Class<?>> classes = new ClassScanner(mainClass.getClassLoader()).scan(mainClass.getPackageName());
        Container container = new Container(classes);

        Router router = new Router();
        container.getBeansAnnotatedWith(Controller.class).forEach(router::register);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> handle(router, exchange));
        server.start();

        router.routes().forEach(r -> LOG.info("Mapped " + r));
        LOG.info(String.format("Started on port %d in %d ms", server.getAddress().getPort(),
                (System.nanoTime() - started) / 1_000_000));
        return new MiniWebApplication(container, router, server, executor);
    }

    private static void handle(Router router, HttpExchange exchange) throws IOException {
        try (exchange) {
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Router.Request request = Router.Request.of(
                    exchange.getRequestMethod(), exchange.getRequestURI().getRawPath()
                            + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                    body);
            Router.Response response = router.dispatch(request);

            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public Container container() {
        return container;
    }

    public List<String> routes() {
        return router.routes();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
