package io.github.takicoyuki.miniweb.core;

import io.github.takicoyuki.miniweb.annotation.Component;
import io.github.takicoyuki.miniweb.annotation.Controller;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTest {

    interface Greeter {
        String greet(String name);
    }

    @Component
    static class JapaneseGreeter implements Greeter {
        public String greet(String name) {
            return "こんにちは、" + name;
        }
    }

    @Component
    static class EnglishGreeter implements Greeter {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    @Component
    static class GreetingService {
        final Greeter greeter;

        GreetingService(Greeter greeter) {
            this.greeter = greeter;
        }
    }

    @Controller
    static class GreetingController {
        final GreetingService service;

        GreetingController(GreetingService service) {
            this.service = service;
        }
    }

    static class NotAComponent {
    }

    @Component
    static class CycleA {
        CycleA(CycleB b) {
        }
    }

    @Component
    static class CycleB {
        CycleB(CycleC c) {
        }
    }

    @Component
    static class CycleC {
        CycleC(CycleA a) {
        }
    }

    @Component
    static class TwoConstructors {
        TwoConstructors() {
        }

        TwoConstructors(String s) {
        }
    }

    @Test
    void コンストラクタの引数にインターフェースの実装を注入する() {
        Container c = new Container(List.of(GreetingController.class, GreetingService.class, JapaneseGreeter.class));

        GreetingController controller = c.getBean(GreetingController.class);

        assertEquals("こんにちは、太郎", controller.service.greeter.greet("太郎"));
    }

    @Test
    void インスタンスはシングルトンで同じものが返る() {
        Container c = new Container(List.of(GreetingController.class, GreetingService.class, JapaneseGreeter.class));

        assertSame(c.getBean(GreetingService.class), c.getBean(GreetingController.class).service);
        assertSame(c.getBean(Greeter.class), c.getBean(JapaneseGreeter.class));
    }

    @Test
    void アノテーションが付いていないクラスは登録しない() {
        Container c = new Container(List.of(JapaneseGreeter.class, NotAComponent.class));

        assertThrows(ContainerException.class, () -> c.getBean(NotAComponent.class));
    }

    @Test
    void Controllerが付いたインスタンスを取り出せる() {
        Container c = new Container(List.of(GreetingController.class, GreetingService.class, JapaneseGreeter.class));

        List<Object> controllers = c.getBeansAnnotatedWith(Controller.class);

        assertEquals(1, controllers.size());
        assertTrue(controllers.get(0) instanceof GreetingController);
    }

    @Test
    void 実装が見つからなければ原因の分かるエラーにする() {
        ContainerException e = assertThrows(ContainerException.class,
                () -> new Container(List.of(GreetingService.class)));

        assertTrue(e.getMessage().contains("Greeter"), e.getMessage());
        assertTrue(e.getMessage().contains("該当するコンポーネントがありません"), e.getMessage());
    }

    @Test
    void 実装が複数あれば候補を並べてエラーにする() {
        ContainerException e = assertThrows(ContainerException.class,
                () -> new Container(List.of(GreetingService.class, JapaneseGreeter.class, EnglishGreeter.class)));

        assertTrue(e.getMessage().contains("複数"), e.getMessage());
        assertTrue(e.getMessage().contains("JapaneseGreeter") && e.getMessage().contains("EnglishGreeter"), e.getMessage());
    }

    @Test
    void 循環依存は経路を示してエラーにする() {
        ContainerException e = assertThrows(ContainerException.class,
                () -> new Container(List.of(CycleA.class, CycleB.class, CycleC.class)));

        assertTrue(e.getMessage().contains("CycleA -> CycleB -> CycleC -> CycleA"), e.getMessage());
    }

    @Test
    void コンストラクタが複数あるとエラーにする() {
        ContainerException e = assertThrows(ContainerException.class,
                () -> new Container(List.of(TwoConstructors.class)));

        assertTrue(e.getMessage().contains("コンストラクタは1つ"), e.getMessage());
    }
}
