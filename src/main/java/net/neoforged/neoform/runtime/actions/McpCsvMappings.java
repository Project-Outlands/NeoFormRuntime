package net.neoforged.neoform.runtime.actions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Legacy MCP mapping zips contain CSV files that map SRG member names to MCP member names.
 */
public record McpCsvMappings(Map<String, MemberMapping> methods, Map<String, MemberMapping> fields, Map<String, String> params,
                             Map<String, List<ParamMapping>> paramsByMethodId) {
    public record MemberMapping(String name, String description) {
    }

    /**
     * A legacy MCP parameter mapping: the local variable table slot, the CSV key
     * (e.g. {@code p_1234_1_}) and the MCP name.
     */
    public record ParamMapping(int slot, String srgName, String name) {
    }

    /**
     * Returns the parameter mappings of the given method. Legacy MCP parameter names are keyed by
     * the numeric method id and the local variable table slot (e.g. {@code p_1234_1_} is slot 1 of
     * {@code func_1234_*}); since slot 0 holds {@code this} for instance methods, the first
     * parameter of an instance method is at slot 1.
     */
    public List<ParamMapping> parametersFor(String methodSrgName) {
        var matcher = SRG_METHOD_ID.matcher(methodSrgName);
        if (!matcher.matches()) {
            return List.of();
        }
        return paramsByMethodId.getOrDefault(matcher.group(1), List.of());
    }

    private static final Pattern SRG_METHOD_ID = Pattern.compile("func_(\\d+)_[a-zA-Z_]+");
    private static final Pattern PARAM_KEY = Pattern.compile("p_i?(\\d+)_(\\d+)_");

    private static Map<String, List<ParamMapping>> indexParams(Map<String, String> params) {
        var result = new HashMap<String, List<ParamMapping>>();
        for (var entry : params.entrySet()) {
            var matcher = PARAM_KEY.matcher(entry.getKey());
            if (matcher.matches()) {
                result.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>())
                        .add(new ParamMapping(Integer.parseInt(matcher.group(2)), entry.getKey(), entry.getValue()));
            }
        }
        result.replaceAll((methodId, list) -> {
            list.sort(Comparator.comparingInt(ParamMapping::slot));
            return List.copyOf(list);
        });
        return Map.copyOf(result);
    }

    public static McpCsvMappings load(Path zipPath) throws IOException {
        try (var zip = new ZipFile(zipPath.toFile())) {
            var params = readParamMappings(zip, "params.csv");
            return new McpCsvMappings(
                    readMemberMappings(zip, "methods.csv"),
                    readMemberMappings(zip, "fields.csv"),
                    params,
                    indexParams(params)
            );
        }
    }

    public String methodName(String srgName) {
        var mapping = methods.get(srgName);
        return mapping != null ? mapping.name() : srgName;
    }

    public String fieldName(String srgName) {
        var mapping = fields.get(srgName);
        return mapping != null ? mapping.name() : srgName;
    }

    private static Map<String, MemberMapping> readMemberMappings(ZipFile zip, String entryName) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("MCP mappings zip " + zip.getName() + " does not contain " + entryName);
        }

        var result = new HashMap<String, MemberMapping>();
        try (var reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var columns = parseCsvLine(line);
                if (columns.size() >= 2 && !columns.get(0).isBlank() && !columns.get(1).isBlank()) {
                    var description = columns.size() >= 4 ? columns.get(3) : "";
                    result.put(columns.get(0), new MemberMapping(columns.get(1), description));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> readParamMappings(ZipFile zip, String entryName) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            return Map.of();
        }

        var result = new HashMap<String, String>();
        try (var reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var columns = parseCsvLine(line);
                if (columns.size() >= 2 && !columns.get(0).isBlank() && !columns.get(1).isBlank()) {
                    result.put(columns.get(0), columns.get(1));
                }
            }
        }
        return Map.copyOf(result);
    }

    static List<String> parseCsvLine(String line) {
        var columns = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }
}
