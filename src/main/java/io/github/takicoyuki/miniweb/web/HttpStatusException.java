package io.github.takicoyuki.miniweb.web;

/** HTTP のエラーステータスで応答したいときに投げる。コントローラーから投げてもよい。 */
public class HttpStatusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public HttpStatusException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
