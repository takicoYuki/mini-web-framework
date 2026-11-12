package io.github.takicoyuki.sample;

import io.github.takicoyuki.miniweb.annotation.Controller;
import io.github.takicoyuki.miniweb.annotation.GetMapping;
import io.github.takicoyuki.miniweb.annotation.PathVariable;
import io.github.takicoyuki.miniweb.annotation.PostMapping;
import io.github.takicoyuki.miniweb.annotation.RequestBody;
import io.github.takicoyuki.miniweb.annotation.RequestMapping;
import io.github.takicoyuki.miniweb.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("")
    public List<Task> list(@RequestParam(value = "done", required = false) Boolean done) {
        return service.list(done);
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable("id") long id) {
        return service.get(id);
    }

    @PostMapping("")
    public Task create(@RequestBody String title) {
        return service.create(title);
    }

    @PostMapping("/{id}/done")
    public Task complete(@PathVariable("id") long id) {
        return service.complete(id);
    }
}
