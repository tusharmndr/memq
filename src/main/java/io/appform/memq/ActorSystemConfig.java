package io.appform.memq;

import lombok.*;

import javax.validation.Valid;
import java.util.List;

@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActorSystemConfig {

    @Valid
    List<ExecutorConfig> executorConfig;
}
