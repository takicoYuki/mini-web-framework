package io.github.takicoyuki.miniweb.core;

import io.github.takicoyuki.sample.InMemoryTaskRepository;
import io.github.takicoyuki.sample.TaskController;
import io.github.takicoyuki.sample.TaskRepository;
import io.github.takicoyuki.sample.TaskService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassScannerTest {

    @Test
    void パッケージ配下のクラスを見つける() {
        List<Class<?>> classes = new ClassScanner(getClass().getClassLoader()).scan("io.github.takicoyuki.sample");

        assertTrue(classes.containsAll(List.of(
                TaskController.class, TaskService.class, TaskRepository.class, InMemoryTaskRepository.class)), classes.toString());
        assertTrue(classes.stream().allMatch(c -> c.getPackageName().startsWith("io.github.takicoyuki.sample")));
    }

    @Test
    void 存在しないパッケージなら空() {
        assertTrue(new ClassScanner(getClass().getClassLoader()).scan("no.such.pkg").isEmpty());
    }
}
