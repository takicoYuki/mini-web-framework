package io.github.takicoyuki.sample;

import io.github.takicoyuki.miniweb.MiniWebApplication;

/**
 * フレームワークを使ったサンプルアプリ（タスク管理 API）。
 * <pre>
 * curl localhost:8080/tasks
 * curl -X POST localhost:8080/tasks -d "牛乳を買う"
 * curl -X POST localhost:8080/tasks/1/done
 * curl "localhost:8080/tasks?done=true"
 * </pre>
 */
public class TaskApplication {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        MiniWebApplication.run(TaskApplication.class, port);
    }
}
