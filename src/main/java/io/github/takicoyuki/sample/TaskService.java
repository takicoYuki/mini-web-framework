package io.github.takicoyuki.sample;

import io.github.takicoyuki.miniweb.annotation.Component;
import io.github.takicoyuki.miniweb.web.HttpStatusException;

import java.util.List;

@Component
public class TaskService {

    private static final int MAX_TITLE_LENGTH = 100;

    private final TaskRepository repository;

    /** TaskRepository はインターフェース。実装（InMemoryTaskRepository）はコンテナが探して注入する */
    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task create(String title) {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty()) {
            throw new HttpStatusException(400, "タイトルを入力してください");
        }
        if (t.length() > MAX_TITLE_LENGTH) {
            throw new HttpStatusException(400, "タイトルは" + MAX_TITLE_LENGTH + "文字以内にしてください");
        }
        return repository.save(new Task(repository.nextId(), t, false));
    }

    public Task get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new HttpStatusException(404, "タスクが見つかりません: id=" + id));
    }

    /** done が null なら全件、true / false ならその状態のものだけ返す */
    public List<Task> list(Boolean done) {
        List<Task> all = repository.findAll();
        return done == null ? all : all.stream().filter(t -> t.done() == done).toList();
    }

    public Task complete(long id) {
        return repository.save(get(id).complete());
    }
}
