package io.appform.memq;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceProvider {

    ExecutorService threadPool(String name, int parallel);

}
