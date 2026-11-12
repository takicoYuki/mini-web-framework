# mini-web-framework

![CI](https://github.com/takicoYuki/mini-web-framework/actions/workflows/ci.yml/badge.svg)

Spring Boot が裏で何をしているのかを理解するために作った、**小さな DI コンテナと Web フレームワーク**です。
外部ライブラリは使わず、JDK 標準の機能（リフレクション、アノテーション、`com.sun.net.httpserver`）だけで動きます。

```java
@Controller
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {   // ← コンテナが自動で注入
        this.service = service;
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable("id") long id) {  // ← record を返すと JSON になる
        return service.get(id);
    }

    @PostMapping("")
    public Task create(@RequestBody String title) {
        return service.create(title);
    }
}
```

```java
public static void main(String[] args) throws Exception {
    MiniWebApplication.run(TaskApplication.class, 8080);  // SpringApplication.run に相当
}
```

## 実装した機能

| 機能 | Spring での対応 | 実装 |
|---|---|---|
| コンポーネントスキャン | `@ComponentScan` | 起動クラスのパッケージ配下を、クラスフォルダと JAR の両方から探す（`ClassScanner`） |
| DI（コンストラクタインジェクション） | `@Component` / `@Autowired` | 引数の型がインターフェースなら実装クラスを探して注入。シングルトンで管理（`Container`） |
| 起動時のエラー検出 | 起動失敗時のエラー | 実装なし・候補が複数・循環依存（`A -> B -> C -> A` のように経路を表示）・コンストラクタが複数 |
| ルーティング | `@GetMapping` / `@PostMapping` / `@RequestMapping` | パス変数 `{id}`、固定パスを優先、重複ルートは起動時にエラー（`Router`） |
| 引数の解決 | `@PathVariable` / `@RequestParam` / `@RequestBody` | `int` / `long` / `boolean` への変換、デフォルト値、必須チェック |
| レスポンス | `@RestController` | record・Map・List は JSON、文字列は text/plain、`void` は 204（`JsonWriter`） |
| エラー処理 | `ResponseStatusException` | `HttpStatusException` で任意のステータスを返す。404 / 405 / 400 は自動。想定外の例外は 500 にして詳細は返さない |
| サーバー | 組み込み Tomcat | JDK 標準の `HttpServer` を仮想スレッドで動かす |

## 動かし方

```bash
mvn -B package
java -jar target/mini-web-framework-1.0.0.jar 8080
```

```bash
curl -X POST localhost:8080/tasks -d "牛乳を買う"     # {"id":1,"title":"牛乳を買う","done":false}
curl -X POST localhost:8080/tasks/1/done            # {"id":1,"title":"牛乳を買う","done":true}
curl "localhost:8080/tasks?done=true"
curl localhost:8080/tasks/999                        # {"status":404,"error":"タスクが見つかりません: id=999"}
```

## 構成

```
src/main/java/io/github/takicoyuki
├── miniweb                      フレームワーク本体
│   ├── MiniWebApplication.java  起動（スキャン → DI → ルート登録 → サーバー起動）
│   ├── annotation/              @Component, @Controller, @GetMapping など
│   ├── core/                    ClassScanner, Container（DI コンテナ）
│   └── web/                     Router, JsonWriter, HttpStatusException
└── sample                       フレームワークを使ったタスク管理 API
```

`Router` は HTTP サーバーと切り離してあるので、サーバーを起動せずに単体テストできます。

## テスト

```bash
mvn test
```

| テスト | 内容 |
|---|---|
| `ContainerTest` | インターフェースの注入、シングルトン、実装なし・複数・循環依存・コンストラクタ複数のエラー |
| `ClassScannerTest` | パッケージ配下のクラス検出 |
| `RouterTest` | パス変数、クエリ、デフォルト値、固定パスの優先、404 / 405 / 400 / 500、重複ルートの検出 |
| `JsonWriterTest` | record・コレクション・Map の変換、文字列のエスケープ |
| `TaskApplicationTest` | 実際にサーバーを起動して HTTP で呼ぶ結合テスト（50 件の同時登録で ID が重複しないことも確認） |

## 開発について

以前、途中まで作って止まっていた自作フレームワークを、最後まで作り直したものです。
作り直しには AI コーディングアシスタントの Claude（Anthropic）を使っています。
