package io.github.takicoyuki.sample;

import io.github.takicoyuki.miniweb.annotation.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/** メモリ上に保存する実装。複数スレッドから同時に呼ばれても壊れないようにしている。 */
@Component
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<Long, Task> tasks = new ConcurrentSkipListMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Task save(Task task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<Task> findById(long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }
}
