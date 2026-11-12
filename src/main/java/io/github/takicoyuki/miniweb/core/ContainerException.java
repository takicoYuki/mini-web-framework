package io.github.takicoyuki.miniweb.core;

/** コンテナの構築に失敗したときの例外。アプリケーションの起動時に投げられる。 */
public class ContainerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContainerException(String message) {
        super(message);
    }

    public ContainerException(String message, Throwable cause) {
        super(message, cause);
    }
}
