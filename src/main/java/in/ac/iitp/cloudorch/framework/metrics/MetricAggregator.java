package in.ac.iitp.cloudorch.framework.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates repeated runs (one per seed) into mean and sample standard deviation per metric,
 * grouped by a label such as the policy or forecaster name. Group and metric order follow
 * insertion order so CSV output is stable.
 */
public final class MetricAggregator {

    private final Map<String, Map<String, List<Double>>> groups = new LinkedHashMap<>();

    public void add(String group, Map<String, Double> metrics) {
        Map<String, List<Double>> g = groups.computeIfAbsent(group, k -> new LinkedHashMap<>());
        for (Map.Entry<String, Double> e : metrics.entrySet()) {
            g.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
    }

    public List<String> groups() {
        return new ArrayList<>(groups.keySet());
    }

    public List<String> metrics(String group) {
        return new ArrayList<>(groups.get(group).keySet());
    }

    public int runs(String group) {
        return groups.get(group).values().stream().mapToInt(List::size).max().orElse(0);
    }

    public double mean(String group, String metric) {
        return Statistics.meanIgnoringNaN(groups.get(group).getOrDefault(metric, List.of()));
    }

    public double std(String group, String metric) {
        return Statistics.stdIgnoringNaN(groups.get(group).getOrDefault(metric, List.of()));
    }
}
