package it.finanze.sanita.fse2.ms.gtw.garbage.scheduler;

import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/** Runs bounded deletion batches until no eligible documents remain. */
@Slf4j
final class RetentionBatchRunner {

    private RetentionBatchRunner() {
    }

    static void run(String name, int chunkSize, LongSupplier deleteBatch) {
        long started = System.nanoTime();
        long totalDeleted = 0;
        long completedBatches = 0;
        try {
            if (chunkSize <= 0) {
                throw new IllegalArgumentException("Retention chunk size must be greater than zero");
            }
            log.info("{} retention starting: chunk size={}", name, chunkSize);
            while (true) {
                long deleted = deleteBatch.getAsLong();
                if (deleted == 0) {
                    break;
                }
                completedBatches++;
                totalDeleted += deleted;
                log.info("{} retention batch {} completed: deleted={}, total deleted={}",
                        name, completedBatches, deleted, totalDeleted);
            }
            log.info("{} retention completed: batches={}, total deleted={}, elapsed={} ms",
                    name, completedBatches, totalDeleted, (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            log.error("{} retention failed after {} completed batches and {} confirmed deletions; "
                            + "the current batch may be partially applied. Remaining work will be retried on the next run",
                    name, completedBatches, totalDeleted, e);
        }
    }
}
