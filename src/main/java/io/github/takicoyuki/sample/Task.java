package io.github.takicoyuki.sample;

public record Task(long id, String title, boolean done) {

    public Task complete() {
        return new Task(id, title, true);
    }
}
