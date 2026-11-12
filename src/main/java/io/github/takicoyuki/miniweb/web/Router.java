package io.github.takicoyuki.miniweb.web;

import io.github.takicoyuki.miniweb.annotation.GetMapping;
import io.github.takicoyuki.miniweb.annotation.PathVariable;
import io.github.takicoyuki.miniweb.annotation.PostMapping;
import io.github.takicoyuki.miniweb.annotation.RequestBody;
import io.github.takicoyuki.miniweb.annotation.RequestMapping;
import io.github.takicoyuki.miniweb.annotation.RequestParam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * コントローラーのメソッドを (HTTP メソッド, パス) に対応付けて呼び出す。
 * HTTP サーバーとは切り離してあるので、サーバーを立てずに単体テストできる。
 */
public class Router {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    /** 受け取ったリクエスト */
    public record Request(String method, String path, Map<String, String> query, String body) {

        public static Request of(String method, String pathAndQuery, String body) {
            int q = pathAndQuery.indexOf('?');
            String path = q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
            Map<String, String> query = new LinkedHashMap<>();
            if (q >= 0) {
                for (String pair : pathAndQuery.substring(q + 1).split("&")) {
                    if (pair.isEmpty()) {
                        continue;
                    }
                    int eq = pair.indexOf('=');
                    String key = decode(eq < 0 ? pair : pair.substring(0, eq));
                    String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
                    query.putIfAbsent(key, value);
                }
            }
            return new Request(method.toUpperCase(), path, query, body == null ? "" : body);
        }
    }

    /** 返すレスポンス */
    public record Response(int status, String contentType, String body) {

        static Response json(int status, Object value) {
            return new Response(status, "application/json; charset=utf-8", JsonWriter.write(value));
        }

        static Response error(int status, String message) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("error", message);
            return json(status, body);
        }
    }

    private record Route(String httpMethod, String pattern, String[] segments, Object controller, Method handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public void register(Object controller) {
        Class<?> type = controller.getClass();
        String prefix = Optional.ofNullable(type.getAnnotation(RequestMapping.class)).map(RequestMapping::value).orElse("");
        for (Method m : type.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (get != null) {
                add("GET", prefix + get.value(), controller, m);
            }
            if (post != null) {
                add("POST", prefix + post.value(), controller, m);
            }
        }
    }

    /** 登録済みのルート一覧（"GET /users/{id}" の形） */
    public List<String> routes() {
        return routes.stream().map(r -> r.httpMethod() + " " + r.pattern()).sorted().toList();
    }

    private void add(String httpMethod, String pattern, Object controller, Method handler) {
        String normalized = normalize(pattern);
        String[] segments = split(normalized);
        for (Route r : routes) {
            if (r.httpMethod().equals(httpMethod) && Arrays.equals(shape(r.segments()), shape(segments))) {
                throw new IllegalStateException("同じルートが重複しています: " + httpMethod + " " + normalized
                        + "（" + r.handler() + " と " + handler + "）");
            }
        }
        handler.setAccessible(true);
        routes.add(new Route(httpMethod, normalized, segments, controller, handler));
        // "/users/search" のような固定パスを "/users/{id}" より先に評価する（パス変数が少ない順）
        routes.sort(java.util.Comparator.comparingLong(r -> Arrays.stream(r.segments()).filter(Router::isVariable).count()));
    }

    public Response dispatch(Request request) {
        String[] requested = split(normalize(request.path()));
        boolean pathMatched = false;

        for (Route route : routes) {
            Map<String, String> vars = match(route.segments(), requested);
            if (vars == null) {
                continue;
            }
            pathMatched = true;
            if (route.httpMethod().equals(request.method())) {
                return invoke(route, request, vars);
            }
        }
        return pathMatched
                ? Response.error(405, "Method Not Allowed")
                : Response.error(404, "Not Found: " + request.path());
    }

    private Response invoke(Route route, Request request, Map<String, String> pathVars) {
        Object[] args;
        try {
            args = resolveArguments(route.handler(), request, pathVars);
        } catch (HttpStatusException e) {
            return Response.error(e.status(), e.getMessage());
        }
        try {
            Object result = route.handler().invoke(route.controller(), args);
            if (result == null) {
                return new Response(204, "text/plain; charset=utf-8", "");
            }
            if (result instanceof CharSequence text) {
                return new Response(200, "text/plain; charset=utf-8", text.toString());
            }
            return Response.json(200, result);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof HttpStatusException hse) {
                return Response.error(hse.status(), hse.getMessage());
            }
            LOG.log(Level.SEVERE, "ハンドラでエラーが発生しました: " + route.handler(), e.getCause());
            return Response.error(500, "Internal Server Error");
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            LOG.log(Level.SEVERE, "ハンドラを呼び出せません: " + route.handler(), e);
            return Response.error(500, "Internal Server Error");
        }
    }

    private static Object[] resolveArguments(Method handler, Request request, Map<String, String> pathVars) {
        Parameter[] params = handler.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (p.isAnnotationPresent(PathVariable.class)) {
                String name = p.getAnnotation(PathVariable.class).value();
                args[i] = convert(pathVars.get(name), p.getType(), name);
            } else if (p.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = p.getAnnotation(RequestParam.class);
                String raw = request.query().get(rp.value());
                if (raw == null || raw.isEmpty()) {
                    if (!rp.defaultValue().isEmpty()) {
                        raw = rp.defaultValue();
                    } else if (!rp.required() && !p.getType().isPrimitive()) {
                        args[i] = null;
                        continue;
                    } else {
                        throw new HttpStatusException(400, "必須パラメータがありません: " + rp.value());
                    }
                }
                args[i] = convert(raw, p.getType(), rp.value());
            } else if (p.isAnnotationPresent(RequestBody.class)) {
                args[i] = request.body();
            } else {
                throw new IllegalStateException(handler + " の引数 " + p.getName()
                        + " に @PathVariable / @RequestParam / @RequestBody のいずれかを付けてください");
            }
        }
        return args;
    }

    private static Object convert(String raw, Class<?> type, String name) {
        try {
            if (type == String.class) {
                return raw;
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(raw);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(raw);
            }
            if (type == boolean.class || type == Boolean.class) {
                if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                    throw new NumberFormatException(raw);
                }
                return Boolean.parseBoolean(raw);
            }
        } catch (NumberFormatException e) {
            throw new HttpStatusException(400, name + " の値が正しくありません: " + raw);
        }
        throw new IllegalStateException("対応していない引数の型です: " + type.getName());
    }

    private static Map<String, String> match(String[] pattern, String[] path) {
        if (pattern.length != path.length) {
            return null;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            if (isVariable(pattern[i])) {
                vars.put(pattern[i].substring(1, pattern[i].length() - 1), decode(path[i]));
            } else if (!pattern[i].equals(path[i])) {
                return null;
            }
        }
        return vars;
    }

    private static String[] shape(String[] segments) {
        return Arrays.stream(segments).map(s -> isVariable(s) ? "{}" : s).toArray(String[]::new);
    }

    private static boolean isVariable(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
    }

    private static String normalize(String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static String[] split(String normalized) {
        return normalized.equals("/") ? new String[0] : normalized.substring(1).split("/");
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
