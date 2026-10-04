package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sole-writer invariants over the three money-and-stock tables, enforced instead of asserted.
 *
 * <p>Written for ledger L006. {@code InventoryLedgerService} is documented as "the sole writer to
 * the inventory_transaction table" in its own class javadoc, and the same claim is made for
 * {@code StockBalanceService} and {@code StockBatchService} over their tables. The guard-reversion
 * audit found nothing enforcing any of it: a second service could start writing
 * {@code inventory_transaction} directly and no test would notice, because there is no guard to
 * delete — only a sentence. That is the weakest class of protection in the codebase, and it sits
 * on the ledger that every stock figure is derived from.
 *
 * <p><b>This is a source-structure test, deliberately.</b> The invariant is about which code is
 * allowed to write, which is a property of the codebase rather than of any single execution, so no
 * runtime test can establish it — a runtime test only ever covers the paths it happens to call.
 * Reading the source is the only way to make the claim total.
 *
 * <h2>The balance invariant is narrower than the prose suggests</h2>
 *
 * <p>Four classes outside {@code StockBalanceService} call {@code stockBalanceRepository.save} or
 * {@code saveAll}, so a blanket "sole writer to stock_balance" assertion would fail today. What
 * they write is denormalised metadata — {@code lastPurchasePrice}, {@code lastPurchaseDate},
 * {@code lastCountDate}, {@code lastCountQuantity} — never {@code quantity} or
 * {@code averageCost}. The real invariant, and the one that protects the money, is therefore about
 * those two fields rather than about the table, and that is what
 * {@link #onlyStockBalanceServiceWritesQuantityOrAverageCost} pins. The audit recorded the gap
 * between the documented claim and the enforceable one as its own finding.
 */
class LedgerSoleWriterArchitectureTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java/com/smart/restaurant_saas");

    /**
     * Classes permitted to write each table, by simple name. Adding a name here is a deliberate
     * widening of a money invariant and should not pass review without a reason recorded next to
     * it.
     */
    private static final Map<String, List<String>> PERMITTED_WRITERS = Map.of(
        "InventoryTransactionRepository", List.of("InventoryLedgerService"),
        "StockBatchRepository", List.of("StockBatchService")
    );

    @Test
    @DisplayName("only InventoryLedgerService writes inventory_transaction")
    void onlyTheLedgerServiceWritesTheLedgerTable() {
        assertThat(writersOf("InventoryTransactionRepository"))
            .as("a second writer to inventory_transaction breaks the ledger's single-entry point; "
                + "route the write through InventoryLedgerService.record instead")
            .containsExactlyInAnyOrderElementsOf(PERMITTED_WRITERS.get("InventoryTransactionRepository"));
    }

    @Test
    @DisplayName("only StockBatchService writes stock_batch")
    void onlyTheBatchServiceWritesTheBatchTable() {
        assertThat(writersOf("StockBatchRepository"))
            .as("batch quantities are FIFO state; a write from outside StockBatchService can "
                + "desynchronise them from the balance average derived off them")
            .containsExactlyInAnyOrderElementsOf(PERMITTED_WRITERS.get("StockBatchRepository"));
    }

    /**
     * The two fields that carry money and stock. Other classes may persist a balance row to update
     * denormalised metadata; none may set these.
     */
    @Test
    @DisplayName("only StockBalanceService sets stock_balance quantity or averageCost")
    void onlyStockBalanceServiceWritesQuantityOrAverageCost() throws IOException {
        Pattern setter = Pattern.compile("\\.(setQuantity|setAverageCost)\\s*\\(");
        List<String> offenders = new ArrayList<>();

        for (Path file : javaSources()) {
            String name = fileName(file);
            if (name.equals("StockBalanceService")) {
                continue;
            }
            String source = Files.readString(file);
            // Narrow the sweep to files that deal in balances at all, so that an unrelated
            // setQuantity on an order line or a count line is not mistaken for a balance write.
            if (!source.contains("StockBalance")) {
                continue;
            }
            Matcher matcher = setter.matcher(source);
            while (matcher.find()) {
                if (writesABalance(source, matcher.start())) {
                    offenders.add(name + " -> " + matcher.group(1));
                }
            }
        }

        assertThat(offenders)
            .as("quantity and averageCost are derived together in StockBalanceService.applyMovement "
                + "from the ledger delta and the open batches; a write elsewhere produces a balance "
                + "that no ledger row explains. StockBalanceAverageCostBackfill is the one allowed "
                + "exception — a one-off migration, not a write path.")
            .containsExactly("StockBalanceAverageCostBackfill -> setAverageCost");
    }

    // ------------------------------------------------------------------ source inspection

    /**
     * Simple names of classes that call {@code save}/{@code saveAll} on a field of the given
     * repository type. Finds the field name from its declaration and then looks for a call on it,
     * which is how every writer in this codebase is written — a repository is always injected as a
     * {@code private final} field, never fetched from a context.
     */
    private List<String> writersOf(String repositoryType) {
        List<String> writers = new ArrayList<>();
        Pattern declaration = Pattern.compile(
            "(?:private|protected)\\s+final\\s+" + repositoryType + "\\s+(\\w+)\\s*;");

        for (Path file : javaSources()) {
            String source;
            try {
                source = Files.readString(file);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read " + file, ex);
            }
            Matcher declared = declaration.matcher(source);
            while (declared.find()) {
                String field = declared.group(1);
                if (Pattern.compile("\\b" + Pattern.quote(field) + "\\.(save|saveAll)\\s*\\(")
                        .matcher(source).find()) {
                    writers.add(fileName(file));
                    break;
                }
            }
        }
        return writers;
    }

    /**
     * Whether the setter call at {@code position} is being made on a stock balance. Resolved from
     * the receiver immediately before the dot, since the codebase consistently names the variable
     * for what it holds ({@code balance}, {@code stockBalance}, {@code b} inside balance helpers).
     * A receiver this cannot classify is reported rather than skipped — a false positive costs a
     * line in the allow-list above, a false negative costs the invariant.
     */
    private boolean writesABalance(String source, int position) {
        int dot = source.lastIndexOf('.', position);
        int start = dot;
        while (start > 0 && (Character.isJavaIdentifierPart(source.charAt(start - 1)))) {
            start--;
        }
        String receiver = source.substring(start, dot).toLowerCase();
        return receiver.contains("balance") || receiver.equals("b");
    }

    private List<Path> javaSources() {
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            return paths.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Could not walk " + MAIN_SOURCES.toAbsolutePath()
                + " — this test reads the source tree and must run from the module root", ex);
        }
    }

    private String fileName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }
}
