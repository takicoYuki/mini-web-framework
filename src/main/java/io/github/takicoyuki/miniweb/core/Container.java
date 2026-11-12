package io.github.takicoyuki.miniweb.core;

import io.github.takicoyuki.miniweb.annotation.Component;
import io.github.takicoyuki.miniweb.annotation.Controller;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DI コンテナ。@Component / @Controller が付いたクラスをシングルトンとして生成し、
 * コンストラクタの引数に必要なインスタンスを注入する（コンストラクタインジェクションのみ）。
 *
 * <ul>
 *   <li>引数の型がインターフェースなら、それを実装したコンポーネントを探して注入する</li>
 *   <li>候補が無い・複数ある・循環している場合は、原因が分かるメッセージで起動を止める</li>
 * </ul>
 */
public final class Container {

    private final List<Class<?>> componentTypes;
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private final Deque<Class<?>> creating = new ArrayDeque<>();

    public Container(Collection<Class<?>> candidates) {
        this.componentTypes = candidates.stream()
                .filter(Container::isComponent)
                .toList();
        for (Class<?> type : componentTypes) {
            getBean(type);
        }
    }

    public static boolean isComponent(Class<?> type) {
        return !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())
                && (type.isAnnotationPresent(Component.class) || type.isAnnotationPresent(Controller.class));
    }

    /** 指定した型（インターフェース可）のインスタンスを返す。 */
    public <T> T getBean(Class<T> requested) {
        Class<?> impl = resolveImplementation(requested);
        Object existing = instances.get(impl);
        if (existing != null) {
            return requested.cast(existing);
        }
        if (creating.contains(impl)) {
            throw new ContainerException("依存関係が循環しています: " + describeCycle(impl));
        }
        creating.push(impl);
        try {
            Object created = instantiate(impl);
            instances.put(impl, created);
            return requested.cast(created);
        } finally {
            creating.pop();
        }
    }

    /** 指定したアノテーションが付いたインスタンスを、登録順に返す。 */
    public List<Object> getBeansAnnotatedWith(Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Object> result = new ArrayList<>();
        for (Map.Entry<Class<?>, Object> e : instances.entrySet()) {
            if (e.getKey().isAnnotationPresent(annotation)) {
                result.add(e.getValue());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Class<?> resolveImplementation(Class<?> requested) {
        List<Class<?>> matches = componentTypes.stream()
                .filter(requested::isAssignableFrom)
                .toList();
        if (matches.isEmpty()) {
            throw new ContainerException(requested.getName() + " に該当するコンポーネントがありません"
                    + "（@Component の付け忘れ、またはスキャン対象外のパッケージの可能性があります）");
        }
        if (matches.size() > 1) {
            throw new ContainerException(requested.getName() + " に該当するコンポーネントが複数あります: "
                    + matches.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
        }
        return matches.get(0);
    }

    private Object instantiate(Class<?> impl) {
        Constructor<?>[] constructors = impl.getDeclaredConstructors();
        if (constructors.length != 1) {
            throw new ContainerException(impl.getName() + " のコンストラクタは1つにしてください（現在 " + constructors.length + " 個）");
        }
        Constructor<?> constructor = constructors[0];
        Object[] args = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            args[i] = getBean(types[i]);
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new ContainerException(impl.getName() + " の生成中にエラーが発生しました", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new ContainerException(impl.getName() + " を生成できません", e);
        }
    }

    private String describeCycle(Class<?> repeated) {
        List<Class<?>> chain = new ArrayList<>(creating);
        Collections.reverse(chain);
        int start = chain.indexOf(repeated);
        return chain.subList(start, chain.size()).stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(" -> ")) + " -> " + repeated.getSimpleName();
    }
}
