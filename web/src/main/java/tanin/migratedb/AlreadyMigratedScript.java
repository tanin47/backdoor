package tanin.migratedb;

import java.time.Instant;

public record AlreadyMigratedScript(int id, String up, String down, Instant appliedAt) {
}
