package dev.filipnikolov.vector.connect.detect;

import java.util.List;

public record DbSuggestion(Likelihood likelihood, List<String> signals) {}
