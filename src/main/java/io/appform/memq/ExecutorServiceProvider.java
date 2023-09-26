package io.appform.memq;

import java.util.concurrent.ExecutorService;

public interface ExecutorServiceProvider {

    ExecutorService newFixedThreadPool(String name, int coreSize);

}
