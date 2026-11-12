package com.hmood.ui;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Exécute un travail bloquant hors du thread JavaFX puis rend la main à l'interface. */
public final class Async {

    @FunctionalInterface
    public interface Blocking<T> {
        T get() throws Exception;
    }

    private Async() {
    }

    /** {@code onFx(valeur, messageErreur)} est appelé sur le thread JavaFX ; l'un des deux est null. */
    public static <T> void run(Blocking<T> work, BiConsumer<T, String> onFx) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return work.get();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((value, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                onFx.accept(null, cause.getMessage() == null ? cause.toString() : cause.getMessage());
            } else {
                onFx.accept(value, null);
            }
        }));
    }
}
