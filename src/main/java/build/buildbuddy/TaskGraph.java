package build.buildbuddy;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

class TaskGraph<IDENTITY, STATE> {

    private final BiFunction<STATE, STATE, STATE> merge;
    final Map<IDENTITY, Registration<IDENTITY, STATE>> registrations = new LinkedHashMap<>();

    TaskGraph(BiFunction<STATE, STATE, STATE> merge) {
        this.merge = merge;
    }

    void add(IDENTITY identity,
             BiFunction<Executor, STATE, CompletionStage<STATE>> step,
             Set<IDENTITY> dependencies) {
        if (!registrations.keySet().containsAll(dependencies)) {
            throw new IllegalArgumentException("Unknown dependencies: " + dependencies.stream()
                    .filter(dependency -> !registrations.containsKey(dependency))
                    .distinct()
                    .toList());
        }
        if (registrations.putIfAbsent(identity, new Registration<>(step, dependencies)) != null) {
            throw new IllegalArgumentException("Step already registered: " + identity);
        }
    }

    void replace(IDENTITY identity, BiFunction<Executor, STATE, CompletionStage<STATE>> step) {
        Registration<IDENTITY, STATE> registration = registrations.get(identity);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown step: " + identity);
        }
        registrations.replace(identity, new Registration<>(step, registration.dependencies()));
    }

    CompletionStage<STATE> execute(Executor executor, CompletionStage<STATE> initial) {
        Map<IDENTITY, Registration<IDENTITY, STATE>> pending = new LinkedHashMap<>(registrations);
        Map<IDENTITY, CompletionStage<STATE>> dispatched = new HashMap<>();
        while (!pending.isEmpty()) {
            Iterator<Map.Entry<IDENTITY, Registration<IDENTITY, STATE>>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<IDENTITY, Registration<IDENTITY, STATE>> entry = it.next();
                if (dispatched.keySet().containsAll(entry.getValue().dependencies())) {
                    CompletionStage<STATE> completionStage = initial;
                    for (IDENTITY dependency : entry.getValue().dependencies()) {
                        completionStage = completionStage.thenCombineAsync(dispatched.get(dependency), merge, executor);
                    }
                    dispatched.put(entry.getKey(), completionStage.thenComposeAsync(input -> entry.getValue()
                            .step()
                            .apply(executor, input), executor));
                    it.remove();
                }
            }
        }
        return registrations.keySet().stream()
                .map(dispatched::get)
                .reduce(initial, (left, right) -> left.thenCombineAsync(right, merge, executor));
    }

    record Registration<IDENTITY, STATE>(BiFunction<Executor, STATE, CompletionStage<STATE>> step,
                                         Set<IDENTITY> dependencies) {
    }
}
