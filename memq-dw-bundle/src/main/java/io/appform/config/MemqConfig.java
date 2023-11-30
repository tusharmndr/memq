package io.appform.config;

import io.appform.config.ExecutorConfig;
import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Value
public class MemqConfig {

    @Valid
    List<ExecutorConfig> executors = List.of();

}
