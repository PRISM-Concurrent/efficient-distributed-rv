package phd.experiments;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes benchmark results to a file in Markdown, Org-mode, or LaTeX format.
 *
 * Usage:
 *   ReportWriter w = new ReportWriter(path, Format.MD);
 *   w.title("My Experiment");
 *   w.metadata("Threads", "4");
 *   w.table(columns, rows);   // one or more tables
 *   w.close();
 *
 * Both BatchComparison and ProducersBenchmark use this class.
 * Activate by passing --format=md|org|tex and --output=<file> on the command line.
 */
public class ReportWriter implements AutoCloseable {

    public enum Format { MD, ORG, TEX }

    private final Format  format;
    private final PrintWriter out;

    // ── Constructor ────────────────────────────────────────────────────────

    public ReportWriter(Path path, Format format) throws IOException {
        this.format = format;
        this.out    = new PrintWriter(Files.newBufferedWriter(path));
        writeFileHeader();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void title(String text) {
        switch (format) {
            case MD  -> out.println("# " + text + "\n");
            case ORG -> out.println("#+TITLE: " + text + "\n");
            case TEX -> {
                out.println("\\section*{" + tex(text) + "}");
                out.println();
            }
        }
    }

    public void section(String text) {
        switch (format) {
            case MD  -> out.println("## " + text + "\n");
            case ORG -> out.println("* " + text + "\n");
            case TEX -> {
                out.println("\\subsection*{" + tex(text) + "}");
                out.println();
            }
        }
    }

    public void text(String line) {
        switch (format) {
            case MD, ORG -> out.println(line + "\n");
            case TEX     -> out.println(line + " \\\\\n");
        }
    }

    public void metadata(String key, String value) {
        switch (format) {
            case MD  -> out.println("- **" + key + ":** " + value);
            case ORG -> out.println("- " + key + " :: " + value);
            case TEX -> out.println("\\textbf{" + tex(key) + ":} " + tex(value) + " \\\\");
        }
    }

    /** Blank line between metadata items and tables. */
    public void blank() { out.println(); }

    /**
     * Writes a table.
     *
     * @param caption  Short description shown above the table.
     * @param columns  Column header labels.
     * @param rows     Each inner list is one row; length must match columns.
     * @param alignRight  Indices of columns to right-align (numbers).
     *                    All others are left-aligned.
     */
    public void table(String caption, List<String> columns,
                      List<List<String>> rows, int... alignRight) {

        switch (format) {
            case MD  -> tableMarkdown(caption, columns, rows, alignRight);
            case ORG -> tableOrg(caption, columns, rows);
            case TEX -> tableLatex(caption, columns, rows, alignRight);
        }
        out.println();
    }

    @Override
    public void close() {
        writeFileFooter();
        out.flush();
        out.close();
    }

    // ── Markdown ───────────────────────────────────────────────────────────

    private void tableMarkdown(String caption, List<String> columns,
                               List<List<String>> rows, int[] alignRight) {
        if (caption != null) out.println("**" + caption + "**\n");

        // header
        out.print("|");
        for (String col : columns) out.print(" " + col + " |");
        out.println();

        // separator
        out.print("|");
        for (int i = 0; i < columns.size(); i++) {
            boolean right = isAlignRight(i, alignRight);
            out.print(right ? "---:" : "---");
            out.print("|");
        }
        out.println();

        // rows
        for (List<String> row : rows) {
            out.print("|");
            for (String cell : row) out.print(" " + cell + " |");
            out.println();
        }
    }

    // ── Org-mode ───────────────────────────────────────────────────────────

    private void tableOrg(String caption, List<String> columns, List<List<String>> rows) {
        if (caption != null) out.println("#+CAPTION: " + caption);

        // header
        out.print("|");
        for (String col : columns) out.print(" " + col + " |");
        out.println();

        // separator
        out.print("|-");
        for (int i = 1; i < columns.size(); i++) out.print("+----");
        out.println("-|");

        // rows
        for (List<String> row : rows) {
            out.print("|");
            for (String cell : row) out.print(" " + cell + " |");
            out.println();
        }
    }

    // ── LaTeX ──────────────────────────────────────────────────────────────

    private void tableLatex(String caption, List<String> columns,
                            List<List<String>> rows, int[] alignRight) {
        // column spec: l or r per column
        StringBuilder spec = new StringBuilder("|");
        for (int i = 0; i < columns.size(); i++) {
            spec.append(isAlignRight(i, alignRight) ? "r" : "l").append("|");
        }

        out.println("\\begin{table}[ht]");
        out.println("\\centering");
        if (caption != null)
            out.println("\\caption{" + tex(caption) + "}");
        out.println("\\begin{tabular}{" + spec + "}");
        out.println("\\hline");

        // header
        out.print(String.join(" & ", columns.stream().map(c -> "\\textbf{" + tex(c) + "}").toList()));
        out.println(" \\\\");
        out.println("\\hline");

        // rows
        for (List<String> row : rows) {
            out.print(String.join(" & ", row.stream().map(this::tex).toList()));
            out.println(" \\\\");
        }

        out.println("\\hline");
        out.println("\\end{tabular}");
        out.println("\\end{table}");
    }

    // ── File header/footer ─────────────────────────────────────────────────

    private void writeFileHeader() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        switch (format) {
            case MD  -> out.println("<!-- Generated by BatchComparison / ProducersBenchmark — " + ts + " -->\n");
            case ORG -> {
                out.println("#+DATE: " + ts);
                out.println("#+OPTIONS: toc:nil num:nil\n");
            }
            case TEX -> {
                out.println("% Generated by BatchComparison / ProducersBenchmark — " + ts);
                out.println("\\documentclass{article}");
                out.println("\\usepackage{booktabs,geometry}");
                out.println("\\geometry{margin=2cm}");
                out.println("\\begin{document}\n");
            }
        }
    }

    private void writeFileFooter() {
        if (format == Format.TEX) out.println("\\end{document}");
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static boolean isAlignRight(int col, int[] alignRight) {
        for (int r : alignRight) if (r == col) return true;
        return false;
    }

    /** Escapes special LaTeX characters. */
    private String tex(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\textbackslash{}")
                .replace("&",  "\\&")
                .replace("%",  "\\%")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("_",  "\\_")
                .replace("{",  "\\{")
                .replace("}",  "\\}")
                .replace("~",  "\\textasciitilde{}")
                .replace("^",  "\\textasciicircum{}");
    }

    // ── CLI helpers ────────────────────────────────────────────────────────

    /** Parses --format=md|org|tex from args, defaults to MD. */
    public static Format parseFormat(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--format=")) {
                return switch (arg.substring(9).toLowerCase()) {
                    case "org" -> Format.ORG;
                    case "tex" -> Format.TEX;
                    default    -> Format.MD;
                };
            }
        }
        return Format.MD;
    }

    /** Parses --output=<path> from args, or builds a default name. */
    public static Path parseOutput(String[] args, String defaultBase) {
        for (String arg : args) {
            if (arg.startsWith("--output=")) return Path.of(arg.substring(9));
        }
        return Path.of(defaultBase);
    }

    /** Returns the canonical extension for a format. */
    public static String extension(Format f) {
        return switch (f) {
            case MD  -> ".md";
            case ORG -> ".org";
            case TEX -> ".tex";
        };
    }
}
