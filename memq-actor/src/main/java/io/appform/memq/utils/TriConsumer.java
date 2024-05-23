package io.appform.memq.utils;

import java.util.Objects;


@FunctionalInterface
public interface TriConsumer<T, U, V> {

    void accept(T t, U u, V v);

    default TriConsumer<T, U, V> andThen(TriConsumer<? super T, ? super U, ? super V> after) {
        Objects.requireNonNull(after);

        return (j, k, l) -> {
            accept(j, k, l);
            after.accept(j, k, l);
        };
    }
}